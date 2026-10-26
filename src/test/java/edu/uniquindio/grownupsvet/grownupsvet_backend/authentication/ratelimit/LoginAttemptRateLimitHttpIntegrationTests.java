package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration.LoginAttemptRateLimitProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.exception.LoginAttemptRateLimitException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.repository.LoginAttemptRateLimitRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.service.LoginAttemptRateLimitService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL commits and HTTP validation; every fixture belongs to this UUID schema. */
@SpringBootTest(properties = {
        "grownupsvet.security.login-rate-limit.enabled=true",
        "grownupsvet.security.login-rate-limit.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=^$"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, LoginAttemptRateLimitHttpIntegrationTests.ClockConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginAttemptRateLimitHttpIntegrationTests {
    private static final String SCHEMA = "login_admission_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant INITIAL_TIME = Instant.parse("2026-10-26T15:00:00Z");
    private static final String PASSWORD = "Una frase privada para acceso de pruebas";
    private static final String PEER = "192.0.2.10";
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @MockitoSpyBean private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptRateLimitService admission;
    @Autowired private LoginAttemptRateLimitRepository counters;
    @Autowired private LoginAttemptRateLimitProperties properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MutableLoginClock clock;
    private String email;

    @DynamicPropertySource
    static void isolateFixtures(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @BeforeEach
    void resetOnlyThisSchemaCounters() {
        clock.set(INITIAL_TIME);
        jdbc.update("DELETE FROM login_attempt_rate_limits");
        email = "login-admission+" + UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), UserRole.OWNER));
        clearInvocations(passwordEncoder);
    }

    @AfterAll
    void removeOnlyOwnedSchema() {
        if (!SCHEMA.matches("login_admission_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected schema."); }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void tenNormalizedIdentifierPeerAdmissionsIncludeSuccessAndFailureWithoutDisablingTheAccount() throws Exception {
        login(email, PASSWORD, PEER, 200);
        for (int attempt = 1; attempt < 10; attempt++) { login(email.toUpperCase(), "Contraseña incorrecta", PEER, 401); }
        MvcResult rejected = login(email, PASSWORD, PEER, 429);
        assertRateLimited(rejected, 900);
        assertThat(users.findByEmail(email).orElseThrow().isActive()).isTrue();
        assertThat(jdbc.queryForList("SELECT used_count FROM login_attempt_rate_limits", Integer.class))
                .containsExactlyInAnyOrder(10, 10);
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("login-rate-limited.json"), rejected.getResponse().getContentAsString());
    }

    @Test
    void knownUnknownAndInactiveAccountsReceiveTheSameAdmissionDecisionAndGenericProblem() throws Exception {
        User inactive = users.saveAndFlush(new User("inactive+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(PASSWORD), UserRole.OWNER));
        inactive.deactivate();
        users.saveAndFlush(inactive);
        List<String> identifiers = List.of(email, "unknown+" + UUID.randomUUID() + "@example.com", inactive.getEmail());
        List<String> details = new ArrayList<>();
        for (int index = 0; index < identifiers.size(); index++) {
            String peer = "192.0.2." + (30 + index);
            for (int attempt = 0; attempt < 10; attempt++) { login(identifiers.get(index), "Incorrecta", peer, 401); }
            MvcResult rejected = login(identifiers.get(index), PASSWORD, peer, 429);
            assertRateLimited(rejected, 900);
            details.add(mapper.readTree(rejected.getResponse().getContentAsString()).path("detail").asText());
        }
        assertThat(details).containsOnly(details.getFirst());
        assertThat(users.findById(inactive.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void rejectedRequestsDoNotExtendTheWindowAndRetryAfterRoundsUp() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) { admission.admit(email, PEER); }
        assertRateLimited(login(email, PASSWORD, PEER, 429), 900);
        clock.set(INITIAL_TIME.plusMillis(899500));
        assertRateLimited(login(email, PASSWORD, PEER, 429), 1);
        clock.set(INITIAL_TIME.plusSeconds(900));
        login(email, PASSWORD, PEER, 200);
    }

    @Test
    void originCapacityIsSharedAcrossIdentifiersAndExpiresWithoutCreatingUnlimitedRejectedPairs() throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            admission.admit("unknown-" + attempt + "@example.com", PEER);
        }
        assertRateLimited(login(email, PASSWORD, PEER, 429), 60);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempt_rate_limits", Integer.class)).isEqualTo(61);
        login(email, PASSWORD, "192.0.2.11", 200);
        clock.set(INITIAL_TIME.plusSeconds(60));
        login(email, PASSWORD, PEER, 200);
    }

    @Test
    void spoofedForwardingHeadersCannotChooseAnotherAdmissionBucket() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) { admission.admit(email, PEER); }
        MvcResult result = mvc.perform(post("/api/v1/auth/sessions")
                        .with(request -> { request.setRemoteAddr(PEER); return request; })
                        .header("X-Forwarded-For", "198.51.100.42")
                        .header("CF-Connecting-IP", "198.51.100.43")
                        .header("Forwarded", "for=198.51.100.44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests()).andReturn();
        assertRateLimited(result, 900);
    }

    @Test
    void concurrentServiceInstancesShareExactlyTenAdmissions() throws Exception {
        LoginAttemptRateLimitService secondInstance = new LoginAttemptRateLimitService(properties, counters, clock, transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (int index = 0; index < 24; index++) {
                LoginAttemptRateLimitService instance = index % 2 == 0 ? admission : secondInstance;
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try { instance.admit(email, PEER); return true; }
                    catch (LoginAttemptRateLimitException expected) { return false; }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : futures) { if (result.get(15, TimeUnit.SECONDS)) { accepted++; } }
            assertThat(accepted).isEqualTo(10);
        }
        assertThat(jdbc.queryForList("SELECT used_count FROM login_attempt_rate_limits", Integer.class))
                .containsExactlyInAnyOrder(10, 10);
    }

    @Test
    void admissionSurvivesAnEnclosingRollbackAndCountersContainOnlyHmacKeys() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            admission.admit(email, PEER);
            throw new IllegalStateException("Controlled caller rollback.");
        })).isInstanceOf(IllegalStateException.class);
        List<String> keys = jdbc.queryForList("SELECT bucket_key FROM login_attempt_rate_limits", String.class);
        assertThat(keys).hasSize(2).allSatisfy(key -> assertThat(key).matches("[a-f0-9]{64}").doesNotContain(email, PEER));
        assertThat(jdbc.queryForList("SELECT used_count FROM login_attempt_rate_limits", Integer.class))
                .containsExactlyInAnyOrder(1, 1);
    }

    @Test
    void cleanupRemovesExpiredCountersAndPreservesTheLiveIdentifierWindow() {
        admission.admit(email, PEER);
        clock.set(INITIAL_TIME.plusSeconds(60));
        admission.removeExpiredCounters();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempt_rate_limits", Integer.class)).isEqualTo(1);
        clock.set(INITIAL_TIME.plusSeconds(900));
        admission.removeExpiredCounters();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempt_rate_limits", Integer.class)).isZero();
    }

    @Test
    void exhaustedAdmissionDoesNotRunArgon2Verification() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) { admission.admit(email, PEER); }
        login(email, PASSWORD, PEER, 429);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void disabledPolicyDoesNotUseThePeerSecretOrDatabase() {
        LoginAttemptRateLimitProperties disabled = new LoginAttemptRateLimitProperties(false, "", "PEER",
                10, Duration.ofMinutes(15), 60, Duration.ofMinutes(1));
        new LoginAttemptRateLimitService(disabled, counters, clock, transactionManager).admit(email, "not-an-address");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempt_rate_limits", Integer.class)).isZero();
    }

    private MvcResult login(String identifier, String password, String peer, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/v1/auth/sessions")
                        .with(request -> { request.setRemoteAddr(peer); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", identifier, "password", password))))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private void assertRateLimited(MvcResult result, long retryAfter) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(Long.toString(retryAfter));
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("errorCode").asText())
                .isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(email, PEER, PASSWORD);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary MutableLoginClock loginClock() { return new MutableLoginClock(); }
    }

    static class MutableLoginClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
