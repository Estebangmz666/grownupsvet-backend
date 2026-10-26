package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalControlResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant scheduledAt,
        @Schema(types = "string", maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
