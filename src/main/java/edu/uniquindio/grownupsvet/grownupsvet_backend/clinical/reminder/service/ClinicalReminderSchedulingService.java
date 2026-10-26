package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterFinalizedEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/** Program creation joins the finalization commit; no historical backfill or SMTP happens here. */
@Service
public class ClinicalReminderSchedulingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate mandatoryTransaction;

    public ClinicalReminderSchedulingService(NamedParameterJdbcTemplate jdbc, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        mandatoryTransaction = new TransactionTemplate(transactionManager);
        mandatoryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void finalized(ClinicalEncounterFinalizedEvent event) {
        // Spring restricts @Transactional on event-listener methods; the template enforces the same existing-transaction requirement.
        mandatoryTransaction.executeWithoutResult(status -> schedule(event));
    }

    private void schedule(ClinicalEncounterFinalizedEvent event) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var encounter = jdbc.query("select e.id from clinical_encounters e join appointments a on a.id=e.appointment_id " +
                        "where e.id=:encounter and e.status='FINALIZED' and a.status='COMPLETED'",
                Map.of("encounter", event.encounterId()), (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class));
        if (encounter.isEmpty()) { throw new IllegalStateException("Clinical reminder scheduling requires a committed finalization intent."); }
        jdbc.query("select id,starts_at,ends_at,interval_hours from clinical_prescriptions where encounter_id=:encounter order by position",
                Map.of("encounter", event.encounterId()), resultSet -> {
                    Instant startsAt = resultSet.getTimestamp("starts_at").toInstant();
                    Instant endsAt = resultSet.getTimestamp("ends_at").toInstant();
                    insert(event.encounterId(), "PRESCRIPTION", resultSet.getObject("id", UUID.class), startsAt, endsAt,
                            resultSet.getInt("interval_hours"), now.isBefore(endsAt) ? startsAt : null, now);
                });
        jdbc.query("select id,scheduled_at from clinical_controls where encounter_id=:encounter order by position",
                Map.of("encounter", event.encounterId()), resultSet -> {
                    Instant endsAt = resultSet.getTimestamp("scheduled_at").toInstant();
                    Instant startsAt = endsAt.minus(Duration.ofHours(24));
                    if (now.isAfter(startsAt) && now.isBefore(endsAt)) { startsAt = now; }
                    insert(event.encounterId(), "CONTROL", resultSet.getObject("id", UUID.class), startsAt, endsAt,
                            null, now.isBefore(endsAt) ? startsAt : null, now);
                });
    }

    private void insert(UUID encounterId, String sourceType, UUID sourceId, Instant startsAt, Instant endsAt,
            Integer intervalHours, Instant nextDueAt, Instant now) {
        jdbc.update("insert into clinical_reminder_schedules(id,encounter_id,source_type,prescription_id,control_id,starts_at,ends_at," +
                        "interval_hours,next_due_at,next_occurrence_index,status,created_at) " +
                        "values(:id,:encounter,:type,:prescription,:control,:start,:end,:interval,:next,0,:status,:now) on conflict do nothing",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("encounter", encounterId).addValue("type", sourceType)
                        .addValue("prescription", "PRESCRIPTION".equals(sourceType) ? sourceId : null)
                        .addValue("control", "CONTROL".equals(sourceType) ? sourceId : null).addValue("start", Timestamp.from(startsAt))
                        .addValue("end", Timestamp.from(endsAt)).addValue("interval", intervalHours)
                        .addValue("next", nextDueAt == null ? null : Timestamp.from(nextDueAt))
                        .addValue("status", nextDueAt == null ? "FINISHED" : "ACTIVE").addValue("now", Timestamp.from(now)));
    }
}
