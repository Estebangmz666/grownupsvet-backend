package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentAssignmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AppointmentResponseDTO(
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID ownerId,
        @Schema(types = "string", maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED) String ownerFullName,
        @Schema(types = "string", maxLength = 16, requiredMode = Schema.RequiredMode.REQUIRED) String ownerPhoneNumber,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID petId,
        @Schema(types = "string", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED) String petName,
        @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID veterinarianId,
        @Schema(types = "string", maxLength = 150, requiredMode = Schema.RequiredMode.REQUIRED) String veterinarianFullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AppointmentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AppointmentAssignmentStatus assignmentStatus,
        @Schema(types = "string", maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
        @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(types = "string", allowableValues = "America/Bogota", requiredMode = Schema.RequiredMode.REQUIRED) String timeZone) { }
