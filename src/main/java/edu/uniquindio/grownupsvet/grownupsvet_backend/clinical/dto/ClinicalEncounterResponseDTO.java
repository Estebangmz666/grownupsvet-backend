package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalEncounterResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID petId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID ownerId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID veterinarianId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID createdBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ClinicalEncounterStatus status,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(types = {"string", "null"}, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(types = {"string", "null"}, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String anamnesis,
        @Schema(types = {"string", "null"}, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String physicalExamination,
        @Schema(types = {"string", "null"}, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String diagnosis,
        @Schema(types = {"string", "null"}, maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String carePlan,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(types = {"string", "null"}, format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant finalizedAt) { }
