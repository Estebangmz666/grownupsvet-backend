package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.configuration.ClinicalReminderProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Advances compact schedules arithmetically and materializes only the current deliverable occurrence. */
@Service
public class ClinicalReminderMaterializer {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicalReminderStore store;
    private final ClinicalReminderProperties properties;
    private final Clock clock;

    public ClinicalReminderMaterializer(NamedParameterJdbcTemplate jdbc, ClinicalReminderStore store,
            ClinicalReminderProperties properties, Clock clock) {
        this.jdbc = jdbc; this.store = store; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public int materializeDue() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("update clinical_reminder_email_tasks set status='EXPIRED',lease_token=null,lease_until=null,last_error_code='DELIVERY_WINDOW_EXPIRED' " +
                        "where id in (select id from clinical_reminder_email_tasks where (status='PENDING' or (status='SENDING' and lease_until<=:now)) and expires_at<=:now " +
                        "order by expires_at,id limit :limit for update skip locked)",
                Map.of("now", Timestamp.from(now), "limit", properties.getBatchSize()));
        List<Schedule> schedules = jdbc.query("select id,source_type,starts_at,ends_at,interval_hours,next_due_at,next_occurrence_index " +
                        "from clinical_reminder_schedules where status='ACTIVE' and next_due_at<=:now " +
                        "order by next_due_at,id limit :limit for update skip locked",
                Map.of("now", Timestamp.from(now), "limit", properties.getBatchSize()), (resultSet, rowNumber) ->
                        new Schedule(resultSet.getObject("id", UUID.class), resultSet.getString("source_type"),
                                resultSet.getTimestamp("starts_at").toInstant(), resultSet.getTimestamp("ends_at").toInstant(),
                                resultSet.getInt("interval_hours"), resultSet.getTimestamp("next_due_at").toInstant(),
                                resultSet.getLong("next_occurrence_index")));
        int created = 0;
        for (Schedule schedule : schedules) {
            var source = store.source(schedule.id());
            if ("CONTROL".equals(schedule.sourceType())) {
                boolean deliverable = source != null && source.eligible() && now.isBefore(schedule.endsAt());
                if (deliverable) { created += enqueue(schedule, schedule.nextDueAt(), schedule.endsAt(), source.ownerId(), now); }
                advance(schedule, 1, null, deliverable ? 0 : 1, now);
                continue;
            }
            long intervalSeconds = schedule.intervalHours() * 3600L;
            long wholeIntervals = Duration.between(schedule.startsAt(), schedule.endsAt()).getSeconds() / intervalSeconds;
            long occurrenceCount = wholeIntervals + (schedule.startsAt().plusSeconds(wholeIntervals * intervalSeconds).isBefore(schedule.endsAt()) ? 1 : 0);
            if (!now.isBefore(schedule.endsAt())) {
                advance(schedule, occurrenceCount, null, Math.max(0, occurrenceCount - schedule.nextIndex()), now);
                continue;
            }
            long currentIndex = Duration.between(schedule.startsAt(), now).getSeconds() / intervalSeconds;
            Instant scheduledAt = schedule.startsAt().plusSeconds(currentIndex * intervalSeconds);
            Instant nextDueAt = schedule.startsAt().plusSeconds((currentIndex + 1) * intervalSeconds);
            Instant expiresAt = earliest(scheduledAt.plus(properties.getDeliveryGrace()), nextDueAt, schedule.endsAt());
            boolean deliverable = source != null && source.eligible() && now.isBefore(expiresAt);
            if (deliverable) { created += enqueue(schedule, scheduledAt, expiresAt, source.ownerId(), now); }
            long missed = Math.max(0, currentIndex - schedule.nextIndex()) + (deliverable ? 0 : 1);
            advance(schedule, currentIndex + 1, nextDueAt.isBefore(schedule.endsAt()) ? nextDueAt : null, missed, now);
        }
        return created;
    }

    private int enqueue(Schedule schedule, Instant scheduledAt, Instant expiresAt, UUID recipientId, Instant now) {
        return jdbc.update("insert into clinical_reminder_email_tasks(id,schedule_id,scheduled_at,expires_at,recipient_id,status," +
                        "attempt_count,next_attempt_at,created_at) values(:id,:schedule,:scheduled,:expires,:recipient,'PENDING',0,:now,:now) " +
                        "on conflict(schedule_id,scheduled_at) do nothing",
                Map.of("id", UUID.randomUUID(), "schedule", schedule.id(), "scheduled", Timestamp.from(scheduledAt),
                        "expires", Timestamp.from(expiresAt), "recipient", recipientId, "now", Timestamp.from(now)));
    }

    private void advance(Schedule schedule, long nextIndex, Instant nextDueAt, long missed, Instant now) {
        jdbc.update("update clinical_reminder_schedules set next_occurrence_index=:index,next_due_at=:next,status=:status," +
                        "missed_count=missed_count+:missed,last_missed_at=case when :missed>0 then :now else last_missed_at end where id=:id",
                new MapSqlParameterSource().addValue("id", schedule.id()).addValue("index", nextIndex)
                        .addValue("next", nextDueAt == null ? null : Timestamp.from(nextDueAt))
                        .addValue("status", nextDueAt == null ? "FINISHED" : "ACTIVE").addValue("missed", missed).addValue("now", Timestamp.from(now)));
    }

    private static Instant earliest(Instant first, Instant second, Instant third) {
        Instant result = first.isBefore(second) ? first : second;
        return result.isBefore(third) ? result : third;
    }

    private record Schedule(UUID id, String sourceType, Instant startsAt, Instant endsAt, int intervalHours, Instant nextDueAt, long nextIndex) { }
}
