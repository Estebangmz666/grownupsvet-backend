package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Resumen de una atención publicada; el contenido y tratamiento se consultan en el detalle autorizado.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ClinicalHistoryEntryResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID appointmentId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID petId,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID veterinarianId,
        @Schema(types = "string", maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED) String veterinarianFullName,
        @Schema(types = "string", maxLength = 4000, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant finalizedAt) { }
