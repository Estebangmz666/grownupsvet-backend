package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Vista viva de atenciones publicadas, ordenada por finalizedAt e id descendentes. " +
        "Totales y filas comparten una instantánea por respuesta; nuevas publicaciones pueden desplazar páginas entre solicitudes.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalHistoryPageResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClinicalHistoryEntryResponseDTO> items,
        @Schema(types = "integer", format = "int32", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(types = "integer", format = "int32", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(types = "integer", format = "int32", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {
    public ClinicalHistoryPageResponseDTO { items = List.copyOf(items); }
}
