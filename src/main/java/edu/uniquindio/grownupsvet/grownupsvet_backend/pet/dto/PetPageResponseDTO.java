package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página estable de mascotas propias, ordenada por createdAt descendente e id ascendente. Los totales aplican al filtro active, cuando se envía.")
public record PetPageResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PetResponseDTO> items,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {
    public PetPageResponseDTO { items = List.copyOf(items); }
}
