package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.exception.AvailabilityOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilityEventRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VeterinarianAvailabilityService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    public static final String TIME_ZONE = "America/Bogota";
    private static final int SLOT_MINUTES = 30;
    private static final int MAX_BATCH_DAYS = 31;
    private static final int MAX_BATCH_SLOTS = 1000;

    private final VeterinarianAvailabilitySlotRepository slots;
    private final VeterinarianAvailabilityEventRepository events;
    private final UserRepository users;
    private final VeterinarianProfileRepository veterinarians;
    private final Clock clock;
    private final AppointmentProperties appointmentProperties;
    private final JdbcTemplate jdbc;

    public VeterinarianAvailabilityService(VeterinarianAvailabilitySlotRepository slots,
            VeterinarianAvailabilityEventRepository events, UserRepository users,
            VeterinarianProfileRepository veterinarians, Clock clock, AppointmentProperties appointmentProperties, JdbcTemplate jdbc) {
        this.slots = slots; this.events = events; this.users = users; this.veterinarians = veterinarians;
        this.clock = clock; this.appointmentProperties = appointmentProperties; this.jdbc = jdbc;
    }

    @Transactional
    public VeterinarianAvailabilitySlotResponseDTO create(UUID actorId, UUID veterinarianId,
            CreateVeterinarianAvailabilitySlotRequestDTO request) {
        administrator(actorId, true);
        User veterinarian = lockVeterinarian(veterinarianId);
        Instant now = clock.instant();
        Instant start = requestedInstant(request.startsAt());
        requireFuture(start, now);
        requireActive(veterinarian);
        VeterinarianAvailabilitySlot slot = new VeterinarianAvailabilitySlot(veterinarianId,
                start, start.plus(SLOT_MINUTES, ChronoUnit.MINUTES), actorId, now);
        saveNewSlot(slot);
        events.saveAndFlush(new VeterinarianAvailabilityEvent(slot, actorId, now,
                AvailabilityEventType.CREATED, null, null, null, null));
        return response(slot);
    }

    @Transactional
    public CreateVeterinarianAvailabilitySlotBatchResponseDTO createBatch(UUID actorId, UUID veterinarianId,
            CreateVeterinarianAvailabilitySlotBatchRequestDTO request) {
        administrator(actorId, true);
        User veterinarian = lockVeterinarian(veterinarianId);
        Instant now = clock.instant();
        requireActive(veterinarian);
        List<Instant> starts = batchStarts(request);
        if (starts.isEmpty() || starts.size() > MAX_BATCH_SLOTS) { throw invalid("La generación debe producir entre 1 y 1000 turnos."); }
        if (starts.stream().anyMatch(start -> !start.isAfter(now))) { throw invalid("Todos los turnos generados deben ser futuros."); }
        List<VeterinarianAvailabilitySlot> created = new ArrayList<>(starts.size());
        for (Instant start : starts) {
            VeterinarianAvailabilitySlot slot = new VeterinarianAvailabilitySlot(veterinarianId,
                    start, start.plus(SLOT_MINUTES, ChronoUnit.MINUTES), actorId, now);
            slots.save(slot);
            created.add(slot);
        }
        try {
            slots.flush();
            for (VeterinarianAvailabilitySlot slot : created) {
                events.save(new VeterinarianAvailabilityEvent(slot, actorId, now,
                        AvailabilityEventType.CREATED, null, null, null, null));
            }
            events.flush();
        } catch (DataIntegrityViolationException exception) {
            if (constraintMatches(exception, "uk_veterinarian_availability_start")) { throw conflict(); }
            throw exception;
        }
        return new CreateVeterinarianAvailabilitySlotBatchResponseDTO(created.size(),
                created.stream().map(this::response).toList(), TIME_ZONE);
    }

    public VeterinarianAvailabilitySlotPageResponseDTO list(UUID actorId, UUID veterinarianId,
            LocalDate from, LocalDate to, AvailabilitySlotStatus status, int page, int size) {
        User actor = users.findById(actorId).orElseThrow(VeterinarianAvailabilityService::accessDenied);
        if (!actor.isActive()) { throw accessDenied(); }
        if (actor.getRole() == UserRole.ADMINISTRATOR) {
            requireVeterinarian(veterinarianId);
        } else if (actor.getRole() == UserRole.VETERINARIAN) {
            if (!actor.getId().equals(veterinarianId)) { throw slotNotFound(); }
            requireVeterinarian(veterinarianId);
        } else {
            throw accessDenied();
        }
        DateRange range = dateRange(from, to);
        Pageable pageable = pageable(page, size, Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
        Page<VeterinarianAvailabilitySlot> result = slots.findSchedule(veterinarianId,
                range.from(), range.to(), status, pageable);
        return new VeterinarianAvailabilitySlotPageResponseDTO(result.map(this::response).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public VeterinarianAvailabilitySlotResponseDTO get(UUID actorId, UUID veterinarianId, UUID slotId) {
        requireScheduleReader(actorId, veterinarianId);
        VeterinarianAvailabilitySlot slot = slots.findById(slotId).orElseThrow(VeterinarianAvailabilityService::slotNotFound);
        if (!slot.getVeterinarianId().equals(veterinarianId)) { throw slotNotFound(); }
        return response(slot);
    }

    @Transactional
    public VeterinarianAvailabilitySlotResponseDTO reschedule(UUID actorId, UUID veterinarianId, UUID slotId,
            UpdateVeterinarianAvailabilitySlotRequestDTO request) {
        administrator(actorId, true);
        lockVeterinarian(veterinarianId);
        VeterinarianAvailabilitySlot slot = lockSlot(veterinarianId, slotId);
        Instant now = clock.instant();
        requireFuture(slot.getStartsAt(), now);
        requireFuture(requestedInstant(request.startsAt()), now);
        if (slot.getVersion() != request.expectedVersion()) { throw concurrentUpdate(); }
        User veterinarian = users.findById(veterinarianId).orElseThrow(VeterinarianAvailabilityService::staffNotFound);
        requireActive(veterinarian);
        Instant newStart = requestedInstant(request.startsAt());
        if (newStart.equals(slot.getStartsAt())) { return response(slot); }
        if (hasAppointmentHistory(slotId)) { throw slotReferencedByAppointment(); }
        Instant oldStart = slot.getStartsAt();
        Instant oldEnd = slot.getEndsAt();
        AvailabilitySlotStatus oldStatus = slot.getStatus();
        slot.reschedule(newStart, newStart.plus(SLOT_MINUTES, ChronoUnit.MINUTES), actorId, now);
        flushSlot(slot);
        saveEvent(slot, actorId, now, AvailabilityEventType.RESCHEDULED, oldStart, oldEnd, oldStatus, request.reason());
        return response(slot);
    }

    @Transactional
    public VeterinarianAvailabilitySlotResponseDTO changeStatus(UUID actorId, UUID veterinarianId, UUID slotId,
            UpdateVeterinarianAvailabilitySlotStatusRequestDTO request) {
        administrator(actorId, true);
        User veterinarian = lockVeterinarian(veterinarianId);
        VeterinarianAvailabilitySlot slot = lockSlot(veterinarianId, slotId);
        Instant now = clock.instant();
        requireFuture(slot.getStartsAt(), now);
        if (slot.getVersion() != request.expectedVersion()) { throw concurrentUpdate(); }
        if (request.status() == AvailabilitySlotStatus.PUBLISHED) { requireActive(veterinarian); }
        if (slot.getStatus() == request.status()) { return response(slot); }
        if (request.status() == AvailabilitySlotStatus.BLOCKED && hasActiveAppointment(slotId)) { throw slotOccupiedByAppointment(); }
        Instant oldStart = slot.getStartsAt();
        Instant oldEnd = slot.getEndsAt();
        AvailabilitySlotStatus oldStatus = slot.getStatus();
        slot.changeStatus(request.status(), actorId, now);
        flushSlot(slot);
        AvailabilityEventType eventType = request.status() == AvailabilitySlotStatus.BLOCKED
                ? AvailabilityEventType.BLOCKED : AvailabilityEventType.PUBLISHED;
        saveEvent(slot, actorId, now, eventType, oldStart, oldEnd, oldStatus, request.reason());
        return response(slot);
    }

    public VeterinarianAvailabilityEventPageResponseDTO events(UUID actorId, UUID veterinarianId, UUID slotId,
            int page, int size) {
        administrator(actorId, false);
        VeterinarianAvailabilitySlot slot = slots.findById(slotId).orElseThrow(VeterinarianAvailabilityService::slotNotFound);
        if (!slot.getVeterinarianId().equals(veterinarianId)) { throw slotNotFound(); }
        Page<VeterinarianAvailabilityEvent> result = events.findBySlotId(slotId,
                pageable(page, size, Sort.by(Sort.Order.asc("slotVersion"))));
        return new VeterinarianAvailabilityEventPageResponseDTO(result.map(this::eventResponse).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public AvailableVeterinarianSlotPageResponseDTO available(UUID ownerId, LocalDate from, LocalDate to,
            UUID veterinarianId, int page, int size) {
        User owner = users.findById(ownerId).orElseThrow(VeterinarianAvailabilityService::accessDenied);
        if (!owner.isActive() || owner.getRole() != UserRole.OWNER) { throw accessDenied(); }
        DateRange range = dateRange(from, to);
        Instant now = clock.instant();
        LocalDate earliestDate = LocalDate.now(clock.withZone(BUSINESS_ZONE))
                .plusDays(appointmentProperties.getMinimumOwnerAdvanceDays());
        Instant earliest = earliestDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant latest = now.plus(appointmentProperties.getMaximumOwnerHorizonDays(), ChronoUnit.DAYS);
        Page<AvailableVeterinarianSlotResponseDTO> result = slots.findAvailable(range.from(), range.to(),
                earliest, latest, veterinarianId, pageable(page, size,
                        Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id"))));
        return new AvailableVeterinarianSlotPageResponseDTO(result.getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private List<Instant> batchStarts(CreateVeterinarianAvailabilitySlotBatchRequestDTO request) {
        if (request.startDate() == null || request.endDate() == null || request.daysOfWeek() == null
                || request.daysOfWeek().isEmpty() || request.dailyStartTime() == null || request.dailyEndTime() == null) {
            throw invalid("Completa las fechas, días y horas de generación.");
        }
        long dayCount = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (dayCount < 1 || dayCount > MAX_BATCH_DAYS) { throw invalid("El rango debe incluir entre 1 y 31 fechas."); }
        Set<Integer> days = new HashSet<>(request.daysOfWeek());
        if (days.size() != request.daysOfWeek().size() || days.stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw invalid("Los días deben ser únicos y usar valores ISO entre 1 y 7.");
        }
        int startMinute = parseStartTime(request.dailyStartTime());
        int endMinute = parseEndTime(request.dailyEndTime());
        if (endMinute <= startMinute || (endMinute - startMinute) % SLOT_MINUTES != 0) {
            throw invalid("La hora final debe ser posterior y formar turnos completos de 30 minutos.");
        }
        List<Instant> result = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek().getValue())) { continue; }
            for (int minute = startMinute; minute < endMinute; minute += SLOT_MINUTES) {
                result.add(date.atStartOfDay(BUSINESS_ZONE).plusMinutes(minute).toInstant());
                if (result.size() > MAX_BATCH_SLOTS) { throw invalid("La generación supera el máximo de 1000 turnos."); }
            }
        }
        return result;
    }

    private int parseStartTime(String value) {
        try {
            if (!value.matches("(?:[01][0-9]|2[0-3]):(?:00|30)")) { throw new IllegalArgumentException(); }
            LocalTime time = LocalTime.parse(value);
            return time.getHour() * 60 + time.getMinute();
        } catch (RuntimeException exception) { throw invalid("La hora inicial debe usar el formato HH:mm y coincidir con :00 o :30."); }
    }

    private int parseEndTime(String value) {
        if ("24:00".equals(value)) { return 24 * 60; }
        try {
            if (!value.matches("(?:[01][0-9]|2[0-3]):(?:00|30)")) { throw new IllegalArgumentException(); }
            LocalTime time = LocalTime.parse(value);
            return time.getHour() * 60 + time.getMinute();
        } catch (RuntimeException exception) { throw invalid("La hora final debe usar HH:mm o el valor 24:00."); }
    }

    private Instant requestedInstant(OffsetDateTime dateTime) {
        if (dateTime == null) { throw invalid("Indica la fecha y hora del turno con su zona horaria."); }
        var local = dateTime.atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        if (local.getSecond() != 0 || local.getNano() != 0 || (local.getMinute() != 0 && local.getMinute() != 30)) {
            throw invalid("La hora debe coincidir con :00 o :30 en America/Bogota y no incluir segundos.");
        }
        return dateTime.toInstant();
    }

    private DateRange dateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) { throw invalid("Indica las fechas inicial y final de consulta."); }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > MAX_BATCH_DAYS) { throw invalid("El rango de consulta debe incluir entre 1 y 31 fechas."); }
        try {
            return new DateRange(from.atStartOfDay(BUSINESS_ZONE).toInstant(),
                    to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        } catch (java.time.DateTimeException exception) {
            throw invalid("El rango de fechas no es válido.");
        }
    }

    private User administrator(UUID actorId, boolean lock) {
        User actor = (lock ? users.findByIdForUpdate(actorId) : users.findById(actorId))
                .orElseThrow(VeterinarianAvailabilityService::accessDenied);
        if (!actor.isActive() || actor.getRole() != UserRole.ADMINISTRATOR) { throw accessDenied(); }
        return actor;
    }

    private User lockVeterinarian(UUID veterinarianId) {
        User veterinarian = users.findByIdForUpdate(veterinarianId).orElseThrow(VeterinarianAvailabilityService::staffNotFound);
        if (veterinarian.getRole() != UserRole.VETERINARIAN || !veterinarians.existsById(veterinarianId)) {
            throw staffNotFound();
        }
        return veterinarian;
    }

    private void requireVeterinarian(UUID veterinarianId) {
        if (veterinarianId == null || !veterinarians.existsById(veterinarianId)) { throw staffNotFound(); }
        User veterinarian = users.findById(veterinarianId).orElseThrow(VeterinarianAvailabilityService::staffNotFound);
        if (veterinarian.getRole() != UserRole.VETERINARIAN) { throw staffNotFound(); }
    }

    private void requireScheduleReader(UUID actorId, UUID veterinarianId) {
        User actor = users.findById(actorId).orElseThrow(VeterinarianAvailabilityService::accessDenied);
        if (!actor.isActive()) { throw accessDenied(); }
        if (actor.getRole() == UserRole.ADMINISTRATOR) { requireVeterinarian(veterinarianId); return; }
        if (actor.getRole() == UserRole.VETERINARIAN) {
            if (!actor.getId().equals(veterinarianId)) { throw slotNotFound(); }
            return;
        }
        throw accessDenied();
    }

    private VeterinarianAvailabilitySlot lockSlot(UUID veterinarianId, UUID slotId) {
        VeterinarianAvailabilitySlot slot = slots.findByIdForUpdate(slotId).orElseThrow(VeterinarianAvailabilityService::slotNotFound);
        if (!slot.getVeterinarianId().equals(veterinarianId)) { throw slotNotFound(); }
        return slot;
    }

    private void saveNewSlot(VeterinarianAvailabilitySlot slot) {
        try { slots.saveAndFlush(slot); }
        catch (DataIntegrityViolationException exception) {
            if (constraintMatches(exception, "uk_veterinarian_availability_start")) { throw conflict(); }
            throw exception;
        }
    }

    private void flushSlot(VeterinarianAvailabilitySlot slot) {
        try { slots.flush(); }
        catch (DataIntegrityViolationException exception) {
            if (constraintMatches(exception, "uk_veterinarian_availability_start")) { throw conflict(); }
            throw exception;
        }
    }

    private void saveEvent(VeterinarianAvailabilitySlot slot, UUID actorId, Instant now,
            AvailabilityEventType type, Instant oldStart, Instant oldEnd, AvailabilitySlotStatus oldStatus, String reason) {
        events.saveAndFlush(new VeterinarianAvailabilityEvent(slot, actorId, now, type,
                oldStart, oldEnd, oldStatus, reason.strip()));
    }

    private Pageable pageable(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid("La paginación solicitada no es válida.");
        }
        return PageRequest.of(page, size, sort);
    }

    private void requireFuture(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new AvailabilityOperationException(HttpStatus.CONFLICT, "AVAILABILITY_SLOT_NOT_EDITABLE",
                    "Solo se pueden crear o modificar turnos futuros.");
        }
    }

    private boolean hasActiveAppointment(UUID slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from appointments where current_slot_id=? and status in ('REQUESTED','CONFIRMED'))", Boolean.class, slotId));
    }

    private boolean hasAppointmentHistory(UUID slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from appointment_assignments where slot_id=?)", Boolean.class, slotId));
    }

    private void requireActive(User veterinarian) {
        if (veterinarian.getStatus() != UserStatus.ACTIVE) {
            throw new AvailabilityOperationException(HttpStatus.CONFLICT, "VETERINARIAN_NOT_ACTIVE",
                    "El veterinario debe estar activo para publicar o cambiar turnos.");
        }
    }

    private VeterinarianAvailabilitySlotResponseDTO response(VeterinarianAvailabilitySlot slot) {
        return new VeterinarianAvailabilitySlotResponseDTO(slot.getId(), slot.getVeterinarianId(),
                slot.getStartsAt(), slot.getEndsAt(), slot.getStatus(), slot.getVersion(), TIME_ZONE);
    }

    private VeterinarianAvailabilityEventResponseDTO eventResponse(VeterinarianAvailabilityEvent event) {
        return new VeterinarianAvailabilityEventResponseDTO(event.getId(), event.getSlotId(), event.getSlotVersion(),
                event.getActorId(), event.getOccurredAt(), event.getEventType(), event.getPreviousStartsAt(),
                event.getPreviousEndsAt(), event.getPreviousStatus(), event.getNewStartsAt(), event.getNewEndsAt(),
                event.getNewStatus(), event.getReason());
    }

    private static boolean constraintMatches(Throwable cause, String constraint) {
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException exception && "23505".equals(exception.getSQLState())
                    && constraint.equals(exception.getConstraintName())) { return true; }
            if (cause instanceof PSQLException exception && "23505".equals(exception.getSQLState())
                    && exception.getServerErrorMessage() != null
                    && constraint.equals(exception.getServerErrorMessage().getConstraint())) { return true; }
        }
        return false;
    }

    private record DateRange(Instant from, Instant to) { }

    private static AvailabilityOperationException invalid(String detail) {
        return new AvailabilityOperationException(HttpStatus.BAD_REQUEST, "INVALID_AVAILABILITY_REQUEST", detail);
    }
    private static AvailabilityOperationException conflict() {
        return new AvailabilityOperationException(HttpStatus.CONFLICT, "AVAILABILITY_SLOT_CONFLICT",
                "Ya existe un turno para ese veterinario y esa hora.");
    }
    private static AvailabilityOperationException concurrentUpdate() {
        return new AvailabilityOperationException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Los datos cambiaron mientras realizabas esta acción. Actualiza la información y vuelve a intentarlo.");
    }
    private static AvailabilityOperationException accessDenied() {
        return new AvailabilityOperationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "La cuenta no tiene permiso para esta operación.");
    }
    private static AvailabilityOperationException staffNotFound() {
        return new AvailabilityOperationException(HttpStatus.NOT_FOUND, "STAFF_RESOURCE_NOT_FOUND",
                "No se encontró el profesional solicitado.");
    }
    private static AvailabilityOperationException slotNotFound() {
        return new AvailabilityOperationException(HttpStatus.NOT_FOUND, "AVAILABILITY_SLOT_NOT_FOUND",
                "No se encontró el turno solicitado.");
    }
    private static AvailabilityOperationException slotOccupiedByAppointment() {
        return new AvailabilityOperationException(HttpStatus.CONFLICT, "AVAILABILITY_SLOT_OCCUPIED_BY_APPOINTMENT",
                "No se puede bloquear un turno con una cita pendiente o confirmada.");
    }
    private static AvailabilityOperationException slotReferencedByAppointment() {
        return new AvailabilityOperationException(HttpStatus.CONFLICT, "AVAILABILITY_SLOT_REFERENCED_BY_APPOINTMENT",
                "No se puede cambiar la hora de un turno que ya tiene historial de citas.");
    }
}
