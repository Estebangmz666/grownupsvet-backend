package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiErrorResponseDTOTests {

    private final JsonMapper jsonMapper = new JacksonJsonHttpMessageConverter().getMapper();

    @Test
    void serializesStandardMembersAndTypedExtensionsAtTheRoot() {
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                HttpStatus.BAD_REQUEST, "Solicitud incorrecta", "Revisa los campos indicados.", "INVALID_REQUEST",
                URI.create("urn:uuid:7d667530-867b-4c78-a458-0904fb82d574"),
                List.of(new FieldValidationErrorResponseDTO("email", "TYPE_MISMATCH", "Debe enviarse como texto.")));

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "type", "title", "status", "detail", "instance", "errorCode", "fieldErrors");
        assertThat(json.path("type").stringValue()).isEqualTo("about:blank");
        assertThat(json.path("status").isIntegralNumber()).isTrue();
        assertThat(json.path("status").intValue()).isEqualTo(400);
        assertThat(json.path("title").stringValue()).isEqualTo("Solicitud incorrecta");
        assertThat(json.path("detail").stringValue()).isEqualTo("Revisa los campos indicados.");
        assertThat(json.path("instance").stringValue()).isEqualTo("urn:uuid:7d667530-867b-4c78-a458-0904fb82d574");
        assertThat(json.path("errorCode").stringValue()).isEqualTo("INVALID_REQUEST");
        assertThat(json.path("fieldErrors").isArray()).isTrue();
        JsonNode fieldError = json.path("fieldErrors").get(0);
        assertThat(fieldError.propertyNames()).containsExactlyInAnyOrder("field", "code", "message");
        assertThat(fieldError.path("field").stringValue()).isEqualTo("email");
        assertThat(fieldError.path("code").stringValue()).isEqualTo("TYPE_MISMATCH");
        assertThat(fieldError.path("message").stringValue()).isEqualTo("Debe enviarse como texto.");
    }

    @Test
    void serializesEmptyFieldErrorsAndOmitsAnAbsentInstance() {
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                HttpStatus.UNAUTHORIZED, "No pudimos entrar", "Revisa tu correo y contraseña.",
                "INVALID_CREDENTIALS", null, null);

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(json.path("fieldErrors").isArray()).isTrue();
        assertThat(json.path("fieldErrors")).isEmpty();
        assertThat(json.has("instance")).isFalse();
        assertThat(json.has("properties")).isFalse();
    }

    @Test
    void copiesFieldErrorsSoTheResponseCannotChangeThroughTheOriginalList() {
        List<FieldValidationErrorResponseDTO> fieldErrors = new ArrayList<>();
        fieldErrors.add(new FieldValidationErrorResponseDTO("email", "INVALID_FORMAT", "Revisa el correo."));
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                HttpStatus.BAD_REQUEST, "Solicitud incorrecta", "Revisa los campos indicados.",
                "INVALID_REQUEST", null, fieldErrors);

        fieldErrors.clear();

        assertThat(response.getFieldErrors()).hasSize(1);
        assertThatThrownBy(() -> response.getFieldErrors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void documentsTypedExtensionsWithoutAPropertiesEnvelope() {
        ResolvedSchema resolved = new ModelConverters().resolveAsResolvedSchema(
                new AnnotatedType(ApiErrorResponseDTO.class));
        Schema<?> schema = resolved.schema;

        assertThat(schema.getProperties()).containsKeys(
                "type", "title", "status", "detail", "instance", "errorCode", "fieldErrors");
        assertThat(schema.getProperties()).doesNotContainKey("properties");
        assertThat(schema.getRequired()).containsExactlyInAnyOrder(
                "type", "title", "status", "detail", "errorCode", "fieldErrors");
        assertThat(schema.getProperties().get("errorCode").getType()).isEqualTo("string");
        Schema<?> fieldErrors = schema.getProperties().get("fieldErrors");
        assertThat(fieldErrors.getType()).isEqualTo("array");
        assertThat(fieldErrors.getItems().get$ref()).isEqualTo("#/components/schemas/FieldValidationErrorResponseDTO");
        Schema<?> fieldError = resolved.referencedSchemas.get("FieldValidationErrorResponseDTO");
        assertThat(fieldError.getRequired()).containsExactlyInAnyOrder("field", "code", "message");
        assertThat(fieldError.getProperties().values()).allMatch(property -> "string".equals(property.getType()));
    }
}
