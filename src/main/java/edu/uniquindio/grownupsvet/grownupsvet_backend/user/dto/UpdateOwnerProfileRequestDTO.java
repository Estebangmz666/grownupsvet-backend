package edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidInternationalPhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Único cambio de perfil permitido al propietario. No admite propiedades adicionales.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateOwnerProfileRequestDTO(
        @NotBlank @ValidInternationalPhoneNumber
        @Schema(type = "string", minLength = 3, maxLength = 16, pattern = "^\\+[1-9][0-9]{1,14}$",
                example = "+573001234567", requiredMode = Schema.RequiredMode.REQUIRED)
        String phoneNumber
) { }
