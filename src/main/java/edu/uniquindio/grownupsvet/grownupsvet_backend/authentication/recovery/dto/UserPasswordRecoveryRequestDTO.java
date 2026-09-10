package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UserPasswordRecoveryRequestDTO(
        @NotBlank @Email @Size(max = 254)
        @Schema(type = "string", format = "email", maxLength = 254, example = "persona@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED) String email) {
    @Override public String toString() { return "UserPasswordRecoveryRequestDTO[email=[REDACTED]]"; }
}
