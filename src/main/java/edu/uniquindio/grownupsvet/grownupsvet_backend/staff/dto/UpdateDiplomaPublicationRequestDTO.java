package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateDiplomaPublicationRequestDTO(
        @NotNull @Schema(description = "true confirma la revisión y permite consultar el diploma a propietarios autenticados cuando el veterinario esté activo.") Boolean published) { }
