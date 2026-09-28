package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AppointmentPageResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AppointmentResponseDTO> items,
        @Schema(types = "integer", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(types = "integer", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(types = "integer", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) { }
