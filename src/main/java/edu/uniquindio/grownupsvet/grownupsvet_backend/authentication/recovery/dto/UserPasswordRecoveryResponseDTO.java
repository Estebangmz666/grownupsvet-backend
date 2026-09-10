package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserPasswordRecoveryResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Mensaje idéntico para correos existentes, desconocidos y cuentas inactivas.")
        String message) {
    public static UserPasswordRecoveryResponseDTO accepted() {
        return new UserPasswordRecoveryResponseDTO(
                "Si existe una cuenta activa con este correo, recibirás un código para recuperar el acceso.");
    }
}
