package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Public account identity returned after successful authentication.")
public record AuthenticatedUserResponseDTO(
        @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> permissions
) {
    public AuthenticatedUserResponseDTO {
        permissions = List.copyOf(permissions);
    }
}
