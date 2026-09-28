package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RevealOwnerEmailRequestDTO(
        @NotBlank @Size(max = 500) @Schema(types = "string", minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
