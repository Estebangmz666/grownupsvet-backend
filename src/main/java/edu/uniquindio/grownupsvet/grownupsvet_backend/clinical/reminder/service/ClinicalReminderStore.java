package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Shared source validation; no message payloads or recipient addresses are copied into durable tasks. */
@Component
class ClinicalReminderStore {
    private final NamedParameterJdbcTemplate jdbc;

    ClinicalReminderStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    ReminderSource source(UUID scheduleId) {
        return jdbc.query("select s.id,s.source_type,s.status schedule_status,s.ends_at,a.owner_id,u.email,p.name pet_name," +
                        "rx.medication_name,rx.dose,rx.dose_unit,rx.administration_route,rx.instructions,c.scheduled_at control_at,c.reason control_reason," +
                        "(e.status='FINALIZED' and a.status='COMPLETED' and u.status='ACTIVE' and u.role='OWNER' and p.active " +
                        "and e.pet_id=a.pet_id and e.owner_id=a.owner_id and p.owner_id=a.owner_id " +
                        "and ((s.source_type='PRESCRIPTION' and rx.id is not null) or (s.source_type='CONTROL' and c.id is not null))) eligible " +
                        "from clinical_reminder_schedules s join clinical_encounters e on e.id=s.encounter_id " +
                        "join appointments a on a.id=e.appointment_id join pets p on p.id=a.pet_id join users u on u.id=a.owner_id " +
                        "left join clinical_prescriptions rx on rx.encounter_id=s.encounter_id and rx.id=s.prescription_id " +
                        "left join clinical_controls c on c.encounter_id=s.encounter_id and c.id=s.control_id where s.id=:id",
                Map.of("id", scheduleId), (resultSet, rowNumber) -> {
                    Timestamp controlAt = resultSet.getTimestamp("control_at");
                    return new ReminderSource(resultSet.getObject("id", UUID.class), resultSet.getString("source_type"),
                            resultSet.getString("schedule_status"), resultSet.getTimestamp("ends_at").toInstant(),
                            resultSet.getObject("owner_id", UUID.class), resultSet.getString("email"), resultSet.getBoolean("eligible"),
                            resultSet.getString("pet_name"), resultSet.getString("medication_name"), resultSet.getBigDecimal("dose"),
                            resultSet.getString("dose_unit"), resultSet.getString("administration_route"), resultSet.getString("instructions"),
                            controlAt == null ? null : controlAt.toInstant(), resultSet.getString("control_reason"));
                }).stream().findFirst().orElse(null);
    }

    record ReminderSource(UUID scheduleId, String sourceType, String scheduleStatus, Instant endsAt, UUID ownerId,
            String ownerEmail, boolean eligible, String petName, String medicationName, BigDecimal dose, String doseUnit,
            String administrationRoute, String instructions, Instant controlAt, String controlReason) { }
}
