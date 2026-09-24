package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateAdministratorRequestDTO(@NotBlank @ValidFullName @Schema(minLength = 3, maxLength = 150, example = "María Fernanda Gómez") String fullName) { }
