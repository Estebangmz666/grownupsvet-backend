package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ReassignAppointmentRequestDTO(
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID availabilitySlotId,
        @NotNull @jakarta.validation.constraints.Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedAvailabilitySlotVersion,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @NotBlank @Size(max = 1000) @Schema(types = "string", minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Pattern(regexp = "PHONE|WHATSAPP|OTHER") @Schema(types = "string", allowableValues = {"PHONE", "WHATSAPP", "OTHER"}, nullable = true) String agreementChannel,
        @JsonDeserialize(using = StrictAgreementContactedAtDeserializer.class)
        @Schema(types = "string", format = "date-time", nullable = true) Instant agreementContactedAt,
        @Size(max = 500) @Schema(types = "string", maxLength = 500, nullable = true) String agreementNote) { }
