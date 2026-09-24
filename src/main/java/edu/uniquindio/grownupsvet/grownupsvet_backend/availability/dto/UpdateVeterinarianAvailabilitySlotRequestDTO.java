package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateVeterinarianAvailabilitySlotRequestDTO(
        @NotNull @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime startsAt,
        @NotNull @PositiveOrZero @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @NotBlank @Size(max = 500) @Schema(minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
