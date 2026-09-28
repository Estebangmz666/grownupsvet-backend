package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateAppointmentStatusRequestDTO(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AppointmentStatus status,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @Size(max = 1000) @Schema(types = "string", maxLength = 1000, nullable = true) String reason) { }
