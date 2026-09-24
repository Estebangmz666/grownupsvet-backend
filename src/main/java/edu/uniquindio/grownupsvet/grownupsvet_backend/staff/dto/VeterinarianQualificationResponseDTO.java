package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianQualificationResponseDTO(
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UNDERGRADUATE", "SPECIALIZATION", "MASTERS", "DOCTORATE"}) QualificationType type,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String title,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String institution,
        @Schema(types = {"integer", "null"}, format = "int32", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1900", maximum = "9999") Integer graduationYear,
        @Schema(types = {"boolean"}, requiredMode = Schema.RequiredMode.REQUIRED) boolean isBaseDegree,
        @Schema(types = {"boolean"}, requiredMode = Schema.RequiredMode.REQUIRED) boolean diplomaAvailable,
        @Schema(types = {"boolean"}, requiredMode = Schema.RequiredMode.REQUIRED) boolean diplomaPublished) { }
