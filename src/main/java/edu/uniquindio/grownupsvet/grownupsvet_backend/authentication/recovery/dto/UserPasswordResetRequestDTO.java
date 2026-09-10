package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidUserPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UserPasswordResetRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", minLength = 43, maxLength = 43, pattern = "^[A-Za-z0-9_-]{43}$",
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String resetToken,
        @NotBlank @ValidUserPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", format = "password", minLength = 15, maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword,
        @NotBlank @ValidUserPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", format = "password", minLength = 15, maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Debe coincidir exactamente con newPassword, incluidos espacios y mayúsculas.")
        String confirmNewPassword) {
    @Override public String toString() { return "UserPasswordResetRequestDTO[credentials=[REDACTED]]"; }
}
