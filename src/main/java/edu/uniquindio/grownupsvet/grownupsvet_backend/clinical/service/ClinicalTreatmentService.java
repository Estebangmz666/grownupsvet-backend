package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalControlRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalControlResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalPrescriptionRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalPrescriptionResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalTreatmentResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalTreatmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception.ClinicalOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperationCommittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class ClinicalTreatmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicalEncounterService encounters;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ClinicalTreatmentService(NamedParameterJdbcTemplate jdbc, ClinicalEncounterService encounters, Clock clock, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.encounters = encounters;
        this.clock = clock;
        this.events = events;
    }

    // The parent version and both lists must come from one PostgreSQL snapshot.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClinicalTreatmentResponseDTO get(UUID actorId, UUID encounterId) {
        var encounter = encounters.get(actorId, encounterId);
        return readTreatment(encounterId, encounter.version());
    }

    @Transactional
    public ClinicalTreatmentResponseDTO update(UUID actorId, UUID encounterId, UpdateClinicalTreatmentRequestDTO request) {
        if (request == null || request.clientRequestId() == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.prescriptions() == null || request.controls() == null
                || request.prescriptions().size() > 50 || request.controls().size() > 50) {
            throw invalidTreatment();
        }
        List<ClinicalPrescriptionResponseDTO> prescriptions = normalizePrescriptions(request.prescriptions());
        List<ClinicalControlResponseDTO> controls = normalizeControls(request.controls());
        String requestFingerprint = fingerprint(encounterId, request.expectedVersion(), prescriptions, controls);
        var context = encounters.lockForMutation(actorId, encounterId);
        if (encounters.isMutationReplay(actorId, "TREATMENT", request.clientRequestId(), requestFingerprint, context)) {
            return readTreatment(encounterId, context.encounter().version());
        }
        encounters.requireMutableDraft(context, request.expectedVersion());

        jdbc.update("delete from clinical_prescriptions where encounter_id=:encounter", Map.of("encounter", encounterId));
        jdbc.update("delete from clinical_controls where encounter_id=:encounter", Map.of("encounter", encounterId));
        List<ClinicalPrescriptionResponseDTO> storedPrescriptions = insertPrescriptions(encounterId, prescriptions);
        List<ClinicalControlResponseDTO> storedControls = insertControls(encounterId, controls);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        encounters.advanceDraftVersion(actorId, context, now);
        encounters.recordMutationAudit(actorId, context, "TREATMENT_UPDATED", "TREATMENT", request.clientRequestId(), requestFingerprint, now);
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_TREATMENT_SAVE));
        return new ClinicalTreatmentResponseDTO(encounterId, context.encounter().version() + 1, storedPrescriptions, storedControls);
    }

    private ClinicalTreatmentResponseDTO readTreatment(UUID encounterId, long version) {
        List<ClinicalPrescriptionResponseDTO> prescriptions = new ArrayList<>();
        List<ClinicalControlResponseDTO> controls = new ArrayList<>();
        // Independent branches preserve empty/asymmetric lists without a prescription-control product.
        jdbc.query("""
                select 1 child_type,position,id,medication_name,dose,dose_unit,administration_route,
                    interval_hours,starts_at,ends_at,instructions,cast(null as timestamptz) scheduled_at,cast(null as varchar) reason
                from clinical_prescriptions where encounter_id=:encounter
                union all
                select 2 child_type,position,id,cast(null as varchar) medication_name,cast(null as numeric(18,6)) dose,
                    cast(null as varchar) dose_unit,cast(null as varchar) administration_route,cast(null as integer) interval_hours,
                    cast(null as timestamptz) starts_at,cast(null as timestamptz) ends_at,cast(null as varchar) instructions,scheduled_at,reason
                from clinical_controls where encounter_id=:encounter
                order by child_type,position
                """, Map.of("encounter", encounterId), resultSet -> {
            if (resultSet.getInt("child_type") == 1) {
                prescriptions.add(prescription(resultSet));
            } else {
                controls.add(control(resultSet));
            }
        });
        return new ClinicalTreatmentResponseDTO(encounterId, version, List.copyOf(prescriptions), List.copyOf(controls));
    }

    private List<ClinicalPrescriptionResponseDTO> insertPrescriptions(UUID encounterId, List<ClinicalPrescriptionResponseDTO> prescriptions) {
        if (prescriptions.isEmpty()) { return List.of(); }
        StringJoiner values = new StringJoiner(",");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("encounter", encounterId);
        for (int position = 0; position < prescriptions.size(); position++) {
            ClinicalPrescriptionResponseDTO prescription = prescriptions.get(position);
            String suffix = Integer.toString(position);
            values.add("(:encounter,:id" + suffix + ",:position" + suffix + ",:medication" + suffix + ",:dose" + suffix +
                    ",:unit" + suffix + ",:route" + suffix + ",:interval" + suffix + ",:start" + suffix + ",:end" + suffix + ",:instructions" + suffix + ")");
            parameters.addValue("id" + suffix, prescription.id()).addValue("position" + suffix, position)
                    .addValue("medication" + suffix, prescription.medicationName()).addValue("dose" + suffix, prescription.dose())
                    .addValue("unit" + suffix, prescription.doseUnit()).addValue("route" + suffix, prescription.administrationRoute())
                    .addValue("interval" + suffix, prescription.intervalHours()).addValue("start" + suffix, Timestamp.from(prescription.startsAt()))
                    .addValue("end" + suffix, Timestamp.from(prescription.endsAt())).addValue("instructions" + suffix, prescription.instructions());
        }
        return jdbc.query("insert into clinical_prescriptions(encounter_id,id,position,medication_name,dose,dose_unit,administration_route," +
                        "interval_hours,starts_at,ends_at,instructions) values " + values +
                        " returning id,position,medication_name,dose,dose_unit,administration_route,interval_hours,starts_at,ends_at,instructions",
                parameters, (resultSet, rowNumber) -> new PositionedPrescription(resultSet.getInt("position"), prescription(resultSet)))
                .stream().sorted(Comparator.comparingInt(PositionedPrescription::position)).map(PositionedPrescription::value).toList();
    }

    private List<ClinicalControlResponseDTO> insertControls(UUID encounterId, List<ClinicalControlResponseDTO> controls) {
        if (controls.isEmpty()) { return List.of(); }
        StringJoiner values = new StringJoiner(",");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("encounter", encounterId);
        for (int position = 0; position < controls.size(); position++) {
            ClinicalControlResponseDTO control = controls.get(position);
            String suffix = Integer.toString(position);
            values.add("(:encounter,:id" + suffix + ",:position" + suffix + ",:scheduled" + suffix + ",:reason" + suffix + ")");
            parameters.addValue("id" + suffix, control.id()).addValue("position" + suffix, position)
                    .addValue("scheduled" + suffix, Timestamp.from(control.scheduledAt())).addValue("reason" + suffix, control.reason());
        }
        return jdbc.query("insert into clinical_controls(encounter_id,id,position,scheduled_at,reason) values " + values +
                        " returning id,position,scheduled_at,reason", parameters,
                (resultSet, rowNumber) -> new PositionedControl(resultSet.getInt("position"), control(resultSet)))
                .stream().sorted(Comparator.comparingInt(PositionedControl::position)).map(PositionedControl::value).toList();
    }

    private static ClinicalPrescriptionResponseDTO prescription(ResultSet resultSet) throws SQLException {
        return new ClinicalPrescriptionResponseDTO(resultSet.getObject("id", UUID.class), resultSet.getString("medication_name"),
                resultSet.getBigDecimal("dose"), resultSet.getString("dose_unit"), resultSet.getString("administration_route"),
                resultSet.getInt("interval_hours"), resultSet.getTimestamp("starts_at").toInstant(),
                resultSet.getTimestamp("ends_at").toInstant(), resultSet.getString("instructions"));
    }

    private static ClinicalControlResponseDTO control(ResultSet resultSet) throws SQLException {
        return new ClinicalControlResponseDTO(resultSet.getObject("id", UUID.class), resultSet.getTimestamp("scheduled_at").toInstant(),
                resultSet.getString("reason"));
    }

    private record PositionedPrescription(int position, ClinicalPrescriptionResponseDTO value) { }
    private record PositionedControl(int position, ClinicalControlResponseDTO value) { }

    private static List<ClinicalPrescriptionResponseDTO> normalizePrescriptions(List<ClinicalPrescriptionRequestDTO> requests) {
        List<ClinicalPrescriptionResponseDTO> prescriptions = new ArrayList<>();
        HashSet<UUID> identifiers = new HashSet<>();
        for (ClinicalPrescriptionRequestDTO request : requests) {
            if (request == null || request.id() == null || !identifiers.add(request.id()) || request.dose() == null
                    || request.dose().signum() <= 0 || request.dose().scale() > 6 || (long) request.dose().precision() - request.dose().scale() > 12
                    || request.intervalHours() == null || request.intervalHours() < 1 || request.intervalHours() > 168) {
                throw invalidTreatment();
            }
            Instant startsAt = timestamp(request.startsAt());
            Instant endsAt = timestamp(request.endsAt());
            if (!endsAt.isAfter(startsAt) || Duration.between(startsAt, endsAt).compareTo(Duration.ofDays(366)) > 0) {
                throw invalidTreatment();
            }
            prescriptions.add(new ClinicalPrescriptionResponseDTO(request.id(), text(request.medicationName(), 200, true, false),
                    request.dose().stripTrailingZeros(), text(request.doseUnit(), 50, true, false),
                    text(request.administrationRoute(), 100, true, false), request.intervalHours(), startsAt, endsAt,
                    text(request.instructions(), 2000, false, true)));
        }
        return List.copyOf(prescriptions);
    }

    private static List<ClinicalControlResponseDTO> normalizeControls(List<ClinicalControlRequestDTO> requests) {
        List<ClinicalControlResponseDTO> controls = new ArrayList<>();
        HashSet<UUID> identifiers = new HashSet<>();
        for (ClinicalControlRequestDTO request : requests) {
            if (request == null || request.id() == null || !identifiers.add(request.id())) { throw invalidTreatment(); }
            controls.add(new ClinicalControlResponseDTO(request.id(), timestamp(request.scheduledAt()), text(request.reason(), 1000, true, true)));
        }
        return List.copyOf(controls);
    }

    private static Instant timestamp(OffsetDateTime value) {
        if (value == null || value.getYear() < 1 || value.getYear() > 9999) { throw invalidTreatment(); }
        Instant instant = value.toInstant().truncatedTo(ChronoUnit.MICROS);
        int utcYear = instant.atOffset(ZoneOffset.UTC).getYear();
        if (utcYear < 1 || utcYear > 9999) { throw invalidTreatment(); }
        return instant;
    }

    private static String text(String value, int maximumLength, boolean required, boolean multiline) {
        if (value == null) {
            if (required) { throw invalidTreatment(); }
            return null;
        }
        if (value.length() > maximumLength || value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) && !(multiline && (codePoint == '\n' || codePoint == '\r' || codePoint == '\t')))) {
            throw invalidTreatment();
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            if (required) { throw invalidTreatment(); }
            return null;
        }
        return normalized;
    }

    private static String fingerprint(UUID encounterId, long expectedVersion, List<ClinicalPrescriptionResponseDTO> prescriptions,
            List<ClinicalControlResponseDTO> controls) {
        List<String> values = new ArrayList<>();
        values.add(encounterId.toString());
        values.add(Long.toString(expectedVersion));
        values.add(Integer.toString(prescriptions.size()));
        for (ClinicalPrescriptionResponseDTO prescription : prescriptions) {
            values.add(prescription.id().toString());
            values.add(prescription.medicationName());
            values.add(prescription.dose().toPlainString());
            values.add(prescription.doseUnit());
            values.add(prescription.administrationRoute());
            values.add(Integer.toString(prescription.intervalHours()));
            values.add(prescription.startsAt().toString());
            values.add(prescription.endsAt().toString());
            values.add(prescription.instructions());
        }
        values.add(Integer.toString(controls.size()));
        for (ClinicalControlResponseDTO control : controls) {
            values.add(control.id().toString());
            values.add(control.scheduledAt().toString());
            values.add(control.reason());
        }
        return ClinicalEncounterService.fingerprint(values.toArray(String[]::new));
    }

    private static ClinicalOperationException invalidTreatment() {
        return new ClinicalOperationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La fórmula o los controles contienen datos inválidos.");
    }
}
