package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AvailableVeterinarianSlotPageResponseDTO(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), minItems = 0, maxItems = 100,
                schema = @Schema(implementation = AvailableVeterinarianSlotResponseDTO.class)) List<AvailableVeterinarianSlotResponseDTO> items,
        @Schema(types = {"integer"}, format = "int32", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(types = {"integer"}, format = "int32", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(types = {"integer"}, format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(types = {"integer"}, format = "int32", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {
    public AvailableVeterinarianSlotPageResponseDTO { items = List.copyOf(items); }
}
