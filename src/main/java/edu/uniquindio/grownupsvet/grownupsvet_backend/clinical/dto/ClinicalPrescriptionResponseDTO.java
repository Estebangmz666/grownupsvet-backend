package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalPrescriptionResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED) String medicationName,
        @Schema(types = "number", minimum = "0.000001", maximum = "999999999999.999999", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal dose,
        @Schema(types = "string", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED) String doseUnit,
        @Schema(types = "string", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) String administrationRoute,
        @Schema(types = "integer", minimum = "1", maximum = "168", requiredMode = Schema.RequiredMode.REQUIRED) int intervalHours,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(types = {"string", "null"}, maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED) String instructions) { }
