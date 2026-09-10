package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UserPasswordRecoveryVerificationRequestDTO(
        @NotBlank @Email @Size(max = 254)
        @Schema(type = "string", format = "email", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", pattern = "^[0-9]{6}$", minLength = 6, maxLength = 6,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Código de seis dígitos; conserva los ceros iniciales.") String code) {
    @Override public String toString() {
        return "UserPasswordRecoveryVerificationRequestDTO[credentials=[REDACTED]]";
    }
}
