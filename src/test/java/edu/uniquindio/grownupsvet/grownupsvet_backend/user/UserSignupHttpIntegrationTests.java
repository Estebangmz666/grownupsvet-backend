package edu.uniquindio.grownupsvet.grownupsvet_backend.user;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller.UserSignupController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL and real security filters; no outer test transaction hides commit or rollback failures. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSignupHttpIntegrationTests {

    private static final String REGISTRATION_PATH = UserSignupController.REGISTRATION_PATH;
    private static final String TEST_PASSWORD = "  Una frase privada de prueba 🌳  ";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private OwnerProfileRepository ownerProfileRepository;

    private String email;

    @BeforeEach
    void useAnIsolatedAccount() {
        email = "signup-test+" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void removeOnlyThisTestsAccount() {
        jdbcTemplate.update("DELETE FROM owner_profiles WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void createsAnOwnerAndProfileWithAnEncodedPasswordWithoutStartingASession() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("email", email.toUpperCase(Locale.ROOT));
        request.put("fullName", "  María O’Connor  ");

        MvcResult result = register(request, 201);
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.properties()).hasSize(2);
        UUID userId = UUID.fromString(response.path("id").asText());
        assertThat(response.path("email").asText()).isEqualTo(email);
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();

        Map<String, Object> account = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", userId);
        assertThat(account.get("role")).isEqualTo("OWNER");
        assertThat(account.get("active")).isEqualTo(true);
        String passwordHash = (String) account.get("password_hash");
        assertThat(passwordHash).startsWith("{argon2id}").doesNotContain(TEST_PASSWORD);
        assertThat(passwordEncoder.matches(TEST_PASSWORD, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches(TEST_PASSWORD.strip(), passwordHash)).isFalse();
        Map<String, Object> profile = jdbcTemplate.queryForMap("SELECT * FROM owner_profiles WHERE user_id = ?", userId);
        assertThat(profile.get("full_name")).isEqualTo("María O’Connor");
        assertThat(profile.get("date_of_birth").toString()).isEqualTo("1955-05-20");
        assertThat(profile.get("phone_number")).isEqualTo("+573001234567");
        saveResponseExample("signup-success.json", result);
    }

    @Test
    void hashesAll128UnicodeCharactersWithoutTruncation() throws Exception {
        String longPassword = "🌳".repeat(128);
        Map<String, Object> request = validRequest();
        request.put("password", longPassword);
        register(request, 201);
        String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(passwordEncoder.matches(longPassword, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches("🌳".repeat(127) + "🌲", passwordHash)).isFalse();
    }

    @Test
    void returnsConflictForAnExistingEmailRegardlessOfCase() throws Exception {
        register(validRequest(), 201);
        Map<String, Object> duplicate = validRequest();
        duplicate.put("email", email.toUpperCase(Locale.ROOT));
        MvcResult result = register(duplicate, 409);
        assertProblem(result, "EMAIL_ALREADY_REGISTERED");
        assertAccountCounts(1);
        saveResponseExample("signup-conflict.json", result);
    }

    @Test
    void handlesConcurrentDuplicateRegistrationAtTheDatabaseConstraint() throws Exception {
        String concurrentPassword = "Una frase exclusiva para la carrera";
        CyclicBarrier bothPassedTheEmailPrecheck = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothPassedTheEmailPrecheck.await(20, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(concurrentPassword);
        Map<String, Object> request = validRequest();
        request.put("password", concurrentPassword);
        String json = jsonMapper.writeValueAsString(request);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mockMvc.perform(post(REGISTRATION_PATH)
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn());
            var second = executor.submit(() -> mockMvc.perform(post(REGISTRATION_PATH)
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn());
            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .withFailMessage("Concurrent responses: %s, %s. Safe exception metadata: %s; %s",
                            firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus(),
                            safeExceptionMetadata(firstResult), safeExceptionMetadata(secondResult))
                    .containsExactlyInAnyOrder(201, 409);
            assertProblem(firstResult.getResponse().getStatus() == 409 ? firstResult : secondResult,
                    "EMAIL_ALREADY_REGISTERED");
        }
        assertAccountCounts(1);
    }

    @Test
    void rollsBackTheAccountIfSavingTheProfileFails() throws Exception {
        doThrow(new DataIntegrityViolationException("PRIVATE_DATABASE_DIAGNOSTIC"))
                .when(ownerProfileRepository).saveAndFlush(any(OwnerProfile.class));
        MvcResult result = register(validRequest(), 500);
        assertProblem(result, "INTERNAL_ERROR");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("PRIVATE_DATABASE_DIAGNOSTIC");
        assertAccountCounts(0);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsWithoutCreatingAnAccount(InvalidField invalidField) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(invalidField.field(), invalidField.value());
        MvcResult result = register(request, 400);
        assertProblem(result, invalidField.errorCode());
        JsonNode errors = jsonMapper.readTree(result.getResponse().getContentAsString()).path("fieldErrors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors.valueStream().anyMatch(error -> invalidField.field().equals(error.path("field").asText())))
                .isTrue();
        assertAccountCounts(0);
        saveResponseExample("signup-invalid.json", result);
    }

    static Stream<InvalidField> invalidFields() {
        return Stream.of(
                new InvalidField("fullName", "María", "VALIDATION_FAILED"),
                new InvalidField("dateOfBirth", "1800-01-01", "VALIDATION_FAILED"),
                new InvalidField("dateOfBirth", "2999-01-01", "VALIDATION_FAILED"),
                new InvalidField("dateOfBirth", "2000-02-30", "TYPE_MISMATCH"),
                new InvalidField("dateOfBirth", List.of(1955, 5, 20), "TYPE_MISMATCH"),
                new InvalidField("dateOfBirth", 0, "TYPE_MISMATCH"),
                new InvalidField("dateOfBirth", "1955-05-20T00:00:00", "TYPE_MISMATCH"),
                new InvalidField("phoneNumber", "3001234567", "VALIDATION_FAILED"),
                new InvalidField("password", "short", "VALIDATION_FAILED"),
                new InvalidField("password", "               ", "VALIDATION_FAILED"),
                new InvalidField("password", "films+pic+galeries", "VALIDATION_FAILED"),
                new InvalidField("password", null, "VALIDATION_FAILED"),
                new InvalidField("email", 123456, "TYPE_MISMATCH"));
    }

    @ParameterizedTest
    @MethodSource("forbiddenProperties")
    void refusesClientSuppliedPrivilegesAndUnknownProperties(String property) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(property, "ADMINISTRATOR");
        MvcResult result = register(request, 400);
        assertProblem(result, "UNKNOWN_PROPERTY");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(property, "ADMINISTRATOR");
        assertAccountCounts(0);
    }

    static Stream<String> forbiddenProperties() {
        return Stream.of("role", "active", "UNKNOWN_PRIVATE_INPUT");
    }

    @Test
    void retainsMvcProtocolErrorsThroughTheSecurityFilterChain() throws Exception {
        mockMvc.perform(get(REGISTRATION_PATH)).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post(REGISTRATION_PATH).contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(post(REGISTRATION_PATH).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN).content(jsonMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("NOT_ACCEPTABLE"));
    }

    @Test
    void usesTheSameErrorContractForSecurityFailuresBeforeMvc() throws Exception {
        mockMvc.perform(get("/api/v1/private-test-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/private-test-resource").with(user("test-owner").roles("OWNER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void generatesTheSignupContractFromTheControllerAndDtos() throws Exception {
        MvcResult result = mockMvc.perform(get("http://localhost:8080/v3/api-docs"))
                .andExpect(status().isOk()).andReturn();
        JsonNode specification = jsonMapper.readTree(result.getResponse().getContentAsString());
        JsonNode operation = specification.path("paths").path(REGISTRATION_PATH).path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo("signupOwner");
        assertThat(specification.path("paths").properties()).hasSize(1);
        assertThat(operation.path("responses").propertyNames()).contains("201", "400", "409", "415", "500");
        JsonNode requestSchema = specification.path("components").path("schemas").path("UserSignupRequestDTO");
        assertThat(requestSchema.path("additionalProperties").asBoolean(true)).isFalse();
        List<String> required = requestSchema.path("required").valueStream().map(JsonNode::asText).toList();
        assertThat(required).containsExactlyInAnyOrder("email", "fullName", "dateOfBirth", "phoneNumber", "password");
        assertThat(requestSchema.path("properties").path("email").path("maxLength").asInt()).isEqualTo(254);
        assertThat(requestSchema.path("properties").path("dateOfBirth").path("format").asText()).isEqualTo("date");
        JsonNode password = requestSchema.path("properties").path("password");
        assertThat(password.path("writeOnly").asBoolean()).isTrue();
        assertThat(password.path("minLength").asInt()).isEqualTo(15);
        assertThat(password.path("maxLength").asInt()).isEqualTo(128);
        JsonNode responseSchema = specification.path("components").path("schemas").path("UserSignupResponseDTO");
        assertThat(responseSchema.path("properties").propertyNames()).containsExactlyInAnyOrder("id", "email");
        Path directory = Path.of("target", "generated-openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(specification));
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("email", email);
        request.put("fullName", "María Gómez");
        request.put("dateOfBirth", "1955-05-20");
        request.put("phoneNumber", "+573001234567");
        request.put("password", TEST_PASSWORD);
        return request;
    }

    private MvcResult register(Map<String, Object> request, int expectedStatus) throws Exception {
        return mockMvc.perform(post(REGISTRATION_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private void assertProblem(MvcResult result, String errorCode) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode problem = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(problem.path("status").asInt()).isEqualTo(result.getResponse().getStatus());
        assertThat(problem.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(problem.path("instance").asText()).startsWith("urn:uuid:");
        assertThat(problem.path("fieldErrors").isArray()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(email, TEST_PASSWORD, "password_hash", "stackTrace");
    }

    private void assertAccountCounts(int expected) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM owner_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = ?",
                Integer.class, email)).isEqualTo(expected);
    }

    private void saveResponseExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }

    private String safeExceptionMetadata(MvcResult result) {
        StringBuilder metadata = new StringBuilder();
        Throwable cause = result.getResolvedException();
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            metadata.append(cause.getClass().getSimpleName());
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                metadata.append("[state=").append(violation.getSQLState())
                        .append(",constraint=").append(violation.getConstraintName()).append("]");
            }
            metadata.append(" ");
        }
        return metadata.toString();
    }

    private record InvalidField(String field, Object value, String errorCode) { }
}
