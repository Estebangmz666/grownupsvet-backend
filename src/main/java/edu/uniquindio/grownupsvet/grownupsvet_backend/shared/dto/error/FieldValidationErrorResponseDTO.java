package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.Assert;

/** Identifies a request field that could not be accepted without exposing its value. */
@Schema(description = "Error asociado a un campo de la solicitud. Nunca contiene el valor recibido.")
public record FieldValidationErrorResponseDTO(
        @Schema(description = "Nombre del campo en el contrato JSON.", example = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,

        @Schema(description = "Código estable que identifica el error del campo.", example = "TYPE_MISMATCH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "Explicación legible para la persona usuaria.", example = "Debe enviarse como texto.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
    public FieldValidationErrorResponseDTO {
        Assert.hasText(field, "field must not be blank");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(message, "message must not be blank");
    }
}
