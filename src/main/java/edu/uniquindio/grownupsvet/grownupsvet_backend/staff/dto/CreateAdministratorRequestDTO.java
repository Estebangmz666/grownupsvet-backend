package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateAdministratorRequestDTO(@NotBlank @Email @Size(min = 3, max = 254) @Schema(format = "email", example = "personal@example.com", description = "Correo privado de acceso; inmutable después de crear la cuenta.") String email, @NotBlank @ValidFullName @Schema(minLength = 3, maxLength = 150, example = "María Fernanda Gómez") String fullName) {
    @Override public String toString() { return "CreateAdministratorRequestDTO[personalData=[REDACTED]]"; }
}
