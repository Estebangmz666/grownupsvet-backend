package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserPasswordRecoveryVerificationResponseDTO(
        @Schema(type = "string", minLength = 43, maxLength = 43, pattern = "^[A-Za-z0-9_-]{43}$",
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Permiso opaco de un uso exclusivo para restablecer contraseña. No es un JWT de sesión.")
        String resetToken,
        @Schema(implementation = Long.class, type = "integer", format = "int64", minimum = "1", example = "300",
                requiredMode = Schema.RequiredMode.REQUIRED, description = "Vigencia restante en segundos.")
        long expiresIn) {
    @Override public String toString() {
        return "UserPasswordRecoveryVerificationResponseDTO[resetToken=[REDACTED], expiresIn=" + expiresIn + "]";
    }
}
