package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration.JsonTypeConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception.ApiErrorResponseFactory;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises request conversion and validation independently of credential verification. */
@WebMvcTest(UserLoginRequestDTOHttpTests.LoginRequestValidationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JsonTypeConfiguration.class, ApiErrorResponseFactory.class,
        UserLoginRequestDTOHttpTests.LoginRequestValidationController.class})
class UserLoginRequestDTOHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void acceptsValidStringCredentials() throws Exception {
        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"persona@example.com","password":"A test passphrase"}
                                """))
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12.5", "true", "[]", "{}"})
    void rejectsNonStringEmail(String emailJson) throws Exception {
        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":" + emailJson + ",\"password\":\"A test passphrase\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12.5", "true", "[]", "{}"})
    void rejectsNonStringPassword(String passwordJson) throws Exception {
        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"persona@example.com\",\"password\":" + passwordJson + "}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"email\":null,\"password\":\"A test passphrase\"}",
            "{\"email\":\"persona@example.com\",\"password\":null}",
            "{\"email\":\"not-an-email\",\"password\":\"A test passphrase\"}",
            "{\"email\":\"persona@example.com\",\"password\":\" \"}"
    })
    void rejectsIncompleteOrInvalidCredentials(String requestBody) throws Exception {
        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPasswordLongerThanTheHashingContract() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(new LoginJson("persona@example.com", "a".repeat(129)));

        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accepts128UnicodeCodePointsEvenWhenTheyUseMoreUtf16Units() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(new LoginJson(
                "persona@example.com", "🐶".repeat(128)));

        mockMvc.perform(post("/test/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());
    }

    @Test
    void preservesPasswordInputWithoutLeakingItInJsonOrDiagnosticText() {
        String password = "  Keep my spaces!  ";
        UserLoginRequestDTO request = jsonMapper.readValue("""
                {"email":"persona@example.com","password":"  Keep my spaces!  "}
                """, UserLoginRequestDTO.class);

        assertThat(request.password()).isEqualTo(password);
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(request)).has("password")).isFalse();
        assertThat(request.toString()).doesNotContain(password, "persona@example.com");
    }

    @Test
    void documentsBothCredentialsAsRequiredStringsAndPasswordAsWriteOnly() {
        Schema<?> schema = new ModelConverters().resolveAsResolvedSchema(
                new AnnotatedType(UserLoginRequestDTO.class)).schema;

        assertThat(schema.getRequired()).containsExactlyInAnyOrder("email", "password");
        assertThat(schema.getProperties()).containsOnlyKeys("email", "password");
        assertThat(schema.getProperties().get("email").getType()).isEqualTo("string");
        assertThat(schema.getProperties().get("email").getFormat()).isEqualTo("email");
        assertThat(schema.getProperties().get("password").getType()).isEqualTo("string");
        assertThat(schema.getProperties().get("password").getMaxLength()).isEqualTo(128);
        assertThat(schema.getProperties().get("password").getWriteOnly()).isTrue();
    }

    private record LoginJson(String email, String password) { }

    /** Test-only endpoint: it validates a request without implementing authentication. */
    @RestController
    static class LoginRequestValidationController {
        @PostMapping("/test/login-request")
        ResponseEntity<Void> validate(@Valid @RequestBody UserLoginRequestDTO request) {
            return ResponseEntity.noContent().build();
        }
    }
}
