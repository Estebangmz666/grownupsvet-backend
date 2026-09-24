package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianResponseDTO(
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150) String fullName,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 16) String professionalPhoneNumber,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50) String professionalRegistrationNumber,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String baseDegreeTitle,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String baseDegreeInstitution,
        @Schema(types = {"string", "null"}, maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED) String biography,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, format = "email", maxLength = 254, description = "Correo privado de acceso.") String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PENDING_ACTIVATION", "ACTIVE", "DISABLED"}) UserStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VeterinarianQualificationResponseDTO> qualifications,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant createdAt,
        @Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant updatedAt) { }
