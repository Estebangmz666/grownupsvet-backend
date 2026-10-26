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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        if (encounters.isMutationReplay(actorId, "TREATMENT", request.clientRequestId(), requestFingerprint, encounterId)) {
            return readTreatment(encounterId, context.encounter().version());
        }
        encounters.requireMutableDraft(context, request.expectedVersion());

        jdbc.update("delete from clinical_prescriptions where encounter_id=:encounter", Map.of("encounter", encounterId));
        jdbc.update("delete from clinical_controls where encounter_id=:encounter", Map.of("encounter", encounterId));
        insertPrescriptions(encounterId, prescriptions);
        insertControls(encounterId, controls);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        encounters.advanceDraftVersion(actorId, context, now);
        encounters.recordMutationAudit(actorId, context, "TREATMENT_UPDATED", "TREATMENT", request.clientRequestId(), requestFingerprint, now);
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_TREATMENT_SAVE));
        return readTreatment(encounterId, context.encounter().version() + 1);
    }

    private ClinicalTreatmentResponseDTO readTreatment(UUID encounterId, long version) {
        List<ClinicalPrescriptionResponseDTO> prescriptions = jdbc.query("select id,medication_name,dose,dose_unit,administration_route," +
                        "interval_hours,starts_at,ends_at,instructions from clinical_prescriptions where encounter_id=:encounter order by position",
                Map.of("encounter", encounterId), (resultSet, rowNumber) -> new ClinicalPrescriptionResponseDTO(
                        resultSet.getObject("id", UUID.class), resultSet.getString("medication_name"), resultSet.getBigDecimal("dose"),
                        resultSet.getString("dose_unit"), resultSet.getString("administration_route"), resultSet.getInt("interval_hours"),
                        resultSet.getTimestamp("starts_at").toInstant(), resultSet.getTimestamp("ends_at").toInstant(),
                        resultSet.getString("instructions")));
        List<ClinicalControlResponseDTO> controls = jdbc.query("select id,scheduled_at,reason from clinical_controls " +
                        "where encounter_id=:encounter order by position",
                Map.of("encounter", encounterId), (resultSet, rowNumber) -> new ClinicalControlResponseDTO(
                        resultSet.getObject("id", UUID.class), resultSet.getTimestamp("scheduled_at").toInstant(), resultSet.getString("reason")));
        return new ClinicalTreatmentResponseDTO(encounterId, version, List.copyOf(prescriptions), List.copyOf(controls));
    }

    private void insertPrescriptions(UUID encounterId, List<ClinicalPrescriptionResponseDTO> prescriptions) {
        for (int position = 0; position < prescriptions.size(); position++) {
            ClinicalPrescriptionResponseDTO prescription = prescriptions.get(position);
            jdbc.update("insert into clinical_prescriptions(encounter_id,id,position,medication_name,dose,dose_unit,administration_route," +
                            "interval_hours,starts_at,ends_at,instructions) values(:encounter,:id,:position,:medication,:dose,:unit,:route,:interval,:start,:end,:instructions)",
                    new MapSqlParameterSource().addValue("encounter", encounterId).addValue("id", prescription.id()).addValue("position", position)
                            .addValue("medication", prescription.medicationName()).addValue("dose", prescription.dose()).addValue("unit", prescription.doseUnit())
                            .addValue("route", prescription.administrationRoute()).addValue("interval", prescription.intervalHours())
                            .addValue("start", Timestamp.from(prescription.startsAt())).addValue("end", Timestamp.from(prescription.endsAt()))
                            .addValue("instructions", prescription.instructions()));
        }
    }

    private void insertControls(UUID encounterId, List<ClinicalControlResponseDTO> controls) {
        for (int position = 0; position < controls.size(); position++) {
            ClinicalControlResponseDTO control = controls.get(position);
            jdbc.update("insert into clinical_controls(encounter_id,id,position,scheduled_at,reason) values(:encounter,:id,:position,:scheduled,:reason)",
                    Map.of("encounter", encounterId, "id", control.id(), "position", position,
                            "scheduled", Timestamp.from(control.scheduledAt()), "reason", control.reason()));
        }
    }

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
