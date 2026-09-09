package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.validation.ValidLoginPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials supplied when signing in with an email address and password.
 * Passwords are accepted as input only and are never included in diagnostic text.
 *
 * @param email the account's email address
 * @param password the unmodified password supplied by the user
 */
@Schema(description = "Credentials used to sign in to GrownupsVet.")
public record UserLoginRequestDTO(
        @NotBlank(message = "Escribe tu correo electrónico.")
        @Email(message = "Escribe un correo electrónico válido.")
        @Size(max = 254, message = "El correo electrónico no puede superar los 254 caracteres.")
        @Schema(description = "Email address associated with the account.",
                example = "persona@example.com", type = "string", format = "email",
                maxLength = 254, requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "Escribe tu contraseña.")
        @ValidLoginPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(description = "Account password. Accepted only in requests; never returned in responses.",
                type = "string", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                maxLength = 128, requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
    @Override
    public String toString() {
        return "UserLoginRequestDTO[email=[REDACTED], password=[REDACTED]]";
    }
}
