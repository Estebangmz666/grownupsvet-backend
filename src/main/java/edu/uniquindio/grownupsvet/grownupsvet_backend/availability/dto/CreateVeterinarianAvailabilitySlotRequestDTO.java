package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateVeterinarianAvailabilitySlotRequestDTO(
        @NotNull @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-05T09:00:00-05:00") OffsetDateTime startsAt) { }
