package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.exception.AppointmentOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentAssignmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentNotificationsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AppointmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AppointmentProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ZoneId zone;

    private static final RowMapper<AppointmentResponseDTO> APPOINTMENT_ROW = (rs, row) -> new AppointmentResponseDTO(
            rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("owner_full_name"),
            rs.getString("owner_phone_number"), rs.getObject("pet_id", UUID.class), rs.getString("pet_name"),
            rs.getObject("veterinarian_id", UUID.class), rs.getString("veterinarian_full_name"),
            AppointmentStatus.valueOf(rs.getString("status")), AppointmentAssignmentStatus.valueOf(rs.getString("assignment_status")),
            rs.getString("reason"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
            rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), "America/Bogota");

    private static final String APPOINTMENT_SELECT = "select a.id, a.owner_id, op.full_name owner_full_name, op.phone_number owner_phone_number, " +
            "a.pet_id, p.name pet_name, s.veterinarian_id, vp.full_name veterinarian_full_name, a.status, a.assignment_status, " +
            "a.reason, a.starts_at, a.ends_at, a.version, a.created_at, a.updated_at " +
            "from appointments a join owner_profiles op on op.user_id=a.owner_id join pets p on p.id=a.pet_id " +
            "join veterinarian_availability_slots s on s.id=a.current_slot_id join veterinarian_profiles vp on vp.user_id=s.veterinarian_id ";

    public record AppointmentCreationResult(AppointmentResponseDTO appointment, boolean created) { }

    public AppointmentService(NamedParameterJdbcTemplate jdbc, AppointmentProperties properties, Clock clock,
            ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc; this.properties = properties; this.clock = clock; this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.zone = properties.getBusinessZone();
    }

    @Transactional
    public AppointmentCreationResult create(UUID ownerId, CreateAppointmentRequestDTO request) {
        Account owner = lockAccount(ownerId);
        requireRole(owner, UserRole.OWNER);
        String reason = requiredReason(request.reason());
        String fingerprint = fingerprint(request.petId(), request.availabilitySlotId(),
                request.expectedAvailabilitySlotVersion(), reason);
        AppointmentResponseDTO replay = replay(ownerId, request.clientRequestId(), fingerprint);
        if (replay != null) { return new AppointmentCreationResult(replay, false); }

        Pet pet = lockPet(request.petId());
        if (!pet.ownerId().equals(ownerId) || !pet.active()) { throw petUnavailable(); }
        Slot slot = readSlot(request.availabilitySlotId());
        lockAccount(slot.veterinarianId());
        slot = lockSlot(request.availabilitySlotId());
        requireActiveVeterinarian(slot.veterinarianId());
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate slotDate = slot.startsAt().atZone(zone).toLocalDate();
        Instant maximumStart = now.plus(properties.getMaximumOwnerHorizonDays(),ChronoUnit.DAYS);
        if (slotDate.isBefore(today.plusDays(properties.getMinimumOwnerAdvanceDays()))
                || slot.startsAt().isAfter(maximumStart)) {
            throw conflict("APPOINTMENT_OUTSIDE_REQUEST_WINDOW", "El turno está fuera del plazo disponible para nuevas solicitudes.");
        }
        if (!"PUBLISHED".equals(slot.status()) || slot.startsAt().isBefore(now)
                || slot.version() != request.expectedAvailabilitySlotVersion()) {
            throw conflict("APPOINTMENT_SLOT_NOT_AVAILABLE", "El turno ya no está disponible. Actualiza la agenda e inténtalo de nuevo.");
        }
        Instant cutoff = slotDate.atTime(properties.getWorkdayStartTime()).atZone(zone).toInstant();
        if (!now.isBefore(cutoff)) {
            throw conflict("APPOINTMENT_CONFIRMATION_CUTOFF_PASSED", "La hora de corte de esta fecha ya pasó.");
        }

        UUID appointmentId = UUID.randomUUID();
        List<UUID> inserted = jdbc.query("insert into appointments(id,owner_id,pet_id,current_slot_id,status,assignment_status,reason,starts_at,ends_at,confirmation_cutoff_at,client_request_id,request_fingerprint,version,created_at,updated_at) " +
                        "values(:id,:owner,:pet,:slot,'REQUESTED','ASSIGNED',:reason,:start,:end,:cutoff,:requestId,:fingerprint,0,:now,:now) " +
                        "on conflict do nothing returning id",
                new MapSqlParameterSource().addValue("id", appointmentId).addValue("owner", ownerId).addValue("pet", pet.id())
                        .addValue("slot", slot.id()).addValue("reason", reason).addValue("start", dbTime(slot.startsAt()))
                        .addValue("end", dbTime(slot.endsAt())).addValue("cutoff", dbTime(cutoff)).addValue("requestId", request.clientRequestId())
                        .addValue("fingerprint", fingerprint).addValue("now", dbTime(now)), (rs,row)->rs.getObject(1,UUID.class));
        if (inserted.isEmpty()) {
            AppointmentResponseDTO duplicate = replay(ownerId, request.clientRequestId(), fingerprint);
            if (duplicate != null) { return new AppointmentCreationResult(duplicate, false); }
            Boolean occupied = jdbc.queryForObject("select exists(select 1 from appointments where current_slot_id=:slot and status in ('REQUESTED','CONFIRMED'))",Map.of("slot",slot.id()),Boolean.class);
            if (occupied) { throw conflict("APPOINTMENT_SLOT_OCCUPIED", "Otra solicitud ocupó el turno. Elige otro horario."); }
            throw conflict("APPOINTMENT_PET_ALREADY_SCHEDULED", "La mascota ya tiene otra cita en ese horario.");
        }
        insertAssignment(appointmentId, slot, ownerId, now, "Solicitud de cita");
        insertEvent(appointmentId, 0, "REQUESTED", null, "REQUESTED", null, "ASSIGNED", null,
                slot.id(), ownerId, "HUMAN", now, reason, null, null, null);
        return new AppointmentCreationResult(get(ownerId, appointmentId), true);
    }

    public AppointmentPageResponseDTO list(UUID actorId, LocalDate from, LocalDate to, AppointmentStatus status, int page, int size) {
        Account actor = account(actorId);
        validatePageRange(from, to, page, size);
        String access = accessPredicate(actor.role());
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("actor", actorId)
                .addValue("from", dbTime(from.atStartOfDay(zone).toInstant())).addValue("to", dbTime(to.plusDays(1).atStartOfDay(zone).toInstant()))
                .addValue("status", status == null ? null : status.name()).addValue("limit", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("select count(*) from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where " + access +
                " and a.starts_at >= :from and a.starts_at < :to and (CAST(:status AS VARCHAR) is null or a.status=:status)", parameters, Long.class);
        List<AppointmentResponseDTO> items = jdbc.query(APPOINTMENT_SELECT + "where " + access +
                " and a.starts_at >= :from and a.starts_at < :to and (CAST(:status AS VARCHAR) is null or a.status=:status) " +
                "order by a.starts_at asc,a.id asc limit :limit offset :offset", parameters, APPOINTMENT_ROW);
        return new AppointmentPageResponseDTO(items, page, size, total, (int) Math.ceil((double) total / size));
    }

    public AppointmentResponseDTO get(UUID actorId, UUID appointmentId) {
        Account actor = account(actorId);
        return jdbc.query(APPOINTMENT_SELECT + "where a.id=:id and " + accessPredicate(actor.role()),
                Map.of("id", appointmentId, "actor", actorId), APPOINTMENT_ROW).stream().findFirst().orElseThrow(AppointmentService::notFound);
    }

    public AppointmentEventPageResponseDTO events(UUID actorId, UUID appointmentId, int page, int size) {
        Account actor = account(actorId);
        validatePage(page, size);
        if (!canView(actor, appointmentId)) { throw notFound(); }
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("appointment", appointmentId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        String privacyFilter = actor.role() == UserRole.ADMINISTRATOR ? "" : " and event_type <> 'EMAIL_ACCESSED'";
        long total = jdbc.queryForObject("select count(*) from appointment_events where appointment_id=:appointment" + privacyFilter, parameters, Long.class);
        List<AppointmentEventResponseDTO> items = jdbc.query("select id,appointment_id,appointment_version,event_type,previous_status,new_status,previous_assignment_status,new_assignment_status,previous_slot_id,new_slot_id,actor_id,actor_type,occurred_at,reason from appointment_events where appointment_id=:appointment" + privacyFilter +
                " order by occurred_at,id limit :limit offset :offset", parameters, (rs, row) -> new AppointmentEventResponseDTO(
                rs.getObject("id",UUID.class),rs.getObject("appointment_id",UUID.class),rs.getLong("appointment_version"),rs.getString("event_type"),
                rs.getString("previous_status"),rs.getString("new_status"),rs.getString("previous_assignment_status"),rs.getString("new_assignment_status"),
                rs.getObject("previous_slot_id",UUID.class),rs.getObject("new_slot_id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getString("actor_type"),
                rs.getTimestamp("occurred_at").toInstant(),rs.getString("reason")));
        return new AppointmentEventPageResponseDTO(items,page,size,total,(int)Math.ceil((double)total/size));
    }

    @Transactional
    public AppointmentResponseDTO updateStatus(UUID actorId, UUID appointmentId, UpdateAppointmentStatusRequestDTO request) {
        Account actor = lockAccount(actorId);
        requireRole(actor, UserRole.ADMINISTRATOR);
        AppointmentStatus target = request.status();
        if (target != AppointmentStatus.CONFIRMED && target != AppointmentStatus.REJECTED) {
            throw conflict("APPOINTMENT_TRANSITION_NOT_ALLOWED", "Esta operación solo permite confirmar o rechazar una solicitud.");
        }
        String reason = target == AppointmentStatus.REJECTED ? requiredReason(request.reason()) : optionalReason(request.reason());
        AppointmentRow snapshot = appointmentSnapshot(appointmentId);
        if (target == AppointmentStatus.CONFIRMED) {
            lockAccount(snapshot.ownerId());
            lockPet(snapshot.petId());
        }
        lockAccount(snapshot.veterinarianId());
        // Appointment precedes slot, including the KEY SHARE locks acquired by event foreign keys.
        // Cutoff and rejection must not invert this order while confirmation waits for the same row.
        AppointmentRow appointment = lockAppointment(appointmentId);
        if (!snapshot.slotId().equals(appointment.slotId())) {
            throw conflict("CONCURRENT_UPDATE","Los datos cambiaron. Actualiza la cita e inténtalo de nuevo.");
        }
        lockSlot(appointment.slotId());
        Instant now = clock.instant();
        if (request.expectedVersion() != null && request.expectedVersion() < Long.MAX_VALUE
                && appointment.version() == request.expectedVersion()+1 && appointment.status() == target
                && matchesStatusTransition(appointmentId, appointment.version(), target, actorId, reason)) {
            return get(actorId,appointmentId);
        }
        requireVersion(appointment.version(), request.expectedVersion());
        if (appointment.status() != AppointmentStatus.REQUESTED) {
            throw conflict("APPOINTMENT_TRANSITION_NOT_ALLOWED", "La solicitud ya cambió o esta transición no está permitida.");
        }
        if (target == AppointmentStatus.CONFIRMED) {
            if (!now.isBefore(appointment.cutoffAt()) || !appointment.startsAt().isAfter(now)) {
                throw conflict("APPOINTMENT_CONFIRMATION_CUTOFF_PASSED", "La solicitud ya llegó a su hora de corte o el turno comenzó.");
            }
            boolean eligible = jdbc.queryForObject("select (u.status='ACTIVE' and u.role='OWNER' and p.active and v.status='ACTIVE' and s.status='PUBLISHED') " +
                    "from appointments a join users u on u.id=a.owner_id join pets p on p.id=a.pet_id " +
                    "join veterinarian_availability_slots s on s.id=a.current_slot_id join users v on v.id=s.veterinarian_id where a.id=:id",
                    Map.of("id", appointmentId), Boolean.class);
            if (!eligible) { throw conflict("APPOINTMENT_PARTICIPANT_INACTIVE", "El propietario, la mascota o el veterinario ya no están activos."); }
        }
        jdbc.update("update appointments set status=:status,version=version+1,updated_at=:now where id=:id",
                Map.of("status", target.name(), "now", dbTime(now), "id", appointmentId));
        insertEvent(appointmentId, appointment.version()+1, target.name(), appointment.status().name(), target.name(),
                appointment.assignmentStatus().name(), appointment.assignmentStatus().name(), appointment.slotId(), appointment.slotId(),
                actorId, "HUMAN", now, reason, null, null, null);
        return get(actorId, appointmentId);
    }

    @Transactional
    public AppointmentResponseDTO reassign(UUID actorId, UUID appointmentId, ReassignAppointmentRequestDTO request) {
        Account actor = lockAccount(actorId);
        requireRole(actor, UserRole.ADMINISTRATOR);
        String reason = requiredReason(request.reason());
        AppointmentRow snapshot = appointmentSnapshot(appointmentId);
        Account owner = lockAccount(snapshot.ownerId());
        Pet pet = lockPet(snapshot.petId());
        Slot initialDestination = readSlot(request.availabilitySlotId());
        Slot initialCurrent = readSlot(snapshot.slotId());
        lockAccountsInOrder(List.of(initialCurrent.veterinarianId(), initialDestination.veterinarianId()));
        AppointmentRow appointment = lockAppointment(appointmentId);
        String operationFingerprint = fingerprint(request.availabilitySlotId(), request.expectedAvailabilitySlotVersion(), request.expectedVersion(), reason,
                request.agreementChannel(), request.agreementContactedAt(), request.agreementNote());
        List<String> previousOperations = jdbc.query("select request_fingerprint from appointment_operation_idempotency where appointment_id=:appointment and operation_type='REASSIGNMENT' and client_request_id=:requestId",
                Map.of("appointment",appointmentId,"requestId",request.clientRequestId()),(rs,row)->rs.getString(1));
        if (!previousOperations.isEmpty()) {
            if (!MessageDigest.isEqual(previousOperations.getFirst().getBytes(StandardCharsets.US_ASCII),operationFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw conflict("APPOINTMENT_IDEMPOTENCY_CONFLICT","La clave de operación ya se usó con otros datos.");
            }
            return get(actorId,appointmentId);
        }
        if (!snapshot.slotId().equals(appointment.slotId())) {
            throw conflict("CONCURRENT_UPDATE","La asignación cambió. Actualiza la cita e inténtalo de nuevo.");
        }
        requireVersion(appointment.version(), request.expectedVersion());
        if (appointment.status() != AppointmentStatus.REQUESTED && appointment.status() != AppointmentStatus.CONFIRMED) {
            throw conflict("APPOINTMENT_TRANSITION_NOT_ALLOWED", "No se puede reasignar una cita finalizada.");
        }
        Instant now = clock.instant();
        if (!appointment.endsAt().isAfter(now)) {
            throw conflict("APPOINTMENT_ALREADY_ENDED", "La cita ya terminó y no puede reasignarse.");
        }
        Account originalVeterinarian = account(appointment.veterinarianId());
        if (appointment.assignmentStatus() != AppointmentAssignmentStatus.NEEDS_REASSIGNMENT
                || originalVeterinarian.active() || originalVeterinarian.role() != UserRole.VETERINARIAN) {
            throw conflict("APPOINTMENT_REASSIGNMENT_NOT_REQUIRED", "La cita no requiere reemplazar a un veterinario no disponible.");
        }
        if (!owner.active() || owner.role() != UserRole.OWNER || !pet.active()
                || !pet.ownerId().equals(appointment.ownerId())) {
            throw conflict("APPOINTMENT_PARTICIPANT_INACTIVE", "El propietario o la mascota ya no están activos para reasignar la cita.");
        }
        if (appointment.status() == AppointmentStatus.REQUESTED
                && (!now.isBefore(appointment.cutoffAt()) || appointment.startsAt().atZone(zone).toLocalDate().isBefore(LocalDate.now(clock.withZone(zone))))) {
            throw conflict("APPOINTMENT_CONFIRMATION_CUTOFF_PASSED", "La solicitud ya llegó a su hora de corte.");
        }
        Slot current = readSlot(appointment.slotId());
        Slot destination = readSlot(request.availabilitySlotId());
        if (!initialCurrent.veterinarianId().equals(current.veterinarianId())
                || !initialDestination.veterinarianId().equals(destination.veterinarianId())) {
            throw conflict("CONCURRENT_UPDATE","La agenda cambió. Actualiza la cita e inténtalo de nuevo.");
        }
        List<UUID> slotIds = List.of(current.id(), destination.id()).stream().distinct().sorted().toList();
        for (UUID slotId : slotIds) { lockSlot(slotId); }
        current = readSlot(appointment.slotId());
        destination = readSlot(request.availabilitySlotId());
        if (destination.version()!=request.expectedAvailabilitySlotVersion()) {
            throw conflict("CONCURRENT_UPDATE","El turno de destino cambió. Actualiza la agenda e inténtalo de nuevo.");
        }
        requireActiveVeterinarian(destination.veterinarianId());
        if (destination.id().equals(current.id()) || !"PUBLISHED".equals(destination.status()) || !destination.startsAt().isAfter(now)) {
            throw conflict("APPOINTMENT_DESTINATION_NOT_AVAILABLE", "El turno de destino ya no está disponible.");
        }
        Boolean occupied = jdbc.queryForObject("select exists(select 1 from appointments where current_slot_id=:slot and id<>:id and status in ('REQUESTED','CONFIRMED'))",
                Map.of("slot",destination.id(),"id",appointmentId),Boolean.class);
        if (occupied) { throw conflict("APPOINTMENT_SLOT_OCCUPIED","Otra cita ya ocupa el turno de destino."); }
        Boolean petOverlap = jdbc.queryForObject("select exists(select 1 from appointments where pet_id=:pet and starts_at=:start and id<>:id and status in ('REQUESTED','CONFIRMED'))",
                Map.of("pet",appointment.petId(),"start",dbTime(destination.startsAt()),"id",appointmentId),Boolean.class);
        if (petOverlap) { throw conflict("APPOINTMENT_PET_ALREADY_SCHEDULED","La mascota ya tiene otra cita en ese horario."); }
        boolean timeChanged = !destination.startsAt().equals(appointment.startsAt());
        if (timeChanged) { requireAgreement(request,now); }
        if (appointment.status() == AppointmentStatus.REQUESTED) {
            Instant newCutoff = destination.startsAt().atZone(zone).toLocalDate().atTime(properties.getWorkdayStartTime()).atZone(zone).toInstant();
            if (!now.isBefore(newCutoff)) { throw conflict("APPOINTMENT_CONFIRMATION_CUTOFF_PASSED", "El turno de destino ya llegó a su hora de corte."); }
            if (destination.startsAt().atZone(zone).toLocalDate().isBefore(LocalDate.now(clock.withZone(zone)).plusDays(properties.getMinimumOwnerAdvanceDays()))) {
                throw conflict("APPOINTMENT_OUTSIDE_REQUEST_WINDOW", "El turno de destino no cumple la anticipación mínima.");
            }
            try {
                jdbc.update("update appointments set current_slot_id=:slot,starts_at=:start,ends_at=:end,confirmation_cutoff_at=:cutoff,assignment_status='ASSIGNED',version=version+1,updated_at=:now where id=:id",
                        Map.of("slot",destination.id(),"start",dbTime(destination.startsAt()),"end",dbTime(destination.endsAt()),"cutoff",dbTime(newCutoff),"now",dbTime(now),"id",appointmentId));
            } catch (DataIntegrityViolationException exception) { throw translateOccupancyConflict(exception); }
        } else {
            try {
                jdbc.update("update appointments set current_slot_id=:slot,starts_at=:start,ends_at=:end,assignment_status='ASSIGNED',version=version+1,updated_at=:now where id=:id",
                        Map.of("slot",destination.id(),"start",dbTime(destination.startsAt()),"end",dbTime(destination.endsAt()),"now",dbTime(now),"id",appointmentId));
            } catch (DataIntegrityViolationException exception) { throw translateOccupancyConflict(exception); }
        }
        insertAssignment(appointmentId,destination,actorId,now,reason);
        insertEvent(appointmentId,appointment.version()+1,"REASSIGNED",appointment.status().name(),appointment.status().name(),
                appointment.assignmentStatus().name(),"ASSIGNED",appointment.slotId(),destination.id(),actorId,"HUMAN",now,reason,
                timeChanged ? request.agreementChannel() : null,timeChanged ? request.agreementContactedAt() : null,
                timeChanged ? request.agreementNote().strip() : null);
        jdbc.update("insert into appointment_operation_idempotency(id,appointment_id,operation_type,client_request_id,request_fingerprint,resulting_version,created_at) values(:id,:appointment,'REASSIGNMENT',:requestId,:fingerprint,:version,:now)",
                Map.of("id",UUID.randomUUID(),"appointment",appointmentId,"requestId",request.clientRequestId(),
                        "fingerprint",operationFingerprint,"version",appointment.version()+1,"now",dbTime(now)));
        enqueueOwnerNotice(appointmentId, destination, now);
        return get(actorId,appointmentId);
    }

    @Transactional
    public OwnerEmailResponseDTO revealOwnerEmail(UUID veterinarianId, UUID appointmentId, RevealOwnerEmailRequestDTO request) {
        Account vet = lockAccount(veterinarianId);
        requireRole(vet, UserRole.VETERINARIAN);
        AppointmentRow appointment = lockAppointment(appointmentId);
        Instant now = clock.instant();
        if (appointment.status() != AppointmentStatus.CONFIRMED || !now.isBefore(appointment.endsAt())
                || !appointment.veterinarianId().equals(veterinarianId)) { throw notFound(); }
        jdbc.update("insert into appointment_email_access_audits(id,appointment_id,veterinarian_id,accessed_at,reason) values(:id,:appointment,:vet,:now,:reason)",
                Map.of("id",UUID.randomUUID(),"appointment",appointmentId,"vet",veterinarianId,"now",dbTime(now),"reason",request.reason().strip()));
        insertEvent(appointmentId,appointment.version()+1,"EMAIL_ACCESSED",null,null,null,null,null,null,
                veterinarianId,"HUMAN",now,null,null,null,null);
        jdbc.update("update appointments set version=version+1,updated_at=:now where id=:id", Map.of("now",dbTime(now),"id",appointmentId));
        String email = jdbc.queryForObject("select u.email from appointments a join users u on u.id=a.owner_id where a.id=:id",
                Map.of("id",appointmentId),String.class);
        return new OwnerEmailResponseDTO(appointmentId,email);
    }

    @Transactional
    public int expirePendingAppointments() {
        int expired = 0;
        Instant now = clock.instant();
        while (true) {
            List<AppointmentRow> batch = jdbc.query("select a.id,a.owner_id,a.pet_id,a.current_slot_id,a.status,a.assignment_status,a.starts_at,a.ends_at,a.confirmation_cutoff_at,a.version,s.veterinarian_id " +
                    "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                    "where a.status='REQUESTED' and a.confirmation_cutoff_at<=:now order by a.confirmation_cutoff_at,a.id limit 100 for update of a skip locked",
                    Map.of("now",dbTime(now)),APPOINTMENT_LOCK_ROW);
            if (batch.isEmpty()) { return expired; }
            for (AppointmentRow appointment : batch) {
                jdbc.update("update appointments set status='CANCELLED',version=version+1,updated_at=:now where id=:id and status='REQUESTED'",
                        Map.of("now",dbTime(now),"id",appointment.id()));
                insertEvent(appointment.id(),appointment.version()+1,"DAILY_CUTOFF","REQUESTED","CANCELLED",
                        appointment.assignmentStatus().name(),appointment.assignmentStatus().name(),appointment.slotId(),appointment.slotId(),
                        null,"SYSTEM",now,"DAILY_CONFIRMATION_CUTOFF",null,null,null);
                expired++;
            }
        }
    }

    @Transactional
    public void markVeterinarianDisabled(UUID veterinarianId, UUID actorId) {
        Instant now = clock.instant();
        List<AppointmentRow> affected = jdbc.query("select a.id,a.owner_id,a.pet_id,a.current_slot_id,a.status,a.assignment_status,a.starts_at,a.ends_at,a.confirmation_cutoff_at,a.version,s.veterinarian_id " +
                "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where s.veterinarian_id=:vet and a.status in ('REQUESTED','CONFIRMED') and a.starts_at>:now order by a.id for update of a",
                Map.of("vet",veterinarianId,"now",dbTime(now)),APPOINTMENT_LOCK_ROW);
        UUID eventKey = UUID.randomUUID();
        for (AppointmentRow appointment : affected) {
            if (appointment.assignmentStatus() == AppointmentAssignmentStatus.NEEDS_REASSIGNMENT) { continue; }
            jdbc.update("update appointments set assignment_status='NEEDS_REASSIGNMENT',version=version+1,updated_at=:now where id=:id",
                    Map.of("now",dbTime(now),"id",appointment.id()));
            insertEvent(appointment.id(),appointment.version()+1,"VETERINARIAN_DISABLED",appointment.status().name(),appointment.status().name(),
                    "ASSIGNED","NEEDS_REASSIGNMENT",appointment.slotId(),appointment.slotId(),actorId,"HUMAN",now,
                    "El veterinario asignado fue deshabilitado.",null,null,null);
        }
        if (!affected.isEmpty()) {
            String veterinarianName=jdbc.queryForObject("select full_name from veterinarian_profiles where user_id=:id",Map.of("id",veterinarianId),String.class);
            enqueueAdministratorNotice(eventKey, affected.getFirst(), veterinarianId, veterinarianName, now, affected.size());
        }
    }

    @Transactional
    public void markVeterinarianReactivated(UUID veterinarianId, UUID actorId) {
        Instant now=clock.instant();
        List<AppointmentRow> affected=jdbc.query("select a.id,a.owner_id,a.pet_id,a.current_slot_id,a.status,a.assignment_status,a.starts_at,a.ends_at,a.confirmation_cutoff_at,a.version,s.veterinarian_id " +
                "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where s.veterinarian_id=:vet " +
                "and a.assignment_status='NEEDS_REASSIGNMENT' and a.status in ('REQUESTED','CONFIRMED') and a.starts_at>:now and s.status='PUBLISHED' " +
                "order by a.id for update of a",Map.of("vet",veterinarianId,"now",dbTime(now)),APPOINTMENT_LOCK_ROW);
        for(AppointmentRow appointment:affected) {
            jdbc.update("update appointments set assignment_status='ASSIGNED',version=version+1,updated_at=:now where id=:id",
                    Map.of("now",dbTime(now),"id",appointment.id()));
            insertEvent(appointment.id(),appointment.version()+1,"VETERINARIAN_RESTORED",appointment.status().name(),appointment.status().name(),
                    "NEEDS_REASSIGNMENT","ASSIGNED",appointment.slotId(),appointment.slotId(),actorId,"HUMAN",now,
                    "El veterinario asignado volvió a estar activo.",null,null,null);
        }
    }

    private void enqueueOwnerNotice(UUID appointmentId, Slot destination, Instant now) {
        Map<String,Object> data = jdbc.queryForMap("select p.name pet_name,vp.full_name veterinarian_name,a.starts_at,a.version appointment_version from appointments a join pets p on p.id=a.pet_id join veterinarian_profiles vp on vp.user_id=:vet where a.id=:id",
                Map.of("id",appointmentId,"vet",destination.veterinarianId()));
        String payload = json(Map.of("petName",data.get("pet_name"),"veterinarianName",data.get("veterinarian_name"),
                "startsAt",destination.startsAt().toString(),"timeZone","America/Bogota",
                "availabilitySlotId",destination.id().toString(),"assignmentVersion",data.get("appointment_version")));
        UUID eventKey = UUID.randomUUID();
        Map<String,Object> owner = jdbc.queryForMap("select a.owner_id,u.email from appointments a join users u on u.id=a.owner_id where a.id=:id",Map.of("id",appointmentId));
        jdbc.update("insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload,status,attempt_count,next_attempt_at,created_at) " +
                "values(:id,:appointment,:event,:recipient,:email,'OWNER_REASSIGNED',cast(:payload as jsonb),'PENDING',0,:now,:now)",
                Map.of("id",UUID.randomUUID(),"appointment",appointmentId,"event",eventKey,"recipient",owner.get("owner_id"),
                        "email",owner.get("email"),"payload",payload,"now",dbTime(now)));
        eventPublisher.publishEvent(new AppointmentNotificationsQueuedEvent(appointmentId));
    }

    private void enqueueAdministratorNotice(UUID eventKey, AppointmentRow example, UUID veterinarianId, String veterinarianName, Instant now, int count) {
        List<Map<String,Object>> recipients = jdbc.queryForList("select u.id,u.email from users u join administrator_profiles ap on ap.user_id=u.id where u.role='ADMINISTRATOR' and u.status='ACTIVE' order by u.id",Map.of());
        if (recipients.isEmpty()) {
            jdbc.update("insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload,status,attempt_count,next_attempt_at,last_error_code,created_at) " +
                    "values(:id,:appointment,:event,null,null,'ADMIN_REASSIGNMENT',cast(:payload as jsonb),'FAILED',0,:now,'NO_ACTIVE_ADMINISTRATORS',:now)",
                    Map.of("id",UUID.randomUUID(),"appointment",example.id(),"event",eventKey,
                            "payload",json(Map.of("veterinarianId",veterinarianId,"veterinarianName",veterinarianName,"appointmentCount",count)),"now",dbTime(now)));
            eventPublisher.publishEvent(new AppointmentNotificationsQueuedEvent(example.id()));
            return;
        }
        String payload = json(Map.of("veterinarianId",veterinarianId,"veterinarianName",veterinarianName,"appointmentCount",count));
        for (Map<String,Object> recipient : recipients) {
            jdbc.update("insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload,status,attempt_count,next_attempt_at,created_at) " +
                            "values(:id,:appointment,:event,:recipient,:email,'ADMIN_REASSIGNMENT',cast(:payload as jsonb),'PENDING',0,:now,:now)",
                    Map.of("id",UUID.randomUUID(),"appointment",example.id(),"event",eventKey,"recipient",recipient.get("id"),
                            "email",recipient.get("email"),"payload",payload,"now",dbTime(now)));
        }
        eventPublisher.publishEvent(new AppointmentNotificationsQueuedEvent(example.id()));
    }

    private AppointmentResponseDTO replay(UUID owner, UUID requestId, String fingerprint) {
        List<UUID> ids = jdbc.query("select id from appointments where owner_id=:owner and client_request_id=:requestId",
                Map.of("owner",owner,"requestId",requestId),(rs,row)->rs.getObject(1,UUID.class));
        if (ids.isEmpty()) { return null; }
        String existing = jdbc.queryForObject("select request_fingerprint from appointments where id=:id",Map.of("id",ids.getFirst()),String.class);
        if (!MessageDigest.isEqual(existing.getBytes(StandardCharsets.US_ASCII),fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("APPOINTMENT_IDEMPOTENCY_CONFLICT","La clave de solicitud ya se usó con otros datos.");
        }
        return get(owner,ids.getFirst());
    }

    private void insertAssignment(UUID appointmentId, Slot slot, UUID actor, Instant now, String reason) {
        jdbc.update("insert into appointment_assignments(id,appointment_id,slot_id,veterinarian_id,starts_at,ends_at,assigned_at,actor_id,actor_type,reason) " +
                        "values(:id,:appointment,:slot,:vet,:start,:end,:now,:actor,'HUMAN',:reason)",
                Map.of("id",UUID.randomUUID(),"appointment",appointmentId,"slot",slot.id(),"vet",slot.veterinarianId(),
                        "start",dbTime(slot.startsAt()),"end",dbTime(slot.endsAt()),"now",dbTime(now),"actor",actor,"reason",reason));
    }

    private void insertEvent(UUID appointmentId,long version,String type,String previousStatus,String newStatus,
            String previousAssignment,String newAssignment,UUID previousSlot,UUID newSlot,UUID actor,String actorType,
            Instant now,String reason,String agreementChannel,Instant agreementAt,String agreementNote) {
        jdbc.update("insert into appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status,previous_assignment_status,new_assignment_status,previous_slot_id,new_slot_id,actor_id,actor_type,occurred_at,reason,agreement_channel,agreement_contacted_at,agreement_note) "+
                        "values(:id,:appointment,:version,:type,:previousStatus,:newStatus,:previousAssignment,:newAssignment,:previousSlot,:newSlot,:actor,:actorType,:now,:reason,:channel,:contacted,:note)",
                new MapSqlParameterSource().addValue("id",UUID.randomUUID()).addValue("appointment",appointmentId).addValue("version",version)
                        .addValue("type",type).addValue("previousStatus",previousStatus).addValue("newStatus",newStatus)
                        .addValue("previousAssignment",previousAssignment).addValue("newAssignment",newAssignment)
                        .addValue("previousSlot",previousSlot).addValue("newSlot",newSlot).addValue("actor",actor).addValue("actorType",actorType)
                        .addValue("now",dbTime(now)).addValue("reason",reason).addValue("channel",agreementChannel)
                        .addValue("contacted",agreementAt == null ? null : dbTime(agreementAt)).addValue("note",agreementNote));
    }

    private Account account(UUID id) {
        return jdbc.query("select id,role,status from users where id=:id",Map.of("id",id),
                (rs,row)->new Account(rs.getObject("id",UUID.class),UserRole.valueOf(rs.getString("role")),"ACTIVE".equals(rs.getString("status"))))
                .stream().findFirst().orElseThrow(AppointmentService::accessDenied);
    }
    private Account lockAccount(UUID id) {
        return jdbc.query("select id,role,status from users where id=:id for update",Map.of("id",id),
                (rs,row)->new Account(rs.getObject("id",UUID.class),UserRole.valueOf(rs.getString("role")),"ACTIVE".equals(rs.getString("status"))))
                .stream().findFirst().orElseThrow(AppointmentService::accessDenied);
    }
    private Pet lockPet(UUID id) {
        return jdbc.query("select id,owner_id,active from pets where id=:id for update",Map.of("id",id),
                (rs,row)->new Pet(rs.getObject("id",UUID.class),rs.getObject("owner_id",UUID.class),rs.getBoolean("active")))
                .stream().findFirst().orElseThrow(AppointmentService::petUnavailable);
    }
    private Slot readSlot(UUID id) {
        return jdbc.query("select id,veterinarian_id,starts_at,ends_at,status,version from veterinarian_availability_slots where id=:id",Map.of("id",id),SLOT_ROW)
                .stream().findFirst().orElseThrow(AppointmentService::slotUnavailable);
    }
    private Slot lockSlot(UUID id) {
        return jdbc.query("select id,veterinarian_id,starts_at,ends_at,status,version from veterinarian_availability_slots where id=:id for update",Map.of("id",id),SLOT_ROW)
                .stream().findFirst().orElseThrow(AppointmentService::slotUnavailable);
    }
    private void lockAccountsInOrder(List<UUID> ids) { ids.stream().distinct().sorted().forEach(this::lockAccount); }
    private void requireActiveVeterinarian(UUID id) {
        Boolean active = jdbc.query("select (u.status='ACTIVE' and u.role='VETERINARIAN' and vp.user_id is not null) from users u left join veterinarian_profiles vp on vp.user_id=u.id where u.id=:id",
                Map.of("id",id),(rs,row)->rs.getBoolean(1)).stream().findFirst().orElse(false);
        if (!active) { throw conflict("APPOINTMENT_VETERINARIAN_INACTIVE","El veterinario ya no está activo."); }
    }
    private AppointmentRow lockAppointment(UUID id) {
        return jdbc.query("select a.id,a.owner_id,a.pet_id,a.current_slot_id,a.status,a.assignment_status,a.starts_at,a.ends_at,a.confirmation_cutoff_at,a.version,s.veterinarian_id " +
                "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where a.id=:id for update of a",
                Map.of("id",id),APPOINTMENT_LOCK_ROW).stream().findFirst().orElseThrow(AppointmentService::notFound);
    }
    private AppointmentRow appointmentSnapshot(UUID id) {
        return jdbc.query("select a.id,a.owner_id,a.pet_id,a.current_slot_id,a.status,a.assignment_status,a.starts_at,a.ends_at,a.confirmation_cutoff_at,a.version,s.veterinarian_id " +
                "from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where a.id=:id",Map.of("id",id),APPOINTMENT_LOCK_ROW)
                .stream().findFirst().orElseThrow(AppointmentService::notFound);
    }
    private boolean canView(Account actor,UUID id) {
        String predicate = accessPredicate(actor.role());
        return !jdbc.query("select a.id from appointments a join veterinarian_availability_slots s on s.id=a.current_slot_id where a.id=:id and "+predicate,
                Map.of("id",id,"actor",actor.id()),(rs,row)->rs.getObject(1,UUID.class)).isEmpty();
    }
    private String accessPredicate(UserRole role) {
        return switch(role) {
            case OWNER -> "a.owner_id=:actor";
            case VETERINARIAN -> "s.veterinarian_id=:actor";
            case ADMINISTRATOR -> "true";
            case SUPER_ADMIN -> throw accessDenied();
        };
    }
    private void validatePageRange(LocalDate from,LocalDate to,int page,int size) {
        validatePage(page,size);
        if (from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>=31) { throw invalid("El rango debe incluir entre 1 y 31 fechas."); }
    }
    private void validatePage(int page,int size) {
        if (page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE) { throw invalid("La paginación solicitada no es válida."); }
    }
    private void requireAgreement(ReassignAppointmentRequestDTO request,Instant now) {
        if (request.agreementChannel()==null||request.agreementContactedAt()==null||request.agreementNote()==null||request.agreementNote().isBlank()) {
            throw invalid("Registra el canal, la fecha y la constancia del acuerdo con el propietario para cambiar fecha u hora.");
        }
        if (request.agreementContactedAt().isAfter(now)) { throw invalid("La fecha del contacto debe ser anterior o igual al momento del cambio."); }
        if (!List.of("PHONE","WHATSAPP","OTHER").contains(request.agreementChannel())) { throw invalid("El canal de contacto no es válido."); }
    }
    private void requireRole(Account account,UserRole role) {
        if (!account.active()||account.role()!=role) { throw accessDenied(); }
    }
    private boolean matchesStatusTransition(UUID appointmentId, long version, AppointmentStatus target,
            UUID actorId, String reason) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from appointment_events " +
                        "where appointment_id=:appointment and appointment_version=:version and event_type=:target " +
                        "and previous_status='REQUESTED' and new_status=:target and actor_type='HUMAN' " +
                        "and actor_id=:actor and reason is not distinct from :reason)",
                new MapSqlParameterSource().addValue("appointment", appointmentId).addValue("version", version)
                        .addValue("target", target.name()).addValue("actor", actorId).addValue("reason", reason), Boolean.class));
    }
    private String requiredReason(String value) {
        String normalized = optionalReason(value);
        if (normalized == null) { throw invalid("Indica un motivo que no esté vacío."); }
        return normalized;
    }
    private String optionalReason(String value) {
        if (value == null || value.isBlank()) { return null; }
        String normalized = value.strip();
        if (normalized.length() > 1000) { throw invalid("El motivo no puede superar 1000 caracteres."); }
        return normalized;
    }
    private void requireVersion(long actual,Long expected) {
        if (expected==null||actual!=expected) { throw conflict("CONCURRENT_UPDATE","Los datos cambiaron. Actualiza la cita e inténtalo de nuevo."); }
    }
    private static String fingerprint(UUID pet,UUID slot,Long version,String reason) {
        try {
            String canonical=pet+"\n"+slot+"\n"+version+"\n"+reason.strip();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String fingerprint(UUID slot,Long slotVersion,Long version,String reason,String channel,Instant contactedAt,String note) {
        try {
            String canonical=slot+"\n"+slotVersion+"\n"+version+"\n"+reason.strip()+"\n"+channel+"\n"+contactedAt+"\n"+(note==null?"":note.strip());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Could not serialize appointment notification.",exception); }
    }
    private static java.sql.Timestamp dbTime(Instant instant) { return java.sql.Timestamp.from(instant); }
    private static boolean constraint(Throwable error,String expected) {
        for(int depth=0;error!=null&&depth<12;depth++,error=error.getCause()) {
            if(error instanceof org.hibernate.exception.ConstraintViolationException e&&expected.equals(e.getConstraintName())) return true;
            if(error instanceof PSQLException e&&e.getServerErrorMessage()!=null&&expected.equals(e.getServerErrorMessage().getConstraint())) return true;
        }
        return false;
    }
    private static AppointmentOperationException translateOccupancyConflict(DataIntegrityViolationException exception) {
        if (constraint(exception,"uk_appointment_active_slot")) {
            return conflict("APPOINTMENT_SLOT_OCCUPIED","Otra cita ya ocupa el turno de destino.");
        }
        if (constraint(exception,"uk_appointment_active_pet_interval")) {
            return conflict("APPOINTMENT_PET_ALREADY_SCHEDULED","La mascota ya tiene otra cita en ese horario.");
        }
        throw exception;
    }
    private static final RowMapper<Slot> SLOT_ROW=(rs,row)->new Slot(rs.getObject("id",UUID.class),rs.getObject("veterinarian_id",UUID.class),rs.getTimestamp("starts_at").toInstant(),rs.getTimestamp("ends_at").toInstant(),rs.getString("status"),rs.getLong("version"));
    private static final RowMapper<AppointmentRow> APPOINTMENT_LOCK_ROW=(rs,row)->new AppointmentRow(rs.getObject("id",UUID.class),rs.getObject("owner_id",UUID.class),rs.getObject("pet_id",UUID.class),rs.getObject("current_slot_id",UUID.class),AppointmentStatus.valueOf(rs.getString("status")),AppointmentAssignmentStatus.valueOf(rs.getString("assignment_status")),rs.getTimestamp("starts_at").toInstant(),rs.getTimestamp("ends_at").toInstant(),rs.getTimestamp("confirmation_cutoff_at").toInstant(),rs.getLong("version"),rs.getObject("veterinarian_id",UUID.class));
    private record Account(UUID id,UserRole role,boolean active) { }
    private record Pet(UUID id,UUID ownerId,boolean active) { }
    private record Slot(UUID id,UUID veterinarianId,Instant startsAt,Instant endsAt,String status,long version) { }
    private record AppointmentRow(UUID id,UUID ownerId,UUID petId,UUID slotId,AppointmentStatus status,AppointmentAssignmentStatus assignmentStatus,Instant startsAt,Instant endsAt,Instant cutoffAt,long version,UUID veterinarianId) { }
    private static AppointmentOperationException invalid(String detail) { return new AppointmentOperationException(HttpStatus.BAD_REQUEST,"INVALID_APPOINTMENT_REQUEST",detail); }
    private static AppointmentOperationException conflict(String code,String detail) { return new AppointmentOperationException(HttpStatus.CONFLICT,code,detail); }
    private static AppointmentOperationException accessDenied() { return new AppointmentOperationException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","La cuenta no tiene permiso para esta operación."); }
    private static AppointmentOperationException notFound() { return new AppointmentOperationException(HttpStatus.NOT_FOUND,"APPOINTMENT_NOT_FOUND","No se encontró la cita solicitada."); }
    private static AppointmentOperationException petUnavailable() { return new AppointmentOperationException(HttpStatus.NOT_FOUND,"PET_RESOURCE_NOT_FOUND","No se encontró la mascota disponible."); }
    private static AppointmentOperationException slotUnavailable() { return new AppointmentOperationException(HttpStatus.CONFLICT,"APPOINTMENT_SLOT_NOT_AVAILABLE","El turno ya no está disponible. Actualiza la agenda e inténtalo de nuevo."); }
}
