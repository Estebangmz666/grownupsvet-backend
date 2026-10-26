package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalTreatmentResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID encounterId,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClinicalPrescriptionResponseDTO> prescriptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ClinicalControlResponseDTO> controls) { }
