package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Estado real del aviso. SENT acredita aceptación SMTP; no lectura ni entrega a un buzón real.")
public record AppointmentNotificationResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId,
        @Schema(types = "string", allowableValues = {"ADMIN_REASSIGNMENT", "OWNER_REASSIGNED", "OWNER_CONFIRMED", "OWNER_REJECTED", "OWNER_CANCELLED", "OWNER_RESCHEDULED", "OWNER_REMINDER"}, requiredMode = Schema.RequiredMode.REQUIRED) String notificationType,
        @Schema(types = "string", allowableValues = {"PENDING", "SENDING", "SENT", "FAILED", "SUPERSEDED"}, requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant scheduledAt,
        @Schema(types = {"string", "null"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Próximo intento pendiente; null si el aviso no espera un reintento.") Instant nextAttemptAt,
        @Schema(types = {"string", "null"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant sentAt,
        @Schema(types = "integer", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int attemptCount,
        @Schema(types = {"string", "null"}, maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED) String errorCode) { }
