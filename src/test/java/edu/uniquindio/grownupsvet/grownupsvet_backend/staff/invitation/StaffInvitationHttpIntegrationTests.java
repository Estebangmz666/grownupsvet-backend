package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto.StaffAccountActivationRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email.StaffInvitationEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationDeliveryService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationMailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationSecrets;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "grownupsvet.staff.invitations.enabled=true",
        "grownupsvet.staff.invitations.activation-url=https://portal.example.invalid/activate-account",
        "grownupsvet.staff.invitations.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "grownupsvet.staff.invitations.sender-address=no-reply@example.test",
        "grownupsvet.staff.invitations.dispatch-delay=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, StaffInvitationHttpIntegrationTests.ControlledClockConfiguration.class})
class StaffInvitationHttpIntegrationTests {
    private static final String PASSWORD = "Una frase privada para personal";
    private static final String ACTIVATE = "/api/v1/auth/account-activations";
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private StaffInvitationService service;
    @Autowired private StaffInvitationDeliveryService delivery;
    @Autowired private StaffInvitationSecrets secrets;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MutableInvitationClock clock;
    @MockitoBean private StaffInvitationEmailSender sender;
    // Deterministic tests invoke the real transactional worker; no background SMTP races.
    @MockitoBean private StaffInvitationMailDispatcher dispatcher;
    private final List<UUID> createdUsers = new ArrayList<>();
    private User administrator;
    private User veterinarian;
    private UUID invitationId;
    private String token;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-09-13T14:00:00Z"));
        administrator = save(new User(email(), encoder.encode(PASSWORD), UserRole.ADMINISTRATOR));
        veterinarian = save(User.pendingActivation(email(), UserRole.VETERINARIAN));
        service.createInvitation(veterinarian, administrator.getId());
        invitationId = latestInvitation(veterinarian.getId());
        token = readToken(invitationId);
    }

    @AfterEach
    void cleanUp() {
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM staff_invitations WHERE user_id = ? OR invited_by = ?", userId, userId);
            jdbc.update("DELETE FROM revoked_access_tokens WHERE user_id = ?", userId);
        }
        users.deleteAllById(createdUsers);
        users.flush();
    }

    @Test
    void reservesTheCredentiallessAccountWithDigestAndEncryptedDurableDelivery() {
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getPasswordHash()).isNull();
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM staff_invitations WHERE id = ?", String.class, invitationId))
                .isEqualTo(secrets.hash(token)).doesNotContain(token);
        assertThat(jdbc.queryForObject("SELECT expires_at - created_at = interval '48 hours' FROM staff_invitations WHERE id = ?",
                Boolean.class, invitationId)).isTrue();
        assertThat(taskStatus(invitationId)).isEqualTo("PENDING");
        assertThat(new StaffAccountActivationRequestDTO(token, PASSWORD, PASSWORD).toString())
                .doesNotContain(token, PASSWORD);
    }

    @Test
    void activatesOnceAndThenAllowsLoginWithTheChosenPassword() throws Exception {
        login(veterinarian, 401);
        activate(token, PASSWORD, PASSWORD).andExpect(status().isNoContent());
        User active = users.findById(veterinarian.getId()).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(active.getPasswordHash()).startsWith("{argon2id}");
        assertThat(active.getAuthenticationVersion()).isEqualTo(1);
        assertThat(encoder.matches(PASSWORD, active.getPasswordHash())).isTrue();
        assertThat(invitationStatus(invitationId)).isEqualTo("CONSUMED");
        assertThat(encryptedToken(invitationId)).isNull();
        login(veterinarian, 200);
        activate(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAFF_INVITATION_INVALID"));
    }

    @Test
    void expiresAtExactlyFortyEightHoursAndNeverMailsAnExpiredLink() throws Exception {
        clock.advance(Duration.ofHours(48));
        activate(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        delivery.deliver(invitationId);
        assertThat(invitationStatus(invitationId)).isEqualTo("EXPIRED");
        assertThat(taskStatus(invitationId)).isEqualTo("EXPIRED");
        assertThat(encryptedToken(invitationId)).isNull();
        verifyNoInteractions(sender);
    }

    @Test
    void cancellationDisablesTheAccountRevokesTheLinkAndErasesTheDeliverySecret() throws Exception {
        String session = login(administrator, 200);
        mvc.perform(delete(invitationPath()).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isNoContent());
        mvc.perform(delete(invitationPath()).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isNoContent());
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(invitationStatus(invitationId)).isEqualTo("CANCELLED");
        assertThat(encryptedToken(invitationId)).isNull();
        activate(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        delivery.deliver(invitationId);
        verifyNoInteractions(sender);
    }

    @Test
    void resendReplacesTheOldLinkAfterCooldownAndCanReinviteACancelledCredentiallessAccount() throws Exception {
        String session = login(administrator, 200);
        mvc.perform(post(invitationPath()).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isTooManyRequests());
        service.cancelInvitation(veterinarian.getId(), administrator.getId());
        clock.advance(Duration.ofSeconds(60));
        String response = mvc.perform(post(invitationPath()).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.deliveryStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(token, "tokenHash", "encryptedToken", "password");
        saveExample("staff-invitation-success.json", response);
        UUID replacementId = latestInvitation(veterinarian.getId());
        String replacementToken = readToken(replacementId);
        assertThat(replacementToken).isNotEqualTo(token);
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        activate(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        activate(replacementToken, PASSWORD, PASSWORD).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(60));
        mvc.perform(post(invitationPath()).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                .andExpect(status().isConflict());
    }

    @Test
    void limitsInvitationsToFivePerRollingTwentyFourHours() {
        for (int index = 0; index < 4; index++) {
            clock.advance(Duration.ofSeconds(60));
            service.resendInvitation(veterinarian.getId(), administrator.getId());
        }
        clock.advance(Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.resendInvitation(veterinarian.getId(), administrator.getId()))
                .isInstanceOfSatisfying(StaffOperationException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(429));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_invitations WHERE user_id = ?", Integer.class,
                veterinarian.getId())).isEqualTo(5);
        clock.advance(Duration.ofHours(24));
        service.resendInvitation(veterinarian.getId(), administrator.getId());
    }

    @Test
    void validatesTheExistingPasswordPolicyAndConfirmationWithoutConsumingTheLink() throws Exception {
        activate(token, "short", "short").andExpect(status().isBadRequest());
        activate(token, PASSWORD, "Otra frase extensa y diferente").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_CONFIRMATION_MISMATCH"));
        String response = mvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("token", token, "password", PASSWORD, "confirmPassword", PASSWORD, "role", "SUPER_ADMIN"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(token, PASSWORD);
        assertThat(invitationStatus(invitationId)).isEqualTo("PENDING");
        activate(token, PASSWORD, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void checksCurrentActorRoleAndStatusAndRejectsOwnerOrSuperAdministratorTargets() {
        User owner = save(new User(email(), encoder.encode(PASSWORD), UserRole.OWNER));
        User pendingAdministrator = save(User.pendingActivation(email(), UserRole.ADMINISTRATOR));
        clock.advance(Duration.ofSeconds(60));
        assertForbidden(() -> service.resendInvitation(owner.getId(), administrator.getId()));
        assertForbidden(() -> service.resendInvitation(pendingAdministrator.getId(), administrator.getId()));
        assertForbidden(() -> service.cancelInvitation(veterinarian.getId(), owner.getId()));
        transactions.executeWithoutResult(status -> users.findByIdForUpdate(administrator.getId()).orElseThrow().deactivate());
        assertForbidden(() -> service.resendInvitation(veterinarian.getId(), administrator.getId()));
        assertThat(invitationStatus(invitationId)).isEqualTo("PENDING");
    }

    @Test
    void onlySuperAdministratorCanInviteAnAdministratorAndItCannotInviteVeterinariansDirectly() {
        User superAdministrator = save(new User(email(), encoder.encode(PASSWORD), UserRole.SUPER_ADMIN));
        User pendingAdministrator = save(User.pendingActivation(email(), UserRole.ADMINISTRATOR));
        service.createInvitation(pendingAdministrator, superAdministrator.getId());
        assertThat(invitationStatus(latestInvitation(pendingAdministrator.getId()))).isEqualTo("PENDING");
        assertForbidden(() -> service.resendInvitation(veterinarian.getId(), superAdministrator.getId()));
        assertForbidden(() -> service.cancelInvitation(superAdministrator.getId(), administrator.getId()));
    }

    @Test
    void failedSmtpRetainsADurableRetryAndSuccessfulRetryErasesTheSecret() {
        doThrow(new MailSendException("private SMTP diagnostics must never escape"))
                .doNothing().when(sender).sendInvitation(anyString(), anyString(), any());
        delivery.deliver(invitationId);
        assertThat(taskStatus(invitationId)).isEqualTo("PENDING");
        assertThat(encryptedToken(invitationId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT last_failure_code FROM staff_invitation_email_tasks WHERE invitation_id = ?",
                String.class, invitationId)).isEqualTo("SMTP_DELIVERY_FAILED");
        delivery.deliver(invitationId);
        verify(sender, times(1)).sendInvitation(anyString(), anyString(), any());
        clock.advance(Duration.ofSeconds(60));
        delivery.deliver(invitationId);
        assertThat(taskStatus(invitationId)).isEqualTo("SENT");
        assertThat(encryptedToken(invitationId)).isNull();
        verify(sender, times(2)).sendInvitation(eq(veterinarian.getEmail()),
                eq("https://portal.example.invalid/activate-account?token=" + token), any());
        delivery.deliver(invitationId);
        verify(sender, times(2)).sendInvitation(anyString(), anyString(), any());
    }

    @Test
    void retryExhaustionRemovesTheTemporarySecretAndLeavesTheAccountPendingForManualResend() {
        doThrow(new MailSendException("private diagnostic")).when(sender).sendInvitation(anyString(), anyString(), any());
        for (int attempt = 0; attempt < 5; attempt++) {
            delivery.deliver(invitationId);
            clock.advance(Duration.ofHours(1));
        }
        assertThat(taskStatus(invitationId)).isEqualTo("FAILED");
        assertThat(encryptedToken(invitationId)).isNull();
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        delivery.deliver(invitationId);
        verify(sender, times(5)).sendInvitation(anyString(), anyString(), any());
    }

    @Test
    void concurrentActivationsConsumeTheInvitationExactlyOnce() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> activateAfterBarrier(ready, start));
            var second = executor.submit(() -> activateAfterBarrier(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 400);
        }
        assertThat(users.findById(veterinarian.getId()).orElseThrow().getAuthenticationVersion()).isEqualTo(1);
    }

    @Test
    void accountInvitationAndDeliveryTaskRollbackTogether() {
        String reservedEmail = email();
        UUID[] rolledBackUser = new UUID[1];
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            User pending = users.saveAndFlush(User.pendingActivation(reservedEmail, UserRole.VETERINARIAN));
            rolledBackUser[0] = pending.getId();
            service.createInvitation(pending, administrator.getId());
            throw new IllegalStateException("intentional test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.existsByEmail(reservedEmail)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_invitations WHERE user_id = ?", Integer.class,
                rolledBackUser[0])).isZero();
    }

    @Test
    void publicActivationAndProtectedInvitationOperationsHaveDistinctSecurityContracts() throws Exception {
        mvc.perform(post(invitationPath())).andExpect(status().isUnauthorized());
        mvc.perform(delete(invitationPath())).andExpect(status().isUnauthorized());
        mvc.perform(get(ACTIVATE)).andExpect(status().isUnauthorized());
        String response = activate("A".repeat(43), PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(PASSWORD, "A".repeat(43), veterinarian.getEmail());
        saveExample("staff-invitation-invalid-error.json", response);
    }

    @Test
    void ownerAndVeterinarianCannotProbeOrLockKnownOrUnknownInvitationTargets() throws Exception {
        for (UserRole role : List.of(UserRole.OWNER, UserRole.VETERINARIAN)) {
            User actor = save(new User(email(), encoder.encode(PASSWORD), role));
            String session = login(actor, 200);
            for (UUID targetId : List.of(veterinarian.getId(), UUID.randomUUID())) {
                String path = "/api/v1/staff/" + targetId + "/invitations";
                mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                        .andExpect(status().isForbidden());
                mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
                        .andExpect(status().isForbidden());
                assertForbidden(() -> service.resendInvitation(targetId, actor.getId()));
                assertForbidden(() -> service.cancelInvitation(targetId, actor.getId()));
            }
        }
    }

    private int activateAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test barrier timed out"); }
        try { service.activateAccount(new StaffAccountActivationRequestDTO(token, PASSWORD, PASSWORD)); return 204; }
        catch (StaffOperationException exception) { return exception.getStatus().value(); }
    }

    private org.springframework.test.web.servlet.ResultActions activate(String token, String password, String confirmation) throws Exception {
        return mvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("token", token, "password", password, "confirmPassword", confirmation))));
    }

    private String login(User user, int expectedStatus) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return expectedStatus == 200 ? mapper.readTree(body).path("accessToken").asText() : "";
    }

    private void saveExample(String filename, String response) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mapper.readTree(response)));
    }

    private User save(User user) { User saved = users.saveAndFlush(user); createdUsers.add(saved.getId()); return saved; }
    private String email() { return "staff-invitation+" + UUID.randomUUID() + "@example.com"; }
    private String invitationPath() { return "/api/v1/staff/" + veterinarian.getId() + "/invitations"; }
    private UUID latestInvitation(UUID userId) {
        return jdbc.queryForObject("SELECT id FROM staff_invitations WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", UUID.class, userId);
    }
    private byte[] encryptedToken(UUID id) {
        return jdbc.queryForObject("SELECT encrypted_token FROM staff_invitation_email_tasks WHERE invitation_id = ?", byte[].class, id);
    }
    private String readToken(UUID id) { return secrets.decrypt(id, encryptedToken(id)); }
    private String invitationStatus(UUID id) { return jdbc.queryForObject("SELECT status FROM staff_invitations WHERE id = ?", String.class, id); }
    private String taskStatus(UUID id) { return jdbc.queryForObject("SELECT status FROM staff_invitation_email_tasks WHERE invitation_id = ?", String.class, id); }
    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(StaffOperationException.class,
                exception -> assertThat(exception.getStatus().value()).isEqualTo(403));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledClockConfiguration {
        @Bean @Primary MutableInvitationClock staffInvitationClock() { return new MutableInvitationClock(); }
    }

    static class MutableInvitationClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T14:00:00Z"));
        void set(Instant instant) { now.set(instant); }
        void advance(Duration duration) { now.updateAndGet(instant -> instant.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
