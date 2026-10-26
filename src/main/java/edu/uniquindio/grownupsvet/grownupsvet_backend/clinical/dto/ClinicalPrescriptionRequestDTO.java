package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalPrescriptionRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @NotBlank @Size(max = 200) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String medicationName,
        @NotNull @DecimalMin("0.000001") @Digits(integer = 12, fraction = 6)
        @Schema(types = "number", minimum = "0.000001", maximum = "999999999999.999999", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal dose,
        @NotBlank @Size(max = 50) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50) String doseUnit,
        @NotBlank @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String administrationRoute,
        @NotNull @Min(1) @Max(168) @Schema(types = "integer", minimum = "1", maximum = "168", requiredMode = Schema.RequiredMode.REQUIRED) Integer intervalHours,
        @NotNull @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime startsAt,
        @NotNull @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime endsAt,
        @Size(max = 2000) @Schema(types = {"string", "null"}, maxLength = 2000) String instructions) { }
