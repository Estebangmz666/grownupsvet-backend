package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalEncounterResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.CreateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception.ClinicalOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperationCommittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Clinical writes lock owner, pet, veterinarian and appointment in the scheduling domain's order. */
@Service
@Transactional(readOnly = true)
public class ClinicalEncounterService {
    private static final String ENCOUNTER_COLUMNS = "e.id,e.appointment_id,e.pet_id,e.owner_id,e.veterinarian_id," +
            "e.created_by,e.status,e.version,e.reason,e.anamnesis,e.physical_examination,e.diagnosis,e.care_plan," +
            "e.created_at,e.updated_at,e.finalized_at";
    private static final String ENCOUNTER_SELECT = "select " + ENCOUNTER_COLUMNS + " from clinical_encounters e ";

    private static final RowMapper<ClinicalEncounterResponseDTO> ENCOUNTER_ROW = (resultSet, rowNumber) -> {
        Timestamp finalizedAt = resultSet.getTimestamp("finalized_at");
        return new ClinicalEncounterResponseDTO(resultSet.getObject("id", UUID.class),
                resultSet.getObject("appointment_id", UUID.class), resultSet.getObject("pet_id", UUID.class),
                resultSet.getObject("owner_id", UUID.class), resultSet.getObject("veterinarian_id", UUID.class),
                resultSet.getObject("created_by", UUID.class), ClinicalEncounterStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"), resultSet.getString("reason"), resultSet.getString("anamnesis"),
                resultSet.getString("physical_examination"), resultSet.getString("diagnosis"), resultSet.getString("care_plan"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant(),
                finalizedAt == null ? null : finalizedAt.toInstant());
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public record CreationResult(ClinicalEncounterResponseDTO encounter, boolean created) { }

    public ClinicalEncounterService(NamedParameterJdbcTemplate jdbc, Clock clock, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public CreationResult create(UUID actorId, CreateClinicalEncounterRequestDTO request) {
        requireVeterinarian(actorId, false);
        if (request == null || request.appointmentId() == null || request.clientRequestId() == null) {
            throw invalidRequest();
        }
        String requestFingerprint = fingerprint(request.appointmentId().toString());
        ClinicalAppointment appointment = lockClinicalParticipants(actorId, request.appointmentId());
        // The veterinarian row serializes request keys across all their appointments.
        StoredOperation existingOperation = operation(actorId, "CREATE", request.clientRequestId());
        if (existingOperation != null && !existingOperation.fingerprint().equals(requestFingerprint)) {
            throw idempotencyConflict();
        }
        if (existingOperation != null) {
            ClinicalMutationContext replay = new ClinicalMutationContext(lockEncounter(existingOperation.encounterId()), appointment);
            requireEligibleReplay(replay);
            return new CreationResult(replay.encounter(), false);
        }
        requireEligibleAppointment(appointment);
        Boolean alreadyExists = jdbc.queryForObject("select exists(select 1 from clinical_encounters where appointment_id=:appointment)",
                Map.of("appointment", appointment.id()), Boolean.class);
        if (Boolean.TRUE.equals(alreadyExists)) {
            throw conflict("CLINICAL_ENCOUNTER_ALREADY_EXISTS", "La cita ya tiene una atención. Consulta el borrador existente.");
        }

        Instant now = clock.instant();
        UUID encounterId = UUID.randomUUID();
        ClinicalEncounterResponseDTO created = jdbc.queryForObject("insert into clinical_encounters as e(id,appointment_id,pet_id,owner_id,veterinarian_id,created_by,status,version,created_at,updated_at) " +
                        "values(:id,:appointment,:pet,:owner,:veterinarian,:veterinarian,'DRAFT',0,:now,:now) returning " + ENCOUNTER_COLUMNS,
                new MapSqlParameterSource().addValue("id", encounterId).addValue("appointment", appointment.id())
                        .addValue("pet", appointment.petId()).addValue("owner", appointment.ownerId())
                        .addValue("veterinarian", actorId).addValue("now", Timestamp.from(now)), ENCOUNTER_ROW);
        insertEvent(encounterId, 0, "CREATED", actorId, null, now);
        insertOperation(encounterId, actorId, "CREATE", request.clientRequestId(), requestFingerprint, 0, now);
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_DRAFT_CREATE));
        return new CreationResult(created, true);
    }

    public ClinicalEncounterResponseDTO get(UUID actorId, UUID encounterId) {
        ClinicalReaderContext reader = requireClinicalReader(actorId);
        return jdbc.query(ENCOUNTER_SELECT + "where e.id=:encounter and " + encounterReadPredicate(reader),
                Map.of("encounter", encounterId, "actor", actorId), ENCOUNTER_ROW)
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
    }

    public ClinicalEncounterResponseDTO getByAppointment(UUID actorId, UUID appointmentId) {
        ClinicalReaderContext reader = requireClinicalReader(actorId);
        return jdbc.query(ENCOUNTER_SELECT + "where e.appointment_id=:appointment and " + encounterReadPredicate(reader),
                Map.of("appointment", appointmentId, "actor", actorId), ENCOUNTER_ROW)
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
    }

    /** History validates access to the pet even when no published encounter exists. */
    ClinicalReaderContext requirePublishedHistoryReader(UUID actorId, UUID petId) {
        ClinicalReaderContext reader = requireClinicalReader(actorId);
        String petAccess = reader.owner() ? "p.owner_id=:actor" : "(" + confirmedPetAssignmentPredicate("p.id") +
                " or exists (select 1 from clinical_encounters own_history where own_history.pet_id=p.id " +
                "and own_history.status='FINALIZED' and own_history.veterinarian_id=:actor))";
        Boolean visible = jdbc.queryForObject("select exists(select 1 from pets p where p.id=:pet and " + petAccess + ")",
                Map.of("pet", petId, "actor", actorId), Boolean.class);
        if (!Boolean.TRUE.equals(visible)) { throw notFound(); }
        return reader;
    }

    /** The same published-resource predicate protects list, detail, treatment and discovery. */
    String publishedReadPredicate(ClinicalReaderContext reader) {
        if (reader.owner()) {
            return "(e.status='FINALIZED' and e.owner_id=:actor and exists " +
                    "(select 1 from pets owned_pet where owned_pet.id=e.pet_id and owned_pet.owner_id=:actor))";
        }
        // The immutable final responsible veterinarian retains only their own published encounters.
        return "(e.status='FINALIZED' and (e.veterinarian_id=:actor or " + confirmedPetAssignmentPredicate("e.pet_id") + "))";
    }

    private String encounterReadPredicate(ClinicalReaderContext reader) {
        String published = publishedReadPredicate(reader);
        if (reader.owner()) { return published; }
        return "(" + published + " or (e.status='DRAFT' and exists " +
                "(select 1 from appointments draft_appointment join veterinarian_availability_slots draft_slot " +
                "on draft_slot.id=draft_appointment.current_slot_id where draft_appointment.id=e.appointment_id " +
                "and draft_appointment.status='CONFIRMED' and draft_appointment.assignment_status='ASSIGNED' " +
                "and draft_slot.veterinarian_id=:actor)))";
    }

    private static String confirmedPetAssignmentPredicate(String petColumn) {
        // Column expressions are fixed internal aliases, never request input.
        return "exists (select 1 from appointments assigned_appointment join veterinarian_availability_slots assigned_slot " +
                "on assigned_slot.id=assigned_appointment.current_slot_id where assigned_appointment.pet_id=" + petColumn +
                " and assigned_appointment.status='CONFIRMED' and assigned_appointment.assignment_status='ASSIGNED' " +
                "and assigned_slot.veterinarian_id=:actor)";
    }

    @Transactional
    public ClinicalEncounterResponseDTO update(UUID actorId, UUID encounterId, UpdateClinicalEncounterRequestDTO request) {
        requireVeterinarian(actorId, false);
        if (request == null || request.clientRequestId() == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalidRequest();
        }
        ClinicalContent content = new ClinicalContent(normalize(request.reason()), normalize(request.anamnesis()),
                normalize(request.physicalExamination()), normalize(request.diagnosis()), normalize(request.carePlan()));
        String requestFingerprint = fingerprint(encounterId.toString(), request.expectedVersion().toString(),
                content.reason(), content.anamnesis(), content.physicalExamination(), content.diagnosis(), content.carePlan());
        ClinicalMutationContext context = lockForValidatedVeterinarian(actorId, encounterId);
        ClinicalEncounterResponseDTO encounter = context.encounter();
        if (isMutationReplay(actorId, "UPDATE", request.clientRequestId(), requestFingerprint, context)) { return encounter; }
        requireMutableDraft(context, request.expectedVersion());

        Instant now = clock.instant();
        long nextVersion = encounter.version() + 1;
        ClinicalEncounterResponseDTO updated = jdbc.queryForObject("update clinical_encounters as e set reason=:reason,anamnesis=:anamnesis,physical_examination=:examination," +
                        "diagnosis=:diagnosis,care_plan=:plan,veterinarian_id=:veterinarian,version=:version,updated_at=:now where id=:id returning " + ENCOUNTER_COLUMNS,
                new MapSqlParameterSource().addValue("id", encounterId).addValue("reason", content.reason())
                        .addValue("anamnesis", content.anamnesis()).addValue("examination", content.physicalExamination())
                        .addValue("diagnosis", content.diagnosis()).addValue("plan", content.carePlan())
                        .addValue("veterinarian", actorId).addValue("version", nextVersion).addValue("now", Timestamp.from(now)), ENCOUNTER_ROW);
        recordMutationAudit(actorId, context, "UPDATED", "UPDATE", request.clientRequestId(), requestFingerprint, now);
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_DRAFT_SAVE));
        return updated;
    }

    // Internal mutation support; public callers provide the encompassing transaction.
    ClinicalMutationContext lockForMutation(UUID actorId, UUID encounterId) {
        requireVeterinarian(actorId, false);
        return lockForValidatedVeterinarian(actorId, encounterId);
    }

    private ClinicalMutationContext lockForValidatedVeterinarian(UUID actorId, UUID encounterId) {
        ClinicalAppointmentIdentity snapshot = jdbc.query("select e.appointment_id,a.owner_id,a.pet_id from clinical_encounters e " +
                        "join appointments a on a.id=e.appointment_id join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "where e.id=:encounter and s.veterinarian_id=:actor",
                Map.of("encounter", encounterId, "actor", actorId), (resultSet, rowNumber) ->
                        new ClinicalAppointmentIdentity(resultSet.getObject("appointment_id", UUID.class),
                                resultSet.getObject("owner_id", UUID.class), resultSet.getObject("pet_id", UUID.class)))
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
        ClinicalAppointment appointment = lockClinicalParticipants(actorId, snapshot);
        return new ClinicalMutationContext(lockEncounter(encounterId), appointment);
    }

    /** The caller validates the locked context; transaction completion still precedes the HTTP response. */
    ClinicalEncounterResponseDTO finalizeLockedEncounter(UUID actorId, ClinicalMutationContext context, Instant now) {
        return jdbc.queryForObject("update clinical_encounters as e set status='FINALIZED',finalized_at=:now,updated_at=:now," +
                        "veterinarian_id=:actor,version=version+1 where id=:id returning " + ENCOUNTER_COLUMNS,
                Map.of("now", Timestamp.from(now), "actor", actorId, "id", context.encounter().id()), ENCOUNTER_ROW);
    }

    boolean isMutationReplay(UUID actorId, String operationType, UUID clientRequestId, String requestFingerprint, ClinicalMutationContext context) {
        StoredOperation existingOperation = operation(actorId, operationType, clientRequestId);
        if (existingOperation == null) { return false; }
        if (!existingOperation.encounterId().equals(context.encounter().id()) || !existingOperation.fingerprint().equals(requestFingerprint)) {
            throw idempotencyConflict();
        }
        requireEligibleReplay(context);
        return true;
    }

    private void requireEligibleReplay(ClinicalMutationContext context) {
        if (context.encounter().status() == ClinicalEncounterStatus.DRAFT) {
            requireEligibleAppointment(context.appointment());
        }
    }

    void requireMutableDraft(ClinicalMutationContext context, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) { throw invalidRequest(); }
        requireEligibleAppointment(context.appointment());
        if (context.encounter().status() != ClinicalEncounterStatus.DRAFT) {
            throw conflict("CLINICAL_ENCOUNTER_FINALIZED", "La atención finalizada no puede modificarse.");
        }
        if (context.encounter().version() != expectedVersion || context.encounter().version() == Long.MAX_VALUE) {
            throw conflict("CONCURRENT_UPDATE", "La atención cambió. Actualiza el borrador e inténtalo de nuevo.");
        }
    }

    void advanceDraftVersion(UUID actorId, ClinicalMutationContext context, Instant now) {
        jdbc.update("update clinical_encounters set veterinarian_id=:actor,version=:version,updated_at=:now where id=:encounter",
                Map.of("actor", actorId, "version", context.encounter().version() + 1, "now", Timestamp.from(now),
                        "encounter", context.encounter().id()));
    }

    void recordMutationAudit(UUID actorId, ClinicalMutationContext context, String eventType, String operationType,
            UUID clientRequestId, String requestFingerprint, Instant now) {
        long nextVersion = context.encounter().version() + 1;
        insertEvent(context.encounter().id(), nextVersion, eventType, actorId, context.encounter().veterinarianId(), now);
        insertOperation(context.encounter().id(), actorId, operationType, clientRequestId, requestFingerprint, nextVersion, now);
    }

    private void requireVeterinarian(UUID actorId, boolean lock) {
        ClinicalAccount account = account(actorId, lock);
        if (!"VETERINARIAN".equals(account.role())) {
            throw accessDenied();
        }
    }

    private ClinicalReaderContext requireClinicalReader(UUID actorId) {
        ClinicalAccount account = account(actorId, false);
        if (!"OWNER".equals(account.role()) && !"VETERINARIAN".equals(account.role())) { throw accessDenied(); }
        return new ClinicalReaderContext(actorId, "OWNER".equals(account.role()));
    }

    private ClinicalAccount account(UUID actorId, boolean lock) {
        ClinicalAccount account = jdbc.query("select role,status from users where id=:id" + (lock ? " for update" : ""),
                Map.of("id", actorId), (resultSet, rowNumber) -> new ClinicalAccount(resultSet.getString("role"), resultSet.getString("status")))
                .stream().findFirst().orElseThrow(ClinicalEncounterService::accessDenied);
        if (!"ACTIVE".equals(account.status())) { throw accessDenied(); }
        return account;
    }

    private ClinicalAppointment lockAppointment(UUID actorId, UUID appointmentId) {
        return jdbc.query("select a.id,a.pet_id,a.owner_id,a.status,a.assignment_status,a.version,a.starts_at,a.current_slot_id,p.active pet_active," +
                        "p.owner_id pet_owner_id,u.status owner_status,u.role owner_role " +
                        "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "join pets p on p.id=a.pet_id join users u on u.id=a.owner_id " +
                        "where a.id=:appointment and s.veterinarian_id=:actor for update of a",
                Map.of("appointment", appointmentId, "actor", actorId), (resultSet, rowNumber) ->
                        new ClinicalAppointment(resultSet.getObject("id", UUID.class), resultSet.getObject("pet_id", UUID.class),
                                resultSet.getObject("owner_id", UUID.class), resultSet.getString("status"),
                                resultSet.getString("assignment_status"), resultSet.getBoolean("pet_active"),
                                resultSet.getObject("pet_owner_id", UUID.class), resultSet.getString("owner_status"),
                                resultSet.getString("owner_role"), resultSet.getLong("version"),
                                resultSet.getTimestamp("starts_at").toInstant(), resultSet.getObject("current_slot_id", UUID.class)))
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
    }

    private ClinicalAppointment lockClinicalParticipants(UUID actorId, UUID appointmentId) {
        ClinicalAppointmentIdentity snapshot = jdbc.query("select a.id appointment_id,a.owner_id,a.pet_id from appointments a " +
                        "join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "where a.id=:appointment and s.veterinarian_id=:actor",
                Map.of("appointment", appointmentId, "actor", actorId), (resultSet, rowNumber) ->
                        new ClinicalAppointmentIdentity(resultSet.getObject("appointment_id", UUID.class),
                                resultSet.getObject("owner_id", UUID.class), resultSet.getObject("pet_id", UUID.class)))
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
        return lockClinicalParticipants(actorId, snapshot);
    }

    private ClinicalAppointment lockClinicalParticipants(UUID actorId, ClinicalAppointmentIdentity snapshot) {
        jdbc.queryForObject("select id from users where id=:owner for update", Map.of("owner", snapshot.ownerId()), UUID.class);
        jdbc.queryForObject("select id from pets where id=:pet for update", Map.of("pet", snapshot.petId()), UUID.class);
        requireVeterinarian(actorId, true);
        ClinicalAppointment appointment = lockAppointment(actorId, snapshot.appointmentId());
        if (!appointment.ownerId().equals(snapshot.ownerId()) || !appointment.petId().equals(snapshot.petId())) {
            throw conflict("CONCURRENT_UPDATE", "La cita cambió. Actualiza sus datos e inténtalo de nuevo.");
        }
        return appointment;
    }

    private static void requireEligibleAppointment(ClinicalAppointment appointment) {
        if (!"CONFIRMED".equals(appointment.status()) || !"ASSIGNED".equals(appointment.assignmentStatus())) {
            throw conflict("CLINICAL_APPOINTMENT_NOT_ELIGIBLE", "La atención requiere una cita confirmada con veterinario asignado.");
        }
        if (!appointment.petActive() || !"ACTIVE".equals(appointment.ownerStatus()) || !"OWNER".equals(appointment.ownerRole())
                || !appointment.ownerId().equals(appointment.petOwnerId())) {
            throw conflict("CLINICAL_PARTICIPANT_INACTIVE", "El propietario o la mascota ya no están activos para esta atención.");
        }
    }

    private ClinicalEncounterResponseDTO lockEncounter(UUID encounterId) {
        return jdbc.query(ENCOUNTER_SELECT + "where e.id=:id for update of e", Map.of("id", encounterId), ENCOUNTER_ROW)
                .stream().findFirst().orElseThrow(ClinicalEncounterService::notFound);
    }

    private StoredOperation operation(UUID actorId, String operationType, UUID clientRequestId) {
        return jdbc.query("select encounter_id,request_fingerprint from clinical_encounter_operations " +
                        "where actor_id=:actor and operation_type=:type and client_request_id=:request",
                Map.of("actor", actorId, "type", operationType, "request", clientRequestId),
                (resultSet, rowNumber) -> new StoredOperation(resultSet.getObject("encounter_id", UUID.class), resultSet.getString("request_fingerprint")))
                .stream().findFirst().orElse(null);
    }

    private void insertOperation(UUID encounterId, UUID actorId, String operationType, UUID clientRequestId,
            String requestFingerprint, long resultingVersion, Instant now) {
        jdbc.update("insert into clinical_encounter_operations(id,encounter_id,actor_id,operation_type,client_request_id," +
                        "request_fingerprint,resulting_version,created_at) values(:id,:encounter,:actor,:type,:request,:fingerprint,:version,:now)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("encounter", encounterId)
                        .addValue("actor", actorId).addValue("type", operationType).addValue("request", clientRequestId)
                        .addValue("fingerprint", requestFingerprint).addValue("version", resultingVersion).addValue("now", Timestamp.from(now)));
    }

    private void insertEvent(UUID encounterId, long version, String eventType, UUID actorId, UUID previousVeterinarianId, Instant now) {
        jdbc.update("insert into clinical_encounter_events(id,encounter_id,encounter_version,event_type,actor_id," +
                        "previous_veterinarian_id,veterinarian_id,occurred_at) values(:id,:encounter,:version,:type,:actor,:previous,:actor,:now)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("encounter", encounterId)
                        .addValue("version", version).addValue("type", eventType).addValue("actor", actorId)
                        .addValue("previous", previousVeterinarianId).addValue("now", Timestamp.from(now)));
    }

    private static String normalize(String value) {
        if (value == null) { return null; }
        if (value.length() > 4000 || value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')) {
            throw invalidRequest();
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Length-prefixing avoids collisions between absent values, empty strings and embedded separators. */
    static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] encoded = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded == null ? -1 : encoded.length).array());
                if (encoded != null) { digest.update(encoded); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("The required SHA-256 algorithm is unavailable.");
        }
    }

    private static ClinicalOperationException invalidRequest() {
        return new ClinicalOperationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Los datos de la atención no son válidos.");
    }

    private static ClinicalOperationException accessDenied() {
        return new ClinicalOperationException(HttpStatus.FORBIDDEN, "CLINICAL_ACCESS_DENIED", "No tienes permiso para realizar esta operación clínica.");
    }

    private static ClinicalOperationException idempotencyConflict() {
        return conflict("CLINICAL_IDEMPOTENCY_CONFLICT", "La clave de la solicitud ya se utilizó con otros datos.");
    }

    private static ClinicalOperationException notFound() {
        return new ClinicalOperationException(HttpStatus.NOT_FOUND, "CLINICAL_ENCOUNTER_NOT_FOUND", "La atención no existe o no está disponible para tu cuenta.");
    }

    private static ClinicalOperationException conflict(String errorCode, String detail) {
        return new ClinicalOperationException(HttpStatus.CONFLICT, errorCode, detail);
    }

    private record ClinicalAccount(String role, String status) { }
    record ClinicalReaderContext(UUID actorId, boolean owner) { }
    private record ClinicalAppointmentIdentity(UUID appointmentId, UUID ownerId, UUID petId) { }
    record ClinicalAppointment(UUID id, UUID petId, UUID ownerId, String status, String assignmentStatus,
            boolean petActive, UUID petOwnerId, String ownerStatus, String ownerRole,
            long version, Instant startsAt, UUID currentSlotId) { }
    record ClinicalMutationContext(ClinicalEncounterResponseDTO encounter, ClinicalAppointment appointment) { }
    private record StoredOperation(UUID encounterId, String fingerprint) { }
    private record ClinicalContent(String reason, String anamnesis, String physicalExamination, String diagnosis, String carePlan) { }
}
