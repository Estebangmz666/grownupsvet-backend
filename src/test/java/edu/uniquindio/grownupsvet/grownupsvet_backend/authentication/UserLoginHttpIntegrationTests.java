package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, UserLoginHttpIntegrationTests.AuthenticatedTestController.class})
class UserLoginHttpIntegrationTests {
    private static final String SESSIONS_PATH = UserSessionController.SESSIONS_PATH;
    private static final String RAW_PASSWORD = "  Una frase privada de acceso 🌳  ";
    private static final String AUTHENTICATED_TEST_PATH = "/api/v1/test-authentication";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;

    private String email;
    private User user;

    @BeforeEach
    void createAnIsolatedAccount() {
        email = "login-test+" + UUID.randomUUID() + "@example.com";
        user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(RAW_PASSWORD), UserRole.OWNER));
    }

    @AfterEach
    void removeOnlyThisTestsAccount() {
        userRepository.deleteAll(userRepository.findByEmail(email).stream().toList());
        userRepository.flush();
    }

    @Test
    void createsAStatelessSessionWithAnRs256TokenAndApprovedClaims() throws Exception {
        MvcResult result = login(email.toUpperCase(), RAW_PASSWORD, 200);
        result.getResponse().getHeaderNames().forEach(name -> {
            if (HttpHeaders.SET_COOKIE.equalsIgnoreCase(name)) {
                assertThat(result.getResponse().getHeaders(name)).isEmpty();
            }
        });
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.properties()).hasSize(4);
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.path("expiresIn").asLong()).isEqualTo(86400);
        assertThat(response.path("user").path("id").asText()).isEqualTo(user.getId().toString());
        assertThat(response.path("user").path("email").asText()).isEqualTo(email);
        assertThat(response.path("user").path("role").asText()).isEqualTo("OWNER");
        assertThat(response.path("user").path("permissions").valueStream().map(JsonNode::asText).toList())
                .containsExactly("PROFILE_READ_SELF", "PROFILE_UPDATE_SELF",
                        "PROFILE_DEACTIVATE_SELF", "PROFILE_PHOTO_READ_SELF", "PROFILE_PHOTO_UPDATE_SELF");

        Jwt jwt = jwtDecoder.decode(response.path("accessToken").asText());
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256")
                .containsEntry("kid", "grownupsvet-jwt-signing-2026-09")
                .containsEntry("typ", "JWT");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(jwt.getAudience()).containsExactly("grownupsvet-clients");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("grownupsvet-backend");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(24));
        saveResponseExample("login-success.json", result);
    }

    @Test
    void preservesPasswordSpacesAndRejectsTheTrimmedAlternative() throws Exception {
        login(email, RAW_PASSWORD, 200);
        MvcResult result = login(email, RAW_PASSWORD.strip(), 401);
        assertInvalidCredentials(result, RAW_PASSWORD.strip());
    }

    @Test
    void doesNotRevealWhetherEmailPasswordOrAccountStateFailed() throws Exception {
        MvcResult wrongPassword = login(email, "Una contraseña deliberadamente incorrecta", 401);
        MvcResult unknownEmail = login("unknown+" + UUID.randomUUID() + "@example.com", RAW_PASSWORD, 401);
        user.deactivate();
        userRepository.saveAndFlush(user);
        MvcResult inactiveAccount = login(email, RAW_PASSWORD, 401);

        assertInvalidCredentials(wrongPassword, "Una contraseña deliberadamente incorrecta");
        assertInvalidCredentials(unknownEmail, RAW_PASSWORD);
        assertInvalidCredentials(inactiveAccount, RAW_PASSWORD);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualToIgnoringWhitespace(unknownEmail.getResponse().getContentAsString()
                        .replaceAll("urn:uuid:[0-9a-f-]+", extractInstance(wrongPassword)));
        saveResponseExample("login-invalid-credentials.json", inactiveAccount);
    }

    @Test
    void acceptsTheIssuedBearerTokenAndRejectsAModifiedSignature() throws Exception {
        JsonNode login = jsonMapper.readTree(login(email, RAW_PASSWORD, 200).getResponse().getContentAsString());
        String token = login.path("accessToken").asText();
        mockMvc.perform(get(AUTHENTICATED_TEST_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(user.getId().toString()))
                .andExpect(jsonPath("$.authorities", hasItem("PROFILE_READ_SELF")));

        int signatureStart = token.lastIndexOf('.') + 1;
        char originalSignatureCharacter = token.charAt(signatureStart);
        char replacement = originalSignatureCharacter == 'A' ? 'B' : 'A';
        String modifiedToken = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);
        mockMvc.perform(get(AUTHENTICATED_TEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + modifiedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void generatesTheLoginContractAndBearerScheme() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode specification = jsonMapper.readTree(result.getResponse().getContentAsString());
        JsonNode operation = specification.path("paths").path(SESSIONS_PATH).path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo("createUserSession");
        assertThat(operation.path("responses").propertyNames()).contains("200", "400", "401", "415", "500");
        assertThat(specification.path("components").path("securitySchemes").path("bearerAuth")
                .path("scheme").asText()).isEqualTo("bearer");
        JsonNode responseSchema = specification.path("components").path("schemas").path("UserLoginResponseDTO");
        assertThat(responseSchema.path("required").valueStream().map(JsonNode::asText).toList())
                .containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn", "user");
        assertThat(responseSchema.path("properties").path("expiresIn").path("type").asText())
                .isEqualTo("integer");
        Path directory = Path.of("target", "generated-openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(specification));
    }

    private MvcResult login(String requestedEmail, String requestedPassword, int expectedStatus) throws Exception {
        return mockMvc.perform(post(SESSIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of(
                                "email", requestedEmail, "password", requestedPassword))))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private void assertInvalidCredentials(MvcResult result, String privatePassword) throws Exception {
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode problem = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(problem.path("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(problem.path("detail").asText()).isEqualTo("El correo o la contraseña no son correctos.");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(email, privatePassword, "inactive");
    }

    private String extractInstance(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("instance").asText();
    }

    private void saveResponseExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }

    @RestController
    static class AuthenticatedTestController {
        @GetMapping(AUTHENTICATED_TEST_PATH)
        Map<String, Object> authenticated(Authentication authentication) {
            return Map.of("subject", authentication.getName(), "authorities",
                    authentication.getAuthorities().stream().map(Object::toString).toList());
        }
    }
}
