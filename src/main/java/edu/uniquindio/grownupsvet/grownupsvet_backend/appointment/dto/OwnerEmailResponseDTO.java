package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record OwnerEmailResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId,
        @Schema(types = "string", format = "email", maxLength = 254, requiredMode = Schema.RequiredMode.REQUIRED) String email) { }
