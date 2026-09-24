package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StaffInvitationResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID invitationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Vencimiento del enlace de un uso, 48 horas desde su creación.") Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "PENDING",
                description = "Envío persistido y pendiente; no confirma recepción del correo.") String deliveryStatus) { }
