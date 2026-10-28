package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.AppointmentNotificationPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.AppointmentNotificationResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.exception.AppointmentOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentNotificationsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Event and task writes share the business transaction; no SMTP or historical backfill happens here. */
@Service
@Transactional(readOnly = true)
public class AppointmentNotificationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AppointmentProperties properties;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public AppointmentNotificationService(NamedParameterJdbcTemplate jdbc, AppointmentProperties properties,
            ObjectMapper mapper, ApplicationEventPublisher events) {
        this.jdbc = jdbc; this.properties = properties; this.mapper = mapper; this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(UUID appointmentId, long version, String eventType, Instant occurredAt) {
        String taskType = switch (eventType) {
            case "CONFIRMED" -> "OWNER_CONFIRMED";
            case "REJECTED" -> "OWNER_REJECTED";
            case "CANCELLED", "DAILY_CUTOFF" -> "OWNER_CANCELLED";
            case "RESCHEDULED" -> "OWNER_RESCHEDULED";
            case "REASSIGNED" -> "OWNER_REASSIGNED";
            default -> null;
        };
        if (taskType == null && !"NO_SHOW".equals(eventType)) { return; }
        // A business revision supersedes pending work, including a lease held by an older worker.
        jdbc.update("update appointment_email_tasks set status='SUPERSEDED',lease_token=null,last_error_code='NOTICE_NO_LONGER_CURRENT' " +
                        "where appointment_id=:appointment and task_type<>'ADMIN_REASSIGNMENT' and status in ('PENDING','SENDING') " +
                        "and event_key<>(select id from appointment_events where appointment_id=:appointment and appointment_version=:version)",
                Map.of("appointment", appointmentId, "version", version));
        if (taskType == null) { return; }
        var source = jdbc.queryForMap("select a.owner_id,u.email,a.status,a.starts_at,a.current_slot_id,p.name pet_name," +
                        "vp.full_name veterinarian_name,e.id event_id " +
                        "from appointments a join users u on u.id=a.owner_id join pets p on p.id=a.pet_id " +
                        "join veterinarian_availability_slots s on s.id=a.current_slot_id " +
                        "join veterinarian_profiles vp on vp.user_id=s.veterinarian_id " +
                        "join appointment_events e on e.appointment_id=a.id and e.appointment_version=:version where a.id=:appointment",
                Map.of("appointment", appointmentId, "version", version));
        UUID eventId = (UUID) source.get("event_id");
        Instant startsAt = ((Timestamp) source.get("starts_at")).toInstant();
        String payload = mapper.writeValueAsString(Map.of("eventVersion", version, "eventType", eventType,
                "startsAt", startsAt.toString(), "availabilitySlotId", source.get("current_slot_id").toString()));
        if ("CONFIRMED".equals(eventType)) {
            enqueueConfirmation(appointmentId, eventId, source, payload, startsAt, occurredAt);
        } else {
            enqueue(appointmentId, eventId, source, taskType, payload, occurredAt, occurredAt);
            if ("CONFIRMED".equals(source.get("status"))) {
                Instant scheduledAt = startsAt.minus(properties.getReminderAdvance());
                // Equality is eligible; late confirmations/changes never create a retroactive reminder.
                if (!scheduledAt.isBefore(occurredAt)) {
                    enqueue(appointmentId, eventId, source, "OWNER_REMINDER", payload, scheduledAt, occurredAt);
                }
            }
        }
        events.publishEvent(new AppointmentNotificationsQueuedEvent(appointmentId));
    }

    private void enqueueConfirmation(UUID appointmentId, UUID eventId, Map<String, Object> source,
            String payload, Instant startsAt, Instant now) {
        Instant reminderAt = startsAt.minus(properties.getReminderAdvance());
        boolean reminderEligible = "CONFIRMED".equals(source.get("status")) && !reminderAt.isBefore(now);
        String statement = "insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload," +
                "status,attempt_count,scheduled_at,next_attempt_at,created_at) " +
                "values(:id,:appointment,:event,:recipient,:email,'OWNER_CONFIRMED',cast(:payload as jsonb),'PENDING',0,:now,:now,:now)";
        var parameters = new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("appointment", appointmentId)
                .addValue("event", eventId).addValue("recipient", source.get("owner_id")).addValue("email", source.get("email"))
                .addValue("payload", payload).addValue("now", Timestamp.from(now));
        if (reminderEligible) {
            statement += ",(:reminderId,:appointment,:event,:recipient,:email,'OWNER_REMINDER',cast(:payload as jsonb),'PENDING',0,:reminder,:reminder,:now)";
            parameters.addValue("reminderId", UUID.randomUUID()).addValue("reminder", Timestamp.from(reminderAt));
        }
        jdbc.update(statement + " on conflict(event_key,recipient_id,task_type) do nothing", parameters);
    }

    private void enqueue(UUID appointmentId, UUID eventId, Map<String, Object> source, String type,
            String payload, Instant scheduledAt, Instant now) {
        jdbc.update("insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload," +
                        "status,attempt_count,scheduled_at,next_attempt_at,created_at) " +
                        "values(:id,:appointment,:event,:recipient,:email,:type,cast(:payload as jsonb),'PENDING',0,:scheduled,:scheduled,:now) " +
                        "on conflict(event_key,recipient_id,task_type) do nothing",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("appointment", appointmentId)
                        .addValue("event", eventId).addValue("recipient", source.get("owner_id")).addValue("email", source.get("email"))
                        .addValue("type", type).addValue("payload", payload).addValue("scheduled", Timestamp.from(scheduledAt))
                        .addValue("now", Timestamp.from(now)));
    }

    public AppointmentNotificationPageResponseDTO list(UUID actorId, UUID appointmentId, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new AppointmentOperationException(HttpStatus.BAD_REQUEST, "INVALID_APPOINTMENT_REQUEST", "La paginación solicitada no es válida.");
        }
        var actor = jdbc.queryForMap("select role,status from users where id=:actor", Map.of("actor", actorId));
        UserRole role = UserRole.valueOf((String) actor.get("role"));
        if (!"ACTIVE".equals(actor.get("status")) || (role != UserRole.OWNER && role != UserRole.ADMINISTRATOR)) {
            throw new AppointmentOperationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "La cuenta no tiene permiso para consultar estos avisos.");
        }
        boolean owner = role == UserRole.OWNER;
        Boolean visible = jdbc.queryForObject("select exists(select 1 from appointments where id=:appointment " +
                        (owner ? "and owner_id=:actor" : "") + ")",
                Map.of("appointment", appointmentId, "actor", actorId), Boolean.class);
        if (!Boolean.TRUE.equals(visible)) {
            throw new AppointmentOperationException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "No se encontró la cita solicitada.");
        }
        String predicate = "appointment_id=:appointment" + (owner ? " and recipient_id=:actor" : "");
        var parameters = new MapSqlParameterSource().addValue("appointment", appointmentId).addValue("actor", actorId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("select count(*) from appointment_email_tasks where " + predicate, parameters, Long.class);
        List<AppointmentNotificationResponseDTO> items = jdbc.query("select id,appointment_id,task_type,status,scheduled_at," +
                        "case when status='PENDING' then next_attempt_at else null end next_attempt_at,sent_at,attempt_count," +
                        "case when last_error_code in ('NOTICE_NO_LONGER_CURRENT','SMTP_DELIVERY_FAILED','EMAIL_CONFIGURATION_MISSING'," +
                        "'DELIVERY_ATTEMPTS_EXHAUSTED','NO_ACTIVE_ADMINISTRATORS') then last_error_code else null end error_code " +
                        "from appointment_email_tasks where " + predicate + " order by created_at,id limit :limit offset :offset",
                parameters, (rs, row) -> new AppointmentNotificationResponseDTO(rs.getObject("id", UUID.class),
                        rs.getObject("appointment_id", UUID.class), rs.getString("task_type"), rs.getString("status"),
                        rs.getTimestamp("scheduled_at").toInstant(), nullableInstant(rs.getTimestamp("next_attempt_at")),
                        nullableInstant(rs.getTimestamp("sent_at")), rs.getInt("attempt_count"), rs.getString("error_code")));
        return new AppointmentNotificationPageResponseDTO(items, page, size, total, (int) Math.ceil((double) total / size));
    }

    private static Instant nullableInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
