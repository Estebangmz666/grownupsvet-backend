package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.configuration.ClinicalReminderProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration.FrontendConfigurationProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email.TransactionalEmailTemplates;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable at-least-once delivery; leases and SQL transactions never span SMTP. */
@Service
public class ClinicalReminderEmailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicalReminderEmailDispatcher.class);
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu 'a las' HH:mm", Locale.forLanguageTag("es-CO"))
            .withZone(ZoneId.of("America/Bogota"));
    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicalReminderStore store;
    private final ClinicalReminderProperties properties;
    private final AppointmentProperties appointments;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final FrontendConfigurationProperties frontend;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ClinicalReminderEmailDispatcher(NamedParameterJdbcTemplate jdbc, ClinicalReminderStore store,
            ClinicalReminderProperties properties, AppointmentProperties appointments, ObjectProvider<JavaMailSender> mailSenders,
            FrontendConfigurationProperties frontend, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.store = store; this.properties = properties; this.appointments = appointments;
        this.mailSenders = mailSenders; this.frontend = frontend; this.clock = clock;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatchPending() {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null || appointments.getSenderAddress() == null || appointments.getSenderAddress().isBlank()) { return; }
        transactions.executeWithoutResult(status -> retireUnavailableTasks());
        for (int count = 0; count < properties.getBatchSize(); count++) {
            ClaimedTask task = claimNext();
            if (task == null) { return; }
            if (task.recipientEmail() != null) { deliver(task, sender); }
        }
    }

    private void retireUnavailableTasks() {
        Instant now = now();
        jdbc.update("update clinical_reminder_email_tasks set status=case when expires_at<=:now then 'EXPIRED' else 'FAILED' end," +
                        "lease_token=null,lease_until=null,last_error_code=case when expires_at<=:now then 'DELIVERY_WINDOW_EXPIRED' else 'DELIVERY_ATTEMPTS_EXHAUSTED' end " +
                        "where id in (select id from clinical_reminder_email_tasks where status in ('PENDING','SENDING') " +
                        "and (status='PENDING' or lease_until<=:now) and (expires_at<=:now or attempt_count>=:maximum) " +
                        "order by next_attempt_at,id limit :limit for update skip locked)",
                Map.of("now", Timestamp.from(now), "maximum", appointments.getMaximumEmailAttempts(), "limit", properties.getBatchSize()));
    }

    private ClaimedTask claimNext() {
        return transactions.execute(status -> {
            Instant now = now();
            var candidates = jdbc.query("select id,schedule_id,recipient_id,scheduled_at,expires_at,attempt_count from clinical_reminder_email_tasks " +
                            "where ((status='PENDING' and next_attempt_at<=:now) or (status='SENDING' and lease_until<=:now)) " +
                            "and scheduled_at<=:now and expires_at>:now and attempt_count<:maximum " +
                            "order by next_attempt_at,id limit 1 for update skip locked",
                    Map.of("now", Timestamp.from(now), "maximum", appointments.getMaximumEmailAttempts()), (resultSet, rowNumber) ->
                            new Candidate(resultSet.getObject("id", UUID.class), resultSet.getObject("schedule_id", UUID.class),
                                    resultSet.getObject("recipient_id", UUID.class), resultSet.getTimestamp("scheduled_at").toInstant(),
                                    resultSet.getTimestamp("expires_at").toInstant(), resultSet.getInt("attempt_count")));
            if (candidates.isEmpty()) { return null; }
            Candidate task = candidates.getFirst();
            var source = store.source(task.scheduleId());
            if (!eligibleSource(source, task.recipientId()) || !now.isBefore(source.endsAt())) {
                jdbc.update("update clinical_reminder_email_tasks set status='SUPERSEDED',lease_token=null,lease_until=null," +
                        "last_error_code='REMINDER_NO_LONGER_CURRENT' where id=:id", Map.of("id", task.id()));
                return new ClaimedTask(task.id(), task.scheduleId(), task.recipientId(), null, task.scheduledAt(), task.expiresAt(), null, task.attempts());
            }
            UUID token = UUID.randomUUID();
            jdbc.update("update clinical_reminder_email_tasks set status='SENDING',attempt_count=attempt_count+1,lease_token=:token," +
                            "lease_until=:lease where id=:id",
                    Map.of("id", task.id(), "token", token, "lease", Timestamp.from(now.plus(properties.getLeaseDuration()))));
            return new ClaimedTask(task.id(), task.scheduleId(), task.recipientId(), source.ownerEmail(),
                    task.scheduledAt(), task.expiresAt(), token, task.attempts() + 1);
        });
    }

    private void deliver(ClaimedTask task, JavaMailSender sender) {
        try {
            var source = currentSource(task);
            if (source == null) { finishObsolete(task); return; }
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appointments.getSenderAddress());
            helper.setTo(task.recipientEmail());
            String subject;
            String body;
            if ("PRESCRIPTION".equals(source.sourceType())) {
                subject = "Recordatorio de tratamiento de GrownupsVet";
                body = "Recordatorio de la prescripción registrada por tu veterinario para " + source.petName() + ". " +
                        "Medicamento: " + source.medicationName() + ". Dosis: " + source.dose().stripTrailingZeros().toPlainString() +
                        " " + source.doseUnit() + ". Vía: " + source.administrationRoute() + ". Fecha y hora: " +
                        LOCAL_TIME.format(task.scheduledAt()) + " (America/Bogota)." +
                        (source.instructions() == null ? "" : " Instrucciones del profesional: " + source.instructions());
            } else {
                subject = "Recordatorio de control de GrownupsVet";
                body = "Recuerda el control indicado por tu veterinario para " + source.petName() + ": " + source.controlReason() +
                        ". Fecha y hora del control: " + LOCAL_TIME.format(source.controlAt()) + " (America/Bogota).";
            }
            var content = TransactionalEmailTemplates.appointmentNotice(subject, body, frontend.baseUrl());
            helper.setSubject(content.subject());
            helper.setText(content.plainText(), content.html());
            // Message construction can race account changes, expiry or recovery by a newer worker.
            if (currentSource(task) == null) { finishObsolete(task); return; }
            sender.send(message);
            Instant sentAt = now();
            transactions.executeWithoutResult(status -> jdbc.update("update clinical_reminder_email_tasks set status='SENT',sent_at=:now," +
                            "lease_token=null,lease_until=null,last_error_code=null where id=:id and status='SENDING' and lease_token=:token and lease_until>:now",
                    Map.of("id", task.id(), "token", task.leaseToken(), "now", Timestamp.from(sentAt))));
        } catch (Exception failure) {
            LOGGER.warn("Clinical reminder delivery failed: taskId={} exceptionType={}", task.id(), failure.getClass().getName());
            markFailure(task);
        }
    }

    private ClinicalReminderStore.ReminderSource currentSource(ClaimedTask task) {
        Instant now = now();
        Boolean ownsLease = jdbc.queryForObject("select exists(select 1 from clinical_reminder_email_tasks where id=:id and status='SENDING' " +
                        "and lease_token=:token and lease_until>:now and scheduled_at<=:now and expires_at>:now)",
                Map.of("id", task.id(), "token", task.leaseToken(), "now", Timestamp.from(now)), Boolean.class);
        if (!Boolean.TRUE.equals(ownsLease)) { return null; }
        var source = store.source(task.scheduleId());
        return eligibleSource(source, task.recipientId()) && Objects.equals(task.recipientEmail(), source.ownerEmail())
                && now.isBefore(source.endsAt()) ? source : null;
    }

    private static boolean eligibleSource(ClinicalReminderStore.ReminderSource source, UUID recipientId) {
        return source != null && source.eligible() && !"SUPERSEDED".equals(source.scheduleStatus())
                && recipientId.equals(source.ownerId()) && source.ownerEmail() != null && !source.ownerEmail().isBlank();
    }

    private void finishObsolete(ClaimedTask task) {
        Instant now = now();
        boolean expired = !now.isBefore(task.expiresAt());
        transactions.executeWithoutResult(status -> jdbc.update("update clinical_reminder_email_tasks set status=:state,lease_token=null,lease_until=null," +
                        "last_error_code=:code where id=:id and status='SENDING' and lease_token=:token and lease_until>:now",
                Map.of("id", task.id(), "token", task.leaseToken(), "now", Timestamp.from(now),
                        "state", expired ? "EXPIRED" : "SUPERSEDED", "code", expired ? "DELIVERY_WINDOW_EXPIRED" : "REMINDER_NO_LONGER_CURRENT")));
    }

    private void markFailure(ClaimedTask task) {
        Instant now = now();
        Instant nextAttempt = now.plus(properties.getRetryDelay().multipliedBy(1L << Math.min(task.attempts() - 1, 8)));
        boolean exhausted = task.attempts() >= appointments.getMaximumEmailAttempts();
        boolean expired = !nextAttempt.isBefore(task.expiresAt());
        transactions.executeWithoutResult(status -> jdbc.update("update clinical_reminder_email_tasks set status=:state,lease_token=null,lease_until=null," +
                        "next_attempt_at=:next,last_error_code=:code where id=:id and status='SENDING' and lease_token=:token and lease_until>:now",
                Map.of("id", task.id(), "token", task.leaseToken(), "now", Timestamp.from(now), "next", Timestamp.from(nextAttempt),
                        "state", exhausted ? "FAILED" : expired ? "EXPIRED" : "PENDING",
                        "code", exhausted ? "DELIVERY_ATTEMPTS_EXHAUSTED" : expired ? "DELIVERY_WINDOW_EXPIRED" : "SMTP_DELIVERY_FAILED")));
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private record Candidate(UUID id, UUID scheduleId, UUID recipientId, Instant scheduledAt, Instant expiresAt, int attempts) { }
    private record ClaimedTask(UUID id, UUID scheduleId, UUID recipientId, String recipientEmail, Instant scheduledAt,
            Instant expiresAt, UUID leaseToken, int attempts) { }
}
