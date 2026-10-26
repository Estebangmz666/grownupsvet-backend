package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalControlRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @NotNull @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime scheduledAt,
        @NotBlank @Size(max = 1000) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000) String reason) { }
