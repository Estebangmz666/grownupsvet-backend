package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateClinicalEncounterRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId) { }
