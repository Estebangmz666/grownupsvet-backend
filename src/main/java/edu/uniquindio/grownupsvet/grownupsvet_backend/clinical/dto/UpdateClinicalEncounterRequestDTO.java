package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Reemplazo de campos del borrador: omitir un campo clínico o enviarlo en blanco lo limpia. No cambia relaciones.")
public record UpdateClinicalEncounterRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @Size(max = 4000) @Schema(types = {"string", "null"}, maxLength = 4000) String reason,
        @Size(max = 4000) @Schema(types = {"string", "null"}, maxLength = 4000) String anamnesis,
        @Size(max = 4000) @Schema(types = {"string", "null"}, maxLength = 4000) String physicalExamination,
        @Size(max = 4000) @Schema(types = {"string", "null"}, maxLength = 4000) String diagnosis,
        @Size(max = 4000) @Schema(types = {"string", "null"}, maxLength = 4000) String carePlan) { }
