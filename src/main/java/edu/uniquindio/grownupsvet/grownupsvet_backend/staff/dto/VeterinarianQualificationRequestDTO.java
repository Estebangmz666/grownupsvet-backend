package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.*;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianQualificationRequestDTO(
        @NotNull QualificationType type,
        @NotBlank @Size(max = 200) @Schema(maxLength = 200, example = "Especialización en Medicina Interna") String title,
        @NotBlank @Size(max = 200) @Schema(maxLength = 200, example = "Universidad del Quindío") String institution,
        @Min(1900) @Max(9999) @Schema(types = {"integer", "null"}, format = "int32", minimum = "1900", maximum = "9999", description = "Opcional. No puede ser posterior al año actual en America/Bogota.") Integer graduationYear
) { }
