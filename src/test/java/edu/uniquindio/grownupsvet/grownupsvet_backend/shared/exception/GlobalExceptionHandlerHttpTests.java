package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration.JsonTypeConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.EmailAlreadyRegisteredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual MVC conversion, validation and exception resolution without a database or security filters. */
@WebMvcTest(GlobalExceptionHandlerHttpTests.ErrorContractTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JsonTypeConfiguration.class, ApiErrorResponseFactory.class, GlobalExceptionHandler.class,
        GlobalExceptionHandlerHttpTests.ErrorContractTestController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerHttpTests {

    private static final String PRIVATE_VALUE = "private-password-and-email@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void validRequestStillReachesController() throws Exception {
        mockMvc.perform(post("/test/errors/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"persona@example.com","password":"A test passphrase"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void validationReturnsFieldCodesWithoutRejectedValuesOrRequestQuery() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/login-request")
                        .queryParam("password", PRIVATE_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid " + PRIVATE_VALUE + "\",\"password\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code", hasItem("INVALID_EMAIL")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].code", hasItem("REQUIRED")));

        assertProblem(response, 400, "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12.5", "true", "[]", "{}"})
    void incompatibleJsonTypesUseDeclaredFieldAndNeverIncludeSubmittedValue(String passwordJson) throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + PRIVATE_VALUE + "\",\"password\":" + passwordJson + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("TYPE_MISMATCH"));

        assertProblem(response, 400, "TYPE_MISMATCH");
    }

    @Test
    void malformedJsonDoesNotExposeParserExcerpt() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + PRIVATE_VALUE + "\",\"password\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertProblem(response, 400, "MALFORMED_JSON");
    }

    @Test
    void unknownJsonPropertyDoesNotEchoArbitraryPropertyNameOrValue() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"persona@example.com\",\"password\":\"A valid passphrase\",\""
                                + PRIVATE_VALUE + "\":\"" + PRIVATE_VALUE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertProblem(response, 400, "UNKNOWN_PROPERTY");
    }

    @Test
    void wrongMethodRetainsAllowHeader() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/login-request"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"));

        assertProblem(response, 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedContentTypeKeepsIts415Status() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/login-request")
                        .contentType(MediaType.TEXT_PLAIN).content(PRIVATE_VALUE))
                .andExpect(status().isUnsupportedMediaType());

        assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unavailableRepresentationReturnsProblemEvenForAnUnsupportedAcceptHeader() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());

        assertProblem(response, 406, "NOT_ACCEPTABLE");
    }

    @Test
    void missingResourceReturns404WithoutEchoingUrl() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/missing/" + PRIVATE_VALUE))
                .andExpect(status().isNotFound());

        assertProblem(response, 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void oversizedUploadKeepsIts413Status() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/large-upload"))
                .andExpect(status().isContentTooLarge());

        assertProblem(response, 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void queryTypeMismatchDoesNotExposeValue() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/page").queryParam("page", PRIVATE_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));

        assertProblem(response, 400, "TYPE_MISMATCH");
    }

    @Test
    void methodParameterConstraintReturnsFieldFeedback() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/page").queryParam("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));

        assertProblem(response, 400, "VALIDATION_FAILED");
    }

    @Test
    void onlyKnownEmailConflictReturns409() throws Exception {
        ResultActions response = mockMvc.perform(post("/test/errors/duplicate-email"))
                .andExpect(status().isConflict());

        assertProblem(response, 409, "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void methodAccessDenialUsesSamePublicContractAsSecurityFilters() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden());

        assertProblem(response, 403, "ACCESS_DENIED");
    }

    @Test
    void authenticationFailureIncludesTheBearerChallenge() throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpected", "illegal-argument", "database-integrity"})
    void unexpectedFailuresStay500AndDiagnosticsDoNotLeakSecrets(String failure, CapturedOutput output) throws Exception {
        ResultActions response = mockMvc.perform(get("/test/errors/" + failure)
                        .queryParam("secret", PRIVATE_VALUE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.detail").value("No pudimos completar la solicitud. Inténtalo de nuevo más tarde."));

        JsonNode problem = assertProblem(response, 500, "INTERNAL_ERROR");
        assertThat(output.getAll()).contains(problem.path("instance").asString(), "exceptionType=", "locations=")
                .doesNotContain(PRIVATE_VALUE, "sensitive_sql_statement");
    }

    @Test
    void eachFailureHasADifferentOpaqueOccurrenceIdentifier() throws Exception {
        JsonNode first = assertProblem(mockMvc.perform(get("/test/errors/login-request")), 405, "METHOD_NOT_ALLOWED");
        JsonNode second = assertProblem(mockMvc.perform(get("/test/errors/login-request")), 405, "METHOD_NOT_ALLOWED");

        assertThat(first.path("instance").asString()).isNotEqualTo(second.path("instance").asString());
    }

    private JsonNode assertProblem(ResultActions response, int statusCode, String errorCode) throws Exception {
        String body = response
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode problem = jsonMapper.readTree(body);
        String instance = problem.path("instance").asString();
        assertThat(instance).startsWith("urn:uuid:");
        assertThat(UUID.fromString(instance.substring("urn:uuid:".length())).version()).isEqualTo(4);
        assertThat(body).doesNotContain(PRIVATE_VALUE, "rejectedValue", "sensitive_sql_statement", "/test/errors");
        return problem;
    }

    /** Only this test slice registers these routes. */
    @RestController
    static class ErrorContractTestController {

        @PostMapping(value = "/test/errors/login-request", consumes = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<Void> validate(@Valid @RequestBody UserLoginRequestDTO request) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping(value = "/test/errors/json", produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<Void> json() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/test/errors/page")
        ResponseEntity<Void> page(@RequestParam @Min(0) int page) {
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/test/errors/large-upload")
        ResponseEntity<Void> oversizedUpload() {
            throw new MaxUploadSizeExceededException(2_097_152);
        }

        @PostMapping("/test/errors/duplicate-email")
        ResponseEntity<Void> duplicateEmail() {
            throw new EmailAlreadyRegisteredException();
        }

        @GetMapping("/test/errors/forbidden")
        ResponseEntity<Void> forbidden() {
            throw new AccessDeniedException(PRIVATE_VALUE);
        }

        @GetMapping("/test/errors/unauthorized")
        ResponseEntity<Void> unauthorized() {
            throw new AuthenticationCredentialsNotFoundException(PRIVATE_VALUE);
        }

        @GetMapping("/test/errors/unexpected")
        ResponseEntity<Void> unexpected() {
            throw new IllegalStateException(PRIVATE_VALUE);
        }

        @GetMapping("/test/errors/illegal-argument")
        ResponseEntity<Void> illegalArgument() {
            throw new IllegalArgumentException(PRIVATE_VALUE);
        }

        @GetMapping("/test/errors/database-integrity")
        ResponseEntity<Void> databaseIntegrity() {
            throw new DataIntegrityViolationException("sensitive_sql_statement " + PRIVATE_VALUE);
        }
    }
}
