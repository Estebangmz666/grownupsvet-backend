package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.*;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateStaffStatusRequestDTO(@NotNull @Schema(allowableValues = {"ACTIVE", "DISABLED"}, description = "No permite activar una cuenta que nunca estableció contraseña ni asignar PENDING_ACTIVATION.") UserStatus status) { }
