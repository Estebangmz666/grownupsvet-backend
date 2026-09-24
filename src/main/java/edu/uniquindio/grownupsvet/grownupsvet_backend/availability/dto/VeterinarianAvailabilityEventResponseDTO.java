package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilityEventType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianAvailabilityEventResponseDTO(
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID slotId,
        @Schema(types = {"integer"}, format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long slotVersion,
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID actorId,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilityEventType eventType,
        @Schema(types = {"string", "null"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant previousStartsAt,
        @Schema(types = {"string", "null"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant previousEndsAt,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.REQUIRED) AvailabilitySlotStatus previousStatus,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant newStartsAt,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant newEndsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilitySlotStatus newStatus,
        @Schema(types = {"string", "null"}, minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
