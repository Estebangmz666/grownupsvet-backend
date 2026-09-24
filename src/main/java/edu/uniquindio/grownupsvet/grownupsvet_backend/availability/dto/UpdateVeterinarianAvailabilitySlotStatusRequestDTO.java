package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateVeterinarianAvailabilitySlotStatusRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AvailabilitySlotStatus status,
        @NotNull @PositiveOrZero @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @NotBlank @Size(max = 500) @Schema(minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
