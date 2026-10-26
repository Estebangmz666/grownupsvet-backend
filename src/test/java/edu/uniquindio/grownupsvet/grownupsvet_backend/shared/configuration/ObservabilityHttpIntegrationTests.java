package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.CurrentAccountJwtValidator;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.UserSignupService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.CommittedBusinessOperationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real sockets and PostgreSQL exercise both web servers and the actual JWT boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles({"test", "observability"})
@Import(TestJwtKeyConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObservabilityHttpIntegrationTests {
    @Value("${local.server.port}") private int applicationPort;
    @Value("${local.management.port}") private int managementPort;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService tokenService;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserSignupService userSignup;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoSpyBean private CurrentAccountJwtValidator accountJwtValidator;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void scrapeAndMinimalProbesUseRealSeparatePortsWithoutPersonAuthentication() throws Exception {
        assertThat(managementPort).isPositive().isNotEqualTo(applicationPort);
        clearInvocations(accountJwtValidator);
        HttpResponse<String> scrape = request(managementPort, "/actuator/prometheus", "GET", null);
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.headers().firstValue("content-type").orElseThrow()).contains("text/plain");
        assertThat(scrape.body()).contains("jvm_memory_used_bytes", "application=\"grownupsvet-backend\"", "revision=\"");
        for (String path : new String[]{"/livez", "/readyz"}) {
            HttpResponse<String> probe = request(applicationPort, path, "GET", null);
            assertThat(probe.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(probe.body()).properties()).hasSize(1);
            assertThat(jsonMapper.readTree(probe.body()).path("status").asText()).isEqualTo("UP");
        }
        verifyNoInteractions(accountJwtValidator);
    }

    @Test
    void managementServerDeniesOtherEndpointsAndMethodsAndDoesNotExposeTheApi() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/health", "/actuator/env", "/actuator/metrics",
                "/v3/api-docs", "/api/v1/users/me"}) {
            assertThat(request(managementPort, path, "GET", null).statusCode()).isIn(401, 403);
        }
        for (String method : new String[]{"POST", "HEAD", "OPTIONS"}) {
            assertThat(request(managementPort, "/actuator/prometheus", method, null).statusCode()).isIn(401, 403);
        }
        assertThat(request(applicationPort, "/api/v1/users/me", "GET", null).statusCode()).isEqualTo(401);
        assertThat(request(applicationPort, "/livez/private", "GET", null).statusCode()).isEqualTo(401);
        assertThat(request(applicationPort, "/livez", "POST", null).statusCode()).isEqualTo(401);
    }

    @Test
    void publicPortNeverReturnsMetricsEvenForAuthenticatedApplicationRoles() throws Exception {
        assertThat(request(applicationPort, "/actuator/prometheus", "GET", null).statusCode()).isEqualTo(401);
        for (UserRole role : new UserRole[]{UserRole.OWNER, UserRole.VETERINARIAN, UserRole.ADMINISTRATOR}) {
            User user = account(role);
            try {
                String token = tokenService.issue(user, permissions.resolve(role)).accessToken();
                HttpResponse<String> response = request(applicationPort, "/actuator/prometheus", "GET", token);
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.body()).doesNotContain("jvm_memory", "http_server_requests");
                assertThat(request(applicationPort, "/actuator/prometheus/extra", "GET", token).statusCode()).isEqualTo(403);
            } finally {
                users.deleteById(user.getId());
                users.flush();
            }
        }
    }

    @Test
    void realApiTrafficPublishesAnAggregableHistogramWithoutRequestValues() throws Exception {
        User user = account(UserRole.OWNER);
        UUID privatePetId = UUID.randomUUID();
        String privateQueryValue = "private-query-" + UUID.randomUUID();
        try {
            String token = tokenService.issue(user, permissions.resolve(user.getRole())).accessToken();
            HttpResponse<String> api = request(applicationPort, "/api/v1/pets/" + privatePetId + "?note=" + privateQueryValue, "GET", token);
            assertThat(api.statusCode()).isEqualTo(404);
            String scrape = request(managementPort, "/actuator/prometheus", "GET", null).body();
            assertThat(scrape).contains("http_server_requests_seconds_bucket", "uri=\"/api/v1/pets/{petId}\"");
            assertThat(Pattern.compile("http_server_requests_seconds_bucket\\{[^\\n]*le=\"0\\.5\"[^\\n]*}").matcher(scrape).find()).isTrue();
            assertThat(scrape).doesNotContain(user.getEmail(), user.getId().toString(), privatePetId.toString(), privateQueryValue, token);
        } finally {
            users.deleteById(user.getId());
            users.flush();
        }
    }

    @Test
    void scrapeExposesCommittedBusinessOperationsWithoutResourceOrClinicalValues() throws Exception {
        double before = meterRegistry.get(CommittedBusinessOperationMetrics.METRIC_NAME)
                .tag("operation", BusinessOperation.OWNER_SIGNUP.metricTag()).counter().count();
        String privateEmail = "committed-observability-" + UUID.randomUUID() + "@example.test";
        var registered = userSignup.signup(new UserSignupRequestDTO(privateEmail, "María Gómez",
                LocalDate.of(1955, 5, 20), "+573001234567", "Las mascotas ficticias visitan parques tranquilos"));
        try {
            assertThat(meterRegistry.get(CommittedBusinessOperationMetrics.METRIC_NAME)
                    .tag("operation", BusinessOperation.OWNER_SIGNUP.metricTag()).counter().count()).isEqualTo(before + 1);
            HttpResponse<String> scrape = request(managementPort, "/actuator/prometheus", "GET", null);
            assertThat(scrape.statusCode()).isEqualTo(200);
            assertThat(scrape.body()).contains("grownupsvet_business_commits_total");
            for (BusinessOperation operation : BusinessOperation.values()) {
                assertThat(scrape.body()).contains("operation=\"" + operation.metricTag() + "\"");
            }
            assertThat(scrape.body()).doesNotContain(privateEmail, registered.id().toString(), "María Gómez", "+573001234567");
        } finally {
            owners.deleteById(registered.id());
            owners.flush();
            users.deleteById(registered.id());
            users.flush();
        }
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("observability+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Fixture de observabilidad con acceso privado"), role));
    }

    private HttpResponse<String> request(int port, String path, String method, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
