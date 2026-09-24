package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record VeterinarianPageResponseDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VeterinarianResponseDTO> items,
        @Schema(types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", minimum = "0") int page,
        @Schema(types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", minimum = "1", maximum = "100") int size,
        @Schema(types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0") long totalElements,
        @Schema(types = {"integer"}, requiredMode = Schema.RequiredMode.REQUIRED, format = "int32", minimum = "0") int totalPages) { }
