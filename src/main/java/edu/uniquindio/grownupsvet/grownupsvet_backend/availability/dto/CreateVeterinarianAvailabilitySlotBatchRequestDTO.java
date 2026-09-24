package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateVeterinarianAvailabilitySlotBatchRequestDTO(
        @NotNull @Schema(type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startDate,
        @NotNull @Schema(type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate endDate,
        @NotEmpty @Size(min = 1, max = 7) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                List<@Min(1) @Max(7) Integer> daysOfWeek,
        @NotNull @Schema(pattern = "^(?:[01][0-9]|2[0-3]):(?:00|30)$", requiredMode = Schema.RequiredMode.REQUIRED) String dailyStartTime,
        @NotNull @Schema(pattern = "^(?:(?:[01][0-9]|2[0-3]):(?:00|30)|24:00)$", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "24:00") String dailyEndTime) { }
