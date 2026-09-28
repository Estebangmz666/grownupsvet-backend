package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AppointmentEventResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long appointmentVersion,
        @Schema(types = "string", allowableValues = {"REQUESTED", "CONFIRMED", "REJECTED", "DAILY_CUTOFF", "VETERINARIAN_DISABLED", "VETERINARIAN_RESTORED", "REASSIGNED", "EMAIL_ACCESSED"}, requiredMode = Schema.RequiredMode.REQUIRED) String eventType,
        @Schema(types = {"string", "null"}, allowableValues = {"REQUESTED", "CONFIRMED", "REJECTED", "CANCELLED"}, requiredMode = Schema.RequiredMode.REQUIRED) String previousStatus,
        @Schema(types = {"string", "null"}, allowableValues = {"REQUESTED", "CONFIRMED", "REJECTED", "CANCELLED"}, requiredMode = Schema.RequiredMode.REQUIRED) String newStatus,
        @Schema(types = {"string", "null"}, allowableValues = {"ASSIGNED", "NEEDS_REASSIGNMENT"}, requiredMode = Schema.RequiredMode.REQUIRED) String previousAssignmentStatus,
        @Schema(types = {"string", "null"}, allowableValues = {"ASSIGNED", "NEEDS_REASSIGNMENT"}, requiredMode = Schema.RequiredMode.REQUIRED) String newAssignmentStatus,
        @Schema(types = {"string", "null"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID previousSlotId,
        @Schema(types = {"string", "null"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID newSlotId,
        @Schema(types = {"string", "null"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID actorId,
        @Schema(types = "string", allowableValues = {"HUMAN", "SYSTEM"}, requiredMode = Schema.RequiredMode.REQUIRED) String actorType,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(types = {"string", "null"}, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
