package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateVeterinarianAvailabilitySlotBatchResponseDTO(
        @Schema(types = {"integer"}, format = "int32", minimum = "1", maximum = "1000", requiredMode = Schema.RequiredMode.REQUIRED) int createdCount,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), minItems = 1, maxItems = 1000,
                schema = @Schema(implementation = VeterinarianAvailabilitySlotResponseDTO.class)) List<VeterinarianAvailabilitySlotResponseDTO> items,
        @Schema(types = {"string"}, allowableValues = "America/Bogota", requiredMode = Schema.RequiredMode.REQUIRED) String timeZone) {
    public CreateVeterinarianAvailabilitySlotBatchResponseDTO { items = List.copyOf(items); }
}
