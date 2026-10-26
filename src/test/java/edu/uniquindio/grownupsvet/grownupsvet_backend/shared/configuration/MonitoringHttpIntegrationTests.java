package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.CurrentAccountJwtValidator;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.MonitoringTokenAuthenticationFilter;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real HTTP and PostgreSQL verify that machine scraping never enters the human JWT account validator. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "monitoring"})
@Import(TestJwtKeyConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MonitoringHttpIntegrationTests {
    private static final String FIXTURE_TOKEN = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String FIXTURE_HASH = hash(FIXTURE_TOKEN);
    @Value("${local.server.port}") private int applicationPort;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private JsonMapper mapper;
    @MockitoSpyBean private CurrentAccountJwtValidator accountJwtValidator;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void machineMonitoringFixture(DynamicPropertyRegistry properties) {
        properties.add("grownupsvet.monitoring.token-sha256", () -> FIXTURE_HASH);
    }

    @Test
    void machineTokenScrapesTheApplicationPortWithoutHumanAuthenticationOrGlobalFilterRegistration() throws Exception {
        clearInvocations(accountJwtValidator);
        assertThat(request("/api/v1/hello", "GET", null).statusCode()).isEqualTo(200);
        HttpResponse<String> scrape = request("/actuator/prometheus", "GET", FIXTURE_TOKEN);
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.headers().firstValue("content-type").orElseThrow()).contains("text/plain");
        assertThat(scrape.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(scrape.body()).contains("jvm_memory_used_bytes", "application=\"grownupsvet-backend\"", "revision=\"");
        assertThat(Pattern.compile("http_server_requests_seconds_bucket\\{[^\\n]*le=\"0\\.5\"[^\\n]*}").matcher(scrape.body()).find()).isTrue();
        assertThat(scrape.body()).doesNotContain(FIXTURE_TOKEN, FIXTURE_HASH);
        assertThat(applicationContext.getBeansOfType(MonitoringTokenAuthenticationFilter.class)).isEmpty();
        verifyNoInteractions(accountJwtValidator);
    }

    @Test
    void missingWrongAndDigestAsTokenAreRejectedWithoutInvokingTheAccountValidator() throws Exception {
        clearInvocations(accountJwtValidator);
        for (String candidate : new String[]{null, "A".repeat(43), FIXTURE_HASH, "fixture.invalid.jwt"}) {
            HttpResponse<String> response = request("/actuator/prometheus", "GET", candidate);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
            assertThat(response.body()).doesNotContain("jvm_memory", FIXTURE_TOKEN, FIXTURE_HASH);
        }
        verifyNoInteractions(accountJwtValidator);
    }

    @Test
    void humanJwtOfEveryRoleIsRejectedByTheMachineChainWithoutDatabaseAccountValidation() throws Exception {
        for (UserRole role : UserRole.values()) {
            User user = account(role);
            try {
                String token = tokens.issue(user, permissions.resolve(role)).accessToken();
                clearInvocations(accountJwtValidator);
                HttpResponse<String> response = request("/actuator/prometheus", "GET", token);
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).doesNotContain(token, user.getEmail(), "jvm_memory", FIXTURE_HASH);
                verifyNoInteractions(accountJwtValidator);
            } finally { users.deleteById(user.getId()); users.flush(); }
        }
    }

    @Test
    void onlyTheExactGetIsAllowedAndTheMachineTokenCannotAuthorizeOtherApiRoutes() throws Exception {
        clearInvocations(accountJwtValidator);
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}) {
            assertThat(request("/actuator/prometheus", method, FIXTURE_TOKEN).statusCode()).isEqualTo(401);
        }
        for (String path : new String[]{"/actuator/prometheus/", "/actuator/prometheus/extra", "/api/v1/users/me", "/actuator/env"}) {
            assertThat(request(path, "GET", FIXTURE_TOKEN).statusCode()).isEqualTo(401);
        }
        verifyNoInteractions(accountJwtValidator);
    }

    @Test
    void existingProbesStayPublicAndMinimalWithoutAdditionalProbePaths() throws Exception {
        clearInvocations(accountJwtValidator);
        for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> response = request(path, "GET", null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).properties()).hasSize(1);
            assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("UP");
        }
        assertThat(request("/livez", "GET", null).statusCode()).isEqualTo(401);
        assertThat(request("/readyz", "GET", null).statusCode()).isEqualTo(401);
        verifyNoInteractions(accountJwtValidator);
    }

    @Test
    void privateRequestValuesNeverBecomeHistogramLabels() throws Exception {
        User owner = account(UserRole.OWNER);
        UUID privatePetId = UUID.randomUUID();
        String privateQuery = "private-monitoring-query-" + UUID.randomUUID();
        try {
            String token = tokens.issue(owner, permissions.resolve(owner.getRole())).accessToken();
            assertThat(request("/api/v1/pets/" + privatePetId + "?note=" + privateQuery, "GET", token).statusCode()).isEqualTo(404);
            clearInvocations(accountJwtValidator);
            HttpResponse<String> scrape = request("/actuator/prometheus", "GET", FIXTURE_TOKEN);
            assertThat(scrape.statusCode()).isEqualTo(200);
            assertThat(scrape.body()).contains("uri=\"/api/v1/pets/{petId}\"");
            assertThat(scrape.body()).doesNotContain(owner.getEmail(), owner.getId().toString(), privatePetId.toString(), privateQuery, token,
                    FIXTURE_TOKEN, FIXTURE_HASH);
            verifyNoInteractions(accountJwtValidator);
        } finally { users.deleteById(owner.getId()); users.flush(); }
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("monitoring+" + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }

    private HttpResponse<String> request(String path, String method, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + applicationPort + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException("Fixture SHA-256 is unavailable."); }
    }
}
