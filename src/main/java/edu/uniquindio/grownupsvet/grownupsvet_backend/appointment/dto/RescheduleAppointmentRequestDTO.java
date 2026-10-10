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

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Reprogramación administrativa con constancia obligatoria del acuerdo previo con el propietario.")
public record RescheduleAppointmentRequestDTO(
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID clientRequestId,
        @NotNull @Schema(types = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) UUID availabilitySlotId,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedAvailabilitySlotVersion,
        @NotNull @Min(0) @Schema(types = "integer", format = "int64", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) Long expectedVersion,
        @NotBlank @Size(min = 1, max = 1000) @Schema(types = "string", minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @NotBlank @Pattern(regexp = "PHONE|WHATSAPP|OTHER")
        @Schema(types = "string", allowableValues = {"PHONE", "WHATSAPP", "OTHER"}, requiredMode = Schema.RequiredMode.REQUIRED) String agreementChannel,
        @NotNull @JsonDeserialize(using = StrictAgreementContactedAtDeserializer.class)
        @Schema(types = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Fecha textual ISO 8601 con offset explícito; no puede ser posterior a la operación.") Instant agreementContactedAt,
        @NotBlank @Size(min = 1, max = 500) @Schema(types = "string", minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED) String agreementNote) { }
