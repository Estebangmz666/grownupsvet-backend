package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bearer session created after valid credentials. No refresh token is issued.")
public record UserLoginResponseDTO(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY,
                description = "RS256-signed JWT access token.")
        String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "Bearer", example = "Bearer")
        String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "86400",
                description = "Token lifetime in seconds.")
        long expiresIn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AuthenticatedUserResponseDTO user
) {
    public UserLoginResponseDTO {
        if (!"Bearer".equals(tokenType)) {
            throw new IllegalArgumentException("tokenType must be Bearer");
        }
    }

    @Override
    public String toString() {
        return "UserLoginResponseDTO[accessToken=[REDACTED], tokenType=Bearer, expiresIn="
                + expiresIn + ", user=" + user + "]";
    }
}
