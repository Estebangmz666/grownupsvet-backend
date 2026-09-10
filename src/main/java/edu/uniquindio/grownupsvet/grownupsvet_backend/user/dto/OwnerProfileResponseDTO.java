package edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Cuenta y perfil del propietario autenticado; nunca contiene credenciales.")
public record OwnerProfileResponseDTO(
        @Schema(type = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(type = "string", format = "email", maxLength = 254, requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        @Schema(type = "string", maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dateOfBirth,
        @Schema(type = "string", pattern = "^\\+[1-9][0-9]{1,14}$", requiredMode = Schema.RequiredMode.REQUIRED)
        String phoneNumber,
        @Schema(type = "string", nullable = true, example = "/api/v1/users/me/profile/photo",
                description = "Ruta API relativa de la foto protegida, o null cuando no existe.")
        String profilePhotoUrl
) { }
