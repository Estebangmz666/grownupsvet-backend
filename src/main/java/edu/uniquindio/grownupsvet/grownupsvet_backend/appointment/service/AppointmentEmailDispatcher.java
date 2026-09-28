package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentNotificationsQueuedEvent;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Durable, at-least-once delivery. Database leases never span an SMTP call. */
@Component
public class AppointmentEmailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentEmailDispatcher.class);
    private static final long LEASE_SECONDS = 120;
    private static final DateTimeFormatter LOCAL_START = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", Locale.forLanguageTag("es-CO"))
            .withZone(ZoneId.of("America/Bogota"));
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final AppointmentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;

    public AppointmentEmailDispatcher(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSenders,
            AppointmentProperties properties, ObjectMapper objectMapper, Clock clock,
            PlatformTransactionManager transactionManager,
            @Qualifier("appointmentEmailExecutor") TaskExecutor executor) {
        this.jdbc = jdbc; this.mailSenders = mailSenders; this.properties = properties;
        this.objectMapper = objectMapper; this.clock = clock; this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notificationsQueued(AppointmentNotificationsQueuedEvent event) { submit(); }

    @Scheduled(fixedDelayString = "${grownupsvet.appointments.email-poll-delay-millis:15000}",
            initialDelayString = "${grownupsvet.appointments.email-poll-delay-millis:15000}")
    public void pollPending() { submit(); }

    private void submit() {
        try {
            executor.execute(() -> {
                try { dispatchPending(); }
                catch (RuntimeException failure) {
                    LOGGER.warn("Appointment notification queue will be retried: exceptionType={}", failure.getClass().getName());
                }
            });
        } catch (TaskRejectedException ignored) {
            // A full queue or shutdown only postpones durable work until the next poll.
        }
    }

    public void dispatchPending() {
        for (int count = 0; count < 50; count++) {
            DeliveryTask task = claimNext();
            if (task == null) { return; }
            deliver(task);
        }
    }

    private DeliveryTask claimNext() {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            jdbc.update("update appointment_email_tasks set status='FAILED',lease_token=null," +
                            "last_error_code='DELIVERY_ATTEMPTS_EXHAUSTED' " +
                            "where status in ('PENDING','SENDING') and next_attempt_at<=? and attempt_count>=?",
                    timestamp(now), properties.getMaximumEmailAttempts());
            List<DeliveryTask> candidates = jdbc.query("select t.id,t.appointment_id,t.recipient_email,t.task_type,t.payload::text payload " +
                            "from appointment_email_tasks t join appointments a on a.id=t.appointment_id " +
                            "where t.status in ('PENDING','SENDING') and t.next_attempt_at<=? and t.attempt_count<? " +
                            "and t.recipient_email is not null " +
                            "and not exists(select 1 from appointment_email_tasks other where other.appointment_id=t.appointment_id " +
                            "and other.id<>t.id and other.status='SENDING') " +
                            "order by t.next_attempt_at,t.created_at,t.id limit 1 for update of a,t skip locked",
                    (row, index) -> new DeliveryTask(row.getObject("id", UUID.class),
                            row.getObject("appointment_id", UUID.class), row.getString("recipient_email"),
                            row.getString("task_type"), row.getString("payload"), UUID.randomUUID()),
                    timestamp(now), properties.getMaximumEmailAttempts());
            if (candidates.isEmpty()) { return null; }
            DeliveryTask task = candidates.getFirst();
            // A new statement after the appointment lock sees any preceding claimant's commit.
            // This serializes claims for the same appointment without holding locks over SMTP.
            Boolean anotherDelivery = jdbc.queryForObject("select exists(select 1 from appointment_email_tasks " +
                            "where appointment_id=? and id<>? and status='SENDING')", Boolean.class,
                    task.appointmentId(), task.id());
            if (Boolean.TRUE.equals(anotherDelivery)) { return null; }
            jdbc.update("update appointment_email_tasks set status='SENDING',attempt_count=attempt_count+1," +
                            "next_attempt_at=?,lease_token=? where id=?", timestamp(now.plusSeconds(LEASE_SECONDS)),
                    task.leaseToken(), task.id());
            return task;
        });
    }

    private void deliver(DeliveryTask task) {
        try {
            Notice notice = currentNotice(task);
            if (notice == null) { finishSuperseded(task); return; }
            JavaMailSender sender = mailSenders.getIfAvailable();
            if (sender == null || properties.getSenderAddress() == null || properties.getSenderAddress().isBlank()) {
                markFailure(task, "EMAIL_CONFIGURATION_MISSING"); return;
            }
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.getSenderAddress());
            helper.setTo(task.recipientEmail());
            // Recheck immediately before SMTP; a business change during message creation
            // must not send the now-obsolete notice. SMTP itself is outside a transaction.
            notice = currentNotice(task);
            if (notice == null) { finishSuperseded(task); return; }
            helper.setSubject(notice.subject());
            helper.setText(notice.body(), false);
            sender.send(message);
            jdbc.update("update appointment_email_tasks set status='SENT',sent_at=?,last_error_code=null,lease_token=null " +
                            "where id=? and status='SENDING' and lease_token=?",
                    timestamp(clock.instant()), task.id(), task.leaseToken());
        } catch (Exception failure) {
            LOGGER.warn("Appointment notification delivery failed: taskId={} exceptionType={}", task.id(), failure.getClass().getName());
            markFailure(task, "SMTP_DELIVERY_FAILED");
        }
    }

    private Notice currentNotice(DeliveryTask task) {
        Boolean ownsLease = jdbc.queryForObject("select exists(select 1 from appointment_email_tasks " +
                        "where id=? and status='SENDING' and lease_token=? and next_attempt_at>?)",
                Boolean.class, task.id(), task.leaseToken(), timestamp(clock.instant()));
        if (!Boolean.TRUE.equals(ownsLease)) { return null; }
        JsonNode payload = objectMapper.readTree(task.payload());
        if ("OWNER_REASSIGNED".equals(task.taskType())) { return currentOwnerNotice(task, payload); }
        if ("ADMIN_REASSIGNMENT".equals(task.taskType())) { return currentAdministratorNotice(task, payload); }
        throw new IllegalStateException("Unsupported appointment notification type.");
    }

    private Notice currentOwnerNotice(DeliveryTask task, JsonNode payload) {
        List<OwnerAssignment> current = jdbc.query("select a.current_slot_id,a.starts_at,p.name pet_name,vp.full_name veterinarian_name," +
                        "(select max(e.appointment_version) from appointment_events e where e.appointment_id=a.id and e.event_type='REASSIGNED') assignment_version " +
                        "from appointments a join pets p on p.id=a.pet_id " +
                        "join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "join veterinarian_profiles vp on vp.user_id=s.veterinarian_id join users v on v.id=s.veterinarian_id " +
                        "where a.id=? and a.status in ('REQUESTED','CONFIRMED') and a.assignment_status='ASSIGNED' " +
                        "and v.status='ACTIVE' and (a.status='CONFIRMED' or a.confirmation_cutoff_at>?)",
                (row, index) -> new OwnerAssignment(row.getObject("current_slot_id", UUID.class),
                        row.getTimestamp("starts_at").toInstant(), row.getString("pet_name"),
                        row.getString("veterinarian_name"), row.getObject("assignment_version", Long.class)),
                task.appointmentId(), timestamp(clock.instant()));
        if (current.isEmpty()) { return null; }
        OwnerAssignment assignment = current.getFirst();
        if (!assignment.startsAt().toString().equals(payload.path("startsAt").asText())) { return null; }
        if (payload.has("availabilitySlotId")) {
            if (!assignment.slotId().toString().equals(payload.path("availabilitySlotId").asText())) { return null; }
        } else if (!assignment.veterinarianName().equals(payload.path("veterinarianName").asText())) {
            // Compatibility with durable tasks created before assignment identifiers were included.
            return null;
        }
        if (payload.has("assignmentVersion") && (assignment.version() == null
                || assignment.version() != payload.path("assignmentVersion").asLong())) { return null; }
        return new Notice("Actualización de tu cita veterinaria", "La cita de " + assignment.petName() +
                " fue actualizada. Veterinario: " + assignment.veterinarianName() + ". Fecha y hora: " +
                LOCAL_START.format(assignment.startsAt()) + " (America/Bogota).");
    }

    private Notice currentAdministratorNotice(DeliveryTask task, JsonNode payload) {
        UUID veterinarianId = UUID.fromString(payload.path("veterinarianId").asText());
        Boolean recipientActive = jdbc.queryForObject("select exists(select 1 from appointment_email_tasks t " +
                        "join users recipient on recipient.id=t.recipient_id " +
                        "where t.id=? and recipient.status='ACTIVE' and recipient.role='ADMINISTRATOR')", Boolean.class, task.id());
        if (!Boolean.TRUE.equals(recipientActive)) { return null; }
        List<AdministratorNotice> current = jdbc.query("select vp.full_name,count(a.id) appointment_count " +
                        "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "join veterinarian_profiles vp on vp.user_id=s.veterinarian_id join users v on v.id=s.veterinarian_id " +
                        "where s.veterinarian_id=? and v.status='DISABLED' and a.assignment_status='NEEDS_REASSIGNMENT' " +
                        "and a.status in ('REQUESTED','CONFIRMED') and a.starts_at>? " +
                        "and (a.status='CONFIRMED' or a.confirmation_cutoff_at>?) group by vp.full_name",
                (row, index) -> new AdministratorNotice(row.getString("full_name"), row.getLong("appointment_count")),
                veterinarianId, timestamp(clock.instant()), timestamp(clock.instant()));
        if (current.isEmpty()) { return null; }
        AdministratorNotice notice = current.getFirst();
        return new Notice("Citas pendientes de reasignación", "El veterinario " + notice.veterinarianName() +
                " fue deshabilitado. Hay " + notice.appointmentCount() + " cita(s) que requieren revisión en la agenda.");
    }

    private void finishSuperseded(DeliveryTask task) {
        jdbc.update("update appointment_email_tasks set status='SUPERSEDED',last_error_code='NOTICE_NO_LONGER_CURRENT',lease_token=null " +
                "where id=? and status='SENDING' and lease_token=?", task.id(), task.leaseToken());
    }

    private void markFailure(DeliveryTask task, String errorCode) {
        jdbc.update("update appointment_email_tasks set status=case when attempt_count>=? then 'FAILED' else 'PENDING' end," +
                        "last_error_code=?,next_attempt_at=?,lease_token=null where id=? and status='SENDING' and lease_token=?",
                properties.getMaximumEmailAttempts(), errorCode, timestamp(clock.instant().plusSeconds(60)), task.id(), task.leaseToken());
    }

    private static java.sql.Timestamp timestamp(Instant instant) { return java.sql.Timestamp.from(instant); }
    private record DeliveryTask(UUID id, UUID appointmentId, String recipientEmail, String taskType, String payload, UUID leaseToken) { }
    private record OwnerAssignment(UUID slotId, Instant startsAt, String petName, String veterinarianName, Long version) { }
    private record AdministratorNotice(String veterinarianName, long appointmentCount) { }
    private record Notice(String subject, String body) { }
}
