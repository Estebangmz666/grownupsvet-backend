package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email.PasswordRecoveryEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoveryMailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoverySecrets;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.controller.UserPasswordRecoveryController.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "grownupsvet.security.password-recovery.enabled=true",
        "grownupsvet.security.password-recovery.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "grownupsvet.security.password-recovery.sender-address=no-reply@example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, PasswordRecoveryHttpIntegrationTests.ControlledClockConfiguration.class})
class PasswordRecoveryHttpIntegrationTests {
    private static final String PASSWORD = "Una contraseña privada anterior";
    private static final String NEW_PASSWORD = "Otra frase extensa para mi cuenta";
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private RevokedAccessTokenRepository revokedTokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PasswordRecoverySecrets secrets;
    @Autowired private PasswordRecoveryRepository recoveries;
    @Autowired private PasswordRecoveryMailDispatcher dispatcher;
    @Autowired private MutableRecoveryClock clock;
    @MockitoBean private PasswordRecoveryEmailSender sender;
    private final BlockingQueue<String> sentCodes = new LinkedBlockingQueue<>();
    private User user;
    private String clientAddress;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-09-10T18:00:00Z"));
        jdbc.update("DELETE FROM password_recovery_rate_limits");
        user = users.saveAndFlush(new User("recovery+" + UUID.randomUUID() + "@example.com",
                encoder.encode(PASSWORD), UserRole.ADMINISTRATOR));
        clientAddress = "192.0.2.42";
        doAnswer(invocation -> { sentCodes.add(invocation.getArgument(2)); return null; })
                .when(sender).sendRecoveryCode(anyString(), any(), anyString());
    }

    @AfterEach
    void cleanUp() throws Exception {
        // Ensure asynchronous tasks finish before Spring resets the mock or the account is removed.
        CountDownLatch drained = new CountDownLatch(2);
        dispatcher.submit(drained::countDown);
        dispatcher.submit(drained::countDown);
        assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
        revokedTokens.deleteForUser(user.getId());
        users.deleteById(user.getId());
        users.flush();
        jdbc.update("DELETE FROM password_recovery_rate_limits");
    }

    @Test
    void recoveryReplacesHashConsumesPermissionAndInvalidatesEveryOldSession() throws Exception {
        String firstSession = login(PASSWORD, 200);
        String secondSession = login(PASSWORD, 200);
        String oldHash = users.findById(user.getId()).orElseThrow().getPasswordHash();
        String code = requestAndReadCode();
        byte[] digest = jdbc.queryForObject("SELECT code_digest FROM password_recovery_challenges WHERE user_id = ?",
                byte[].class, user.getId());
        assertThat(digest).hasSize(32);
        String resetToken = verifyAndReadToken(code);
        assertThat(resetToken).matches("[A-Za-z0-9_-]{43}");
        String persistedToken = jdbc.queryForObject(
                "SELECT reset_token_hash FROM password_recovery_challenges WHERE user_id = ?", String.class, user.getId());
        assertThat(persistedToken).isEqualTo(secrets.resetTokenHash(resetToken)).isNotEqualTo(resetToken);
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(resetToken, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getAuthenticationVersion()).isEqualTo(1);
        assertThat(updated.getPasswordHash()).startsWith("{argon2id}").isNotEqualTo(oldHash);
        assertThat(encoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(encoder.matches(PASSWORD, updated.getPasswordHash())).isFalse();
        assertThat(challengeCount()).isZero();
        for (String session : List.of(firstSession, secondSession)) {
            mvc.perform(get("/api/v1/users/me/profile/photo").header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(resetToken, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(code)))
                .andExpect(status().isBadRequest());
        login(PASSWORD, 401);
        String newSession = login(NEW_PASSWORD, 200);
        mvc.perform(get("/api/v1/users/me/profile/photo").header(HttpHeaders.AUTHORIZATION, "Bearer " + newSession))
                .andExpect(status().isNotFound());
        verify(sender, timeout(5000)).sendPasswordResetNotification(eq(user.getEmail()), any());
    }

    @Test
    void unknownAndInactiveAccountsHaveIdenticalAcceptedResponsesWithoutDelivery() throws Exception {
        String known = mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(sentCodes.poll(5, TimeUnit.SECONDS)).isNotNull();
        String unknown = mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", "unknown+" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        User inactive = users.findById(user.getId()).orElseThrow();
        inactive.deactivate();
        users.saveAndFlush(inactive);
        clock.advance(Duration.ofSeconds(60));
        String disabled = mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(unknown).isEqualTo(known).isEqualTo(disabled);
        verify(sender, timeout(1000).times(1)).sendRecoveryCode(anyString(), any(), anyString());
    }

    @Test
    void wrongAttemptsAreCommittedAndTheFifthDisablesOnlyTheChallenge() throws Exception {
        String correct = requestAndReadCode();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            MvcResult result = mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(wrong)))
                    .andExpect(status().isBadRequest()).andReturn();
            saveExample("recovery-invalid-code.json", result, false);
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM password_recovery_challenges WHERE user_id = ?",
                    Integer.class, user.getId())).isEqualTo(attempt);
        }
        mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(correct)))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isTrue();
        login(PASSWORD, 200);
    }

    @Test
    void aggregateVerificationQuotaSurvivesResendsAndDifferentClientAddresses() throws Exception {
        for (int challenge = 0; challenge < 2; challenge++) {
            String correct = requestAndReadCode();
            String wrong = correct.equals("000000") ? "111111" : "000000";
            for (int attempt = 0; attempt < 5; attempt++) {
                mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(wrong)))
                        .andExpect(status().isBadRequest());
            }
            clock.advance(Duration.ofSeconds(60));
        }
        String third = requestAndReadCode();
        clientAddress = "192.0.2.99";
        mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(third)))
                .andExpect(status().isTooManyRequests());
        assertThat(users.findById(user.getId()).orElseThrow().getAuthenticationVersion()).isZero();
    }

    @Test
    void resendHasCooldownAndInvalidatesPreviouslyIssuedResetPermission() throws Exception {
        String permission = verifyAndReadToken(requestAndReadCode());
        mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail())))
                .andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofSeconds(60));
        String code = requestAndReadCode();
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        assertThat(verifyAndReadToken(code)).isNotEqualTo(permission);
    }

    @Test
    void codeAndResetPermissionExpireAtTheirExactDeadlines() throws Exception {
        String code = requestAndReadCode();
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(code)))
                .andExpect(status().isBadRequest());
        String permission = verifyAndReadToken(requestAndReadCode());
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        assertThat(encoder.matches(PASSWORD, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void oneCodeCannotCreateTwoPermissionsConcurrently() throws Exception {
        String code = requestAndReadCode();
        assertThat(concurrentStatuses(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(code)))
                .containsExactlyInAnyOrder(200, 400);
    }

    @Test
    void onePermissionCannotResetTwiceConcurrently() throws Exception {
        String permission = verifyAndReadToken(requestAndReadCode());
        assertThat(concurrentStatuses(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD)))
                .containsExactlyInAnyOrder(204, 400);
        assertThat(users.findById(user.getId()).orElseThrow().getAuthenticationVersion()).isEqualTo(1);
    }

    @Test
    void passwordPolicyAndConfirmationDoNotConsumeTheValidPermission() throws Exception {
        String permission = verifyAndReadToken(requestAndReadCode());
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, "short", "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, "films+pic+galeries", "films+pic+galeries")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("COMMON_PASSWORD"));
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD + "x")))
                .andExpect(status().isBadRequest());
        String spacedUnicode = "  " + "🐾".repeat(124) + "  ";
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, spacedUnicode, spacedUnicode)))
                .andExpect(status().isNoContent());
        assertThat(encoder.matches(spacedUnicode, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void codeWithLeadingZeroRemainsTextAndNumericJsonIsRejected() throws Exception {
        requestAndReadCode();
        UUID challengeId = jdbc.queryForObject("SELECT challenge_id FROM password_recovery_challenges WHERE user_id = ?",
                UUID.class, user.getId());
        jdbc.update("UPDATE password_recovery_challenges SET code_digest = ? WHERE user_id = ?",
                secrets.codeDigest(challengeId, "000042"), user.getId());
        mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, Map.of("email", user.getEmail(), "code", 42)))
                .andExpect(status().isBadRequest());
        assertThat(verifyAndReadToken("000042")).hasSize(43);
    }

    @Test
    void changedEmailOrInactiveAccountCannotUseOutstandingPermission() throws Exception {
        String permission = verifyAndReadToken(requestAndReadCode());
        User changed = users.findById(user.getId()).orElseThrow();
        changed.changeEmail("updated+" + UUID.randomUUID() + "@example.com");
        users.saveAndFlush(changed);
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        changed = users.findById(user.getId()).orElseThrow();
        changed.changeEmail(user.getEmail());
        changed.deactivate();
        users.saveAndFlush(changed);
        mvc.perform(json(PASSWORD_RESETS_PATH, resetBody(permission, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void resetPermissionDoesNotAuthenticateOrdinaryProtectedRequests() throws Exception {
        String permission = verifyAndReadToken(requestAndReadCode());
        mvc.perform(get("/api/v1/users/me/profile/photo").header(HttpHeaders.AUTHORIZATION, "Bearer " + permission))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPropertiesNeverChangePrivilegesOrRevealSubmittedSecrets() throws Exception {
        String response = mvc.perform(json(PASSWORD_RECOVERIES_PATH,
                        Map.of("email", user.getEmail(), "role", "ADMINISTRATOR", "code", "654321")))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(user.getEmail(), "654321", "ADMINISTRATOR");
        assertThat(challengeCount()).isZero();
    }

    @Test
    void forwardingHeadersDoNotBypassAddressRateLimits() throws Exception {
        for (int request = 0; request < 30; request++) {
            mvc.perform(json(PASSWORD_RECOVERIES_PATH,
                            Map.of("email", "unknown+" + UUID.randomUUID() + "@example.com"))
                            .header("X-Forwarded-For", "198.51.100." + request))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail()))
                        .header("X-Forwarded-For", "198.51.100.200"))
                .andExpect(status().isTooManyRequests());
        assertThat(challengeCount()).isZero();
    }

    @Test
    void smtpFailureKeepsGenericResponseAndRemovesUndeliverableChallenge() throws Exception {
        doThrow(new MailSendException("Private SMTP diagnostics must never reach API"))
                .when(sender).sendRecoveryCode(anyString(), any(), anyString());
        String response = mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        verify(sender, timeout(5000)).sendRecoveryCode(eq(user.getEmail()), any(), anyString());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (challengeCount() != 0 && System.nanoTime() < deadline) { Thread.onSpinWait(); }
        assertThat(challengeCount()).isZero();
        assertThat(response).doesNotContain("SMTP", user.getEmail(), "diagnostics");
        login(PASSWORD, 200);
    }

    @Test
    void cleanupPreservesCurrentRateBucketsAndExpiresOldState() throws Exception {
        requestAndReadCode();
        String key = secrets.rateLimitKey("request-email", user.getEmail());
        recoveries.deleteExpired(clock.instant().minus(Duration.ofHours(2)));
        assertThat(challengeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_count FROM password_recovery_rate_limits WHERE bucket_key = ?",
                Integer.class, key)).isEqualTo(1);
        clock.advance(Duration.ofHours(4));
        recoveries.deleteExpired(clock.instant().minus(Duration.ofHours(2)));
        assertThat(challengeCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_rate_limits WHERE bucket_key = ?",
                Long.class, key)).isZero();
    }

    @Test
    void generatedContractDescribesAllThreeStepsAndTheirSecretFields() throws Exception {
        JsonNode spec = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(spec.path("paths").path(PASSWORD_RECOVERIES_PATH).path("post").path("responses").propertyNames())
                .contains("202", "400", "429", "503");
        assertThat(spec.path("paths").path(PASSWORD_RECOVERY_VERIFICATIONS_PATH).path("post").path("operationId").asText())
                .isEqualTo("verifyUserPasswordRecoveryCode");
        JsonNode schemas = spec.path("components").path("schemas");
        assertThat(schemas.path("UserPasswordRecoveryVerificationRequestDTO").path("properties").path("code")
                .path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("UserPasswordResetRequestDTO").path("properties").path("newPassword")
                .path("writeOnly").asBoolean()).isTrue();
    }

    private List<Integer> concurrentStatuses(String path, Object body) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return mvc.perform(json(path, body)).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return mvc.perform(json(path, body)).andReturn().getResponse().getStatus(); });
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private String requestAndReadCode() throws Exception {
        MvcResult result = mvc.perform(json(PASSWORD_RECOVERIES_PATH, Map.of("email", user.getEmail())))
                .andExpect(status().isAccepted()).andReturn();
        saveExample("recovery-request-success.json", result, false);
        String code = sentCodes.poll(5, TimeUnit.SECONDS);
        assertThat(code).matches("[0-9]{6}");
        return code;
    }

    private String verifyAndReadToken(String code) throws Exception {
        MvcResult result = mvc.perform(json(PASSWORD_RECOVERY_VERIFICATIONS_PATH, verificationBody(code)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(300)).andReturn();
        saveExample("recovery-verification-success.json", result, true);
        return mapper.readTree(result.getResponse().getContentAsString()).path("resetToken").asText();
    }

    private void saveExample(String filename, MvcResult result, boolean redactResetToken) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        ObjectNode response = (ObjectNode) mapper.readTree(result.getResponse().getContentAsString());
        if (redactResetToken) {
            // Keep the real HTTP response structure, replacing only its ephemeral secret with a fictitious value.
            response.put("resetToken", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        }
        Files.writeString(directory.resolve(filename), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    private long challengeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_challenges WHERE user_id = ?", Long.class, user.getId());
    }

    private Map<String, String> verificationBody(String code) { return Map.of("email", user.getEmail(), "code", code); }

    private Map<String, String> resetBody(String token, String password, String confirmation) {
        return Map.of("resetToken", token, "newPassword", password, "confirmNewPassword", confirmation);
    }

    private MockHttpServletRequestBuilder json(String path, Object body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
                .with(request -> { request.setRemoteAddr(clientAddress); return request; });
    }

    private String login(String password, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(json("/api/v1/auth/sessions", Map.of("email", user.getEmail(), "password", password)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? mapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText() : null;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledClockConfiguration {
        @Bean @Primary MutableRecoveryClock recoveryClock() { return new MutableRecoveryClock(); }
    }

    static final class MutableRecoveryClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-10T18:00:00Z"));
        void set(Instant value) { now.set(value); }
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
