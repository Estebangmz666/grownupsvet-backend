package edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidDateOfBirth;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidFullName;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidInternationalPhoneNumber;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidUserPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Public owner signup. Role and active status are assigned only by the server. */
@Schema(description = "Datos para crear una cuenta de propietario. No admite propiedades adicionales.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UserSignupRequestDTO(
        @NotBlank @Email @Size(min = 3, max = 254)
        @Schema(type = "string", format = "email", minLength = 3, maxLength = 254,
                example = "persona@example.com", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Correo de acceso. Se guarda en minúsculas; debe tener formato de correo válido.")
        String email,

        @NotBlank @ValidFullName
        @Schema(type = "string", minLength = 3, maxLength = 150,
                example = "María del Carmen Gómez", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Nombre y apellido, al menos dos componentes. Admite letras internacionales, apóstrofos, guiones y puntos; máximo 150 caracteres incluyendo espacios. Se retiran espacios exteriores al guardar.")
        String fullName,

        @NotNull @ValidDateOfBirth
        @Schema(type = "string", format = "date", example = "1955-05-20",
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Fecha no futura en formato YYYY-MM-DD. Edad de 0 a 130 años cumplidos, calculada con la fecha de America/Bogota.")
        LocalDate dateOfBirth,

        @NotBlank @ValidInternationalPhoneNumber
        @Schema(type = "string", minLength = 3, maxLength = 16, pattern = "^\\+[1-9][0-9]{1,14}$",
                example = "+573001234567", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Número internacional E.164 sin espacios ni extensiones. Se valida con metadatos internacionales; no acredita titularidad ni disponibilidad de WhatsApp.")
        String phoneNumber,

        @NotBlank @ValidUserPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", format = "password", minLength = 15, maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "15–128 puntos de código Unicode. Admite espacios, sin composición obligatoria. No se transforma ni se devuelve. Se rechazan contraseñas comunes.")
        String password
) {
    @Override
    public String toString() { return "UserSignupRequestDTO[personalData=[REDACTED], password=[REDACTED]]"; }
}
