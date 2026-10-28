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
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        jdbc.update("""
                insert into clinical_reminder_schedules(id,encounter_id,source_type,prescription_id,control_id,
                    starts_at,ends_at,interval_hours,next_due_at,next_occurrence_index,status,created_at)
                select gen_random_uuid(),encounter_id,source_type,prescription_id,control_id,starts_at,ends_at,interval_hours,
                    case when :now < ends_at then starts_at else null end,
                    0,case when :now < ends_at then 'ACTIVE' else 'FINISHED' end,:now
                from (
                    select encounter_id,'PRESCRIPTION' as source_type,id as prescription_id,cast(null as uuid) as control_id,
                        starts_at,ends_at,interval_hours
                    from clinical_prescriptions where encounter_id=:encounter
                    union all
                    select encounter_id,'CONTROL',cast(null as uuid),id,
                        case when :now > scheduled_at - interval '24 hours' and :now < scheduled_at
                            then :now else scheduled_at - interval '24 hours' end,
                        scheduled_at,cast(null as integer)
                    from clinical_controls where encounter_id=:encounter
                ) source_schedules
                on conflict do nothing
                """, new MapSqlParameterSource().addValue("encounter", event.encounterId())
                .addValue("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
    }
}
