package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalEncounterResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.FinalizeClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception.ClinicalOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterFinalizedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperationCommittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Service
public class ClinicalFinalizationService {
    private final ClinicalEncounterService encounters;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ClinicalFinalizationService(ClinicalEncounterService encounters, NamedParameterJdbcTemplate jdbc,
            Clock clock, ApplicationEventPublisher events) {
        this.encounters = encounters; this.jdbc = jdbc; this.clock = clock; this.events = events;
    }

    @Transactional
    public ClinicalEncounterResponseDTO finalizeEncounter(UUID actorId, UUID encounterId, FinalizeClinicalEncounterRequestDTO request) {
        if (request == null || request.clientRequestId() == null || request.expectedVersion() == null
                || request.expectedVersion() < 0 || request.expectedAppointmentVersion() == null || request.expectedAppointmentVersion() < 0) {
            throw new ClinicalOperationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La finalización requiere ambas versiones y una clave de solicitud.");
        }
        String fingerprint = ClinicalEncounterService.fingerprint(encounterId.toString(), request.expectedVersion().toString(),
                request.expectedAppointmentVersion().toString());
        var context = encounters.lockForMutation(actorId, encounterId);
        if (encounters.isMutationReplay(actorId, "FINALIZE", request.clientRequestId(), fingerprint, encounterId)) {
            return context.encounter();
        }
        encounters.requireMutableDraft(context, request.expectedVersion());
        var appointment = context.appointment();
        var encounter = context.encounter();
        if (appointment.version() != request.expectedAppointmentVersion() || appointment.version() == Long.MAX_VALUE) {
            throw conflict("CONCURRENT_UPDATE", "La cita cambió. Actualiza la información antes de finalizar.");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(appointment.startsAt())) {
            throw conflict("CLINICAL_APPOINTMENT_NOT_STARTED", "La atención solo puede finalizarse desde el inicio del turno.");
        }
        if (Arrays.stream(new String[]{encounter.reason(), encounter.anamnesis(), encounter.physicalExamination(),
                encounter.diagnosis(), encounter.carePlan()}).anyMatch(value -> value == null || value.isBlank())) {
            throw new ClinicalOperationException(HttpStatus.BAD_REQUEST, "CLINICAL_REQUIRED_FIELDS_MISSING",
                    "Completa motivo, anamnesis, examen físico, diagnóstico y plan antes de finalizar.");
        }
        jdbc.update("update clinical_encounters set status='FINALIZED',finalized_at=:now,updated_at=:now," +
                        "veterinarian_id=:actor,version=version+1 where id=:id",
                Map.of("now", Timestamp.from(now), "actor", actorId, "id", encounterId));
        jdbc.update("update appointments set status='COMPLETED',version=version+1,updated_at=:now where id=:id",
                Map.of("now", Timestamp.from(now), "id", appointment.id()));
        jdbc.update("insert into appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status," +
                        "previous_assignment_status,new_assignment_status,previous_slot_id,new_slot_id,actor_id,actor_type,occurred_at,reason) " +
                        "values(:id,:appointment,:version,'COMPLETED','CONFIRMED','COMPLETED','ASSIGNED','ASSIGNED',:slot,:slot,:actor,'HUMAN',:now,:reason)",
                Map.of("id", UUID.randomUUID(), "appointment", appointment.id(), "version", appointment.version() + 1,
                        "slot", appointment.currentSlotId(), "actor", actorId, "now", Timestamp.from(now), "reason", "Atención clínica finalizada."));
        encounters.recordMutationAudit(actorId, context, "FINALIZED", "FINALIZE", request.clientRequestId(), fingerprint, now);
        jdbc.update("update appointment_email_tasks set status='SUPERSEDED',lease_token=null,last_error_code='APPOINTMENT_COMPLETED' " +
                        "where appointment_id=:appointment and status in ('PENDING','SENDING','FAILED')",
                Map.of("appointment", appointment.id()));
        events.publishEvent(new ClinicalEncounterFinalizedEvent(encounterId));
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_FINALIZE));
        return encounters.readMutationResult(context);
    }

    private static ClinicalOperationException conflict(String code, String detail) {
        return new ClinicalOperationException(HttpStatus.CONFLICT, code, detail);
    }
}
