package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianAvailabilitySlotResponseDTO(
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID veterinarianId,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilitySlotStatus status,
        @Schema(types = {"integer"}, format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(types = {"string"}, allowableValues = "America/Bogota", requiredMode = Schema.RequiredMode.REQUIRED) String timeZone) { }
