package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Reemplazo completo de fórmula y controles; listas vacías eliminan sus elementos. Comparte la versión de la atención.")
public record UpdateClinicalTreatmentRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @NotNull @Size(max = 50) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<@NotNull @Valid ClinicalPrescriptionRequestDTO> prescriptions,
        @NotNull @Size(max = 50) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<@NotNull @Valid ClinicalControlRequestDTO> controls) { }
