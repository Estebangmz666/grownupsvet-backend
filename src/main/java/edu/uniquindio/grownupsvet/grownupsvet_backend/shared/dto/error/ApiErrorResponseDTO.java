package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 9457 response with typed extensions for client handling and field feedback.
 * Serialize as {@code application/problem+json} through Spring's HTTP converters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta de error RFC 9457, con código funcional y errores por campo.",
        requiredProperties = {"type", "title", "status", "detail", "errorCode", "fieldErrors"})
public final class ApiErrorResponseDTO extends ProblemDetail {

    private final String errorCode;
    private final List<FieldValidationErrorResponseDTO> fieldErrors;

    /**
     * Creates an error response. An absent field list becomes an empty JSON array.
     * The optional instance should identify the occurrence without including secrets.
     */
    public ApiErrorResponseDTO(HttpStatus status, String title, String detail, String errorCode,
                               URI instance, List<FieldValidationErrorResponseDTO> fieldErrors) {
        super(Objects.requireNonNull(status, "status must not be null").value());
        Assert.isTrue(status.isError(), "status must be a client or server error");
        Assert.hasText(title, "title must not be blank");
        Assert.hasText(detail, "detail must not be blank");
        Assert.hasText(errorCode, "errorCode must not be blank");
        setType(URI.create("about:blank"));
        setTitle(title);
        setDetail(detail);
        setInstance(instance);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    @Override
    @Schema(description = "Referencia URI al tipo de problema.", format = "uri", example = "about:blank")
    public URI getType() {
        return super.getType();
    }

    @Override
    @Schema(description = "Resumen del problema.", example = "Solicitud incorrecta")
    public String getTitle() {
        return super.getTitle();
    }

    @Override
    @Schema(description = "Estado HTTP de la respuesta.", example = "400", minimum = "400", maximum = "599")
    public int getStatus() {
        return super.getStatus();
    }

    @Override
    @Schema(description = "Explicación de lo ocurrido en esta solicitud.", example = "Revisa los campos indicados.")
    public String getDetail() {
        return super.getDetail();
    }

    @Override
    @Schema(description = "Referencia URI opcional que identifica esta ocurrencia del problema.",
            format = "uri-reference", example = "urn:uuid:7d667530-867b-4c78-a458-0904fb82d574")
    public URI getInstance() {
        return super.getInstance();
    }

    @Schema(description = "Código funcional estable; el frontend no debe comparar los mensajes.",
            example = "INVALID_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    public String getErrorCode() {
        return errorCode;
    }

    @Schema(description = "Errores por campo. Es una lista vacía cuando el error no corresponde a campos específicos.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    public List<FieldValidationErrorResponseDTO> getFieldErrors() {
        return fieldErrors;
    }

    @Override
    @Schema(hidden = true)
    public Map<String, Object> getProperties() {
        return super.getProperties();
    }
}
