package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AvailableVeterinarianSlotResponseDTO(
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = {"string"}, format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID veterinarianId,
        @Schema(types = {"string"}, maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED) String veterinarianFullName,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(types = {"string"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(types = {"string"}, allowableValues = "America/Bogota", requiredMode = Schema.RequiredMode.REQUIRED) String timeZone) { }
