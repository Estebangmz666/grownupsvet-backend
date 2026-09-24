package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateVeterinarianRequestDTO(@NotBlank @ValidFullName @Schema(minLength = 3, maxLength = 150, example = "María Fernanda Gómez") String fullName,
        @NotBlank @ValidInternationalPhoneNumber @Schema(example = "+573001234567", description = "Contacto profesional público autorizado, en formato E.164.") String professionalPhoneNumber,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ./-]{0,49}$") @Schema(maxLength = 50, example = "12345", description = "Identificador profesional único localmente, normalizado en mayúsculas; no acredita validación en COMVEZCOL.") String professionalRegistrationNumber,
        @NotBlank @Size(max = 200) @Schema(maxLength = 200, example = "Medicina Veterinaria") String baseDegreeTitle,
        @NotBlank @Size(max = 200) @Schema(maxLength = 200, example = "Universidad del Quindío") String baseDegreeInstitution,
        @Size(max = 2000) @Schema(types = {"string", "null"}, maxLength = 2000, description = "Presentación profesional opcional. Omitir o enviar null la elimina al reemplazar el perfil.") String biography) { }
