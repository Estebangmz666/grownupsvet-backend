package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateAppointmentRequestDTO(
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID petId,
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID availabilitySlotId,
        @NotNull @jakarta.validation.constraints.Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedAvailabilitySlotVersion,
        @NotBlank @Size(max = 1000) @Schema(types = "string", minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
