package edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Minimal signup receipt; authentication remains a separate operation. */
@Schema(description = "Cuenta de propietario creada. La interfaz conduce al login; esta respuesta no contiene sesión ni contraseña.")
public record UserSignupResponseDTO(
        @Schema(type = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7d667530-867b-4c78-a458-0904fb82d574")
        UUID id,
        @Schema(type = "string", format = "email", maxLength = 254, requiredMode = Schema.RequiredMode.REQUIRED,
                example = "persona@example.com", description = "Correo normalizado de la cuenta creada.")
        String email
) {
}
