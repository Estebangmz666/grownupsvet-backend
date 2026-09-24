package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidUserPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StaffAccountActivationRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", minLength = 43, maxLength = 43, pattern = "^[A-Za-z0-9_-]{43}$",
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @NotBlank @ValidUserPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", format = "password", minLength = 15, maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        String password,
        @NotBlank @ValidUserPassword
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(type = "string", format = "password", minLength = 15, maxLength = 128,
                accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Debe coincidir exactamente con password, incluidos espacios y mayúsculas.")
        String confirmPassword) {
    @Override public String toString() { return "StaffAccountActivationRequestDTO[credentials=[REDACTED]]"; }
}
