package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordResetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email.PasswordRecoveryEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.exception.PasswordRecoveryException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryEmailTaskRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoveryEmailEncryption;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoveryEmailOutbox;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoveryMailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoveryMaintenance;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.PasswordRecoverySecrets;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.UserPasswordRecoveryService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Connection;
import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real committed PostgreSQL outbox/lease tests; all SMTP is simulated. */
@SpringBootTest(properties = {
        "grownupsvet.security.password-recovery.enabled=true",
        "grownupsvet.security.password-recovery.hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "grownupsvet.security.password-recovery.email-encryption-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "grownupsvet.security.password-recovery.sender-address=no-reply@example.test",
        "grownupsvet.security.password-recovery.email-dispatch-delay-millis=3600000",
        "grownupsvet.security.password-recovery.maximum-email-attempts=3",
        "grownupsvet.security.password-recovery.email-queue-capacity=2",
        "grownupsvet.staff.bootstrap.enabled=false",
        "grownupsvet.staff.invitations.enabled=false"
})
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, PasswordRecoveryEmailOutboxIntegrationTests.ControlledClockConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryEmailOutboxIntegrationTests {
    private static final String SCHEMA="recovery_email_"+UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW=Instant.parse("2026-10-09T14:00:00Z");
    private static final String PASSWORD="Una contraseña privada para estas pruebas";
    private static final String NEW_PASSWORD="Otra contraseña extensa para estas pruebas";

    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO "+SCHEMA);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PasswordRecoveryProperties properties;
    @Autowired private PasswordRecoveryEmailTaskRepository emailTasks;
    @Autowired private PasswordRecoveryRepository challenges;
    @Autowired private PasswordRecoveryEmailEncryption encryption;
    @Autowired private PasswordRecoverySecrets secrets;
    @Autowired private PasswordRecoveryEmailOutbox outbox;
    @Autowired private UserPasswordRecoveryService recoveries;
    @Autowired private PasswordRecoveryMaintenance maintenance;
    @Autowired private ObjectProvider<PasswordRecoveryEmailSender> senderProvider;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MutableRecoveryEmailClock clock;
    @MockitoBean private PasswordRecoveryEmailSender sender;
    @MockitoBean private PasswordRecoveryMailDispatcher wakeupDispatcher;
    private final List<PasswordRecoveryMailDispatcher> workers=new ArrayList<>();
    private final List<User> fixtures=new ArrayList<>();
    private User user;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        reset(sender, wakeupDispatcher);
        transactions=new TransactionTemplate(transactionManager);
        user=account();
    }

    @AfterEach
    void cleanup() {
        workers.forEach(PasswordRecoveryMailDispatcher::close);
        workers.clear();
        fixtures.forEach(value -> users.deleteById(value.getId()));
        users.flush(); fixtures.clear();
        jdbc.update("DELETE FROM password_recovery_rate_limits");
    }

    @AfterAll
    void dropOnlyOwnedSchema() {
        if (!SCHEMA.matches("recovery_email_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected test schema."); }
        jdbc.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
    }

    @Test
    void challengeAndEncryptedIntentCommitTogetherAndAnotherDispatcherRecoversThem() {
        UUID taskId=requestTask();
        String code=code(taskId);
        byte[] ciphertext=jdbc.queryForObject("SELECT encrypted_payload FROM password_recovery_email_tasks WHERE id=?", byte[].class, taskId);
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).doesNotContain(code, user.getEmail());
        verify(wakeupDispatcher).emailsQueued(any(PasswordRecoveryEmailsQueuedEvent.class));
        verifyNoInteractions(sender);
        worker().close();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM password_recovery_email_tasks WHERE id=?", String.class, taskId))
                    .isEqualTo("SENDING");
            assertThat(invocation.<String>getArgument(2)).isEqualTo(code);
            return null;
        }).when(sender).sendRecoveryCode(anyString(), any(), anyString());
        worker().dispatchPending();
        assertThat(state(taskId)).isEqualTo("SENT");
        assertPurged(taskId);
        assertThat(recoveries.verifyCode(user.getEmail(), code, "192.0.2.80").resetToken()).hasSize(43);
    }

    @Test
    void rollbackDoesNotPersistChallengeIntentOrAfterCommitWakeup() {
        UUID challengeId=UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            challenges.replaceChallenge(user.getId(), challengeId, user.getEmail(), 0,
                    secrets.codeDigest(challengeId, "000042"), NOW.plusSeconds(600), NOW);
            outbox.enqueueCode(user.getId(), challengeId, 0, user.getEmail(), null, "000042", NOW.plusSeconds(600), NOW);
            assertThat(taskCount()).isEqualTo(1);
            verifyNoInteractions(wakeupDispatcher);
            status.setRollbackOnly();
        });
        assertThat(taskCount()).isZero();
        assertThat(challengeCount()).isZero();
        verifyNoInteractions(wakeupDispatcher, sender);
    }

    @Test
    void firstFailurePreservesTheChallengeAndRetryUsesTheSameEncryptedCode() {
        UUID id=requestTask();
        String code=code(id);
        doThrow(new MailSendException("Simulated private SMTP diagnostics")).when(sender).sendRecoveryCode(anyString(), any(), anyString());
        var worker=worker();
        worker.dispatchPending();
        assertThat(state(id)).isEqualTo("PENDING");
        assertThat(challengeCount()).isEqualTo(1);
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(code(id)).isEqualTo(code);
        reset(sender);
        clock.advance(Duration.ofSeconds(60));
        worker.dispatchPending();
        verify(sender).sendRecoveryCode(user.getEmail(), null, code);
        assertThat(state(id)).isEqualTo("SENT");
        assertThat(attempts(id)).isEqualTo(2);
        assertPurged(id);
    }

    @Test
    void exhaustedFailuresRemoveOnlyTheUndeliverableCurrentChallengeAndPurgeCiphertext() {
        UUID id=requestTask();
        doThrow(new MailSendException("Simulated transport failure")).when(sender).sendRecoveryCode(anyString(), any(), anyString());
        var worker=worker();
        for (int attempt=0; attempt<properties.maximumEmailAttempts(); attempt++) {
            worker.dispatchPending();
            clock.advance(Duration.ofSeconds(60));
        }
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(challengeCount()).isZero();
        assertPurged(id);
        assertThat(users.findById(user.getId()).orElseThrow().getAuthenticationVersion()).isZero();
    }

    @Test
    void expiredOrInactiveRecoveryNeverReachesSmtpAndPurgesItsEncryptedPayload() {
        UUID expired=requestTask();
        clock.advance(Duration.ofMinutes(10));
        worker().dispatchPending();
        assertThat(state(expired)).isEqualTo("SUPERSEDED");
        assertPurged(expired);
        UUID inactive=requestTask();
        user=users.findById(user.getId()).orElseThrow();
        user.deactivate(); users.saveAndFlush(user);
        worker().dispatchPending();
        assertThat(state(inactive)).isEqualTo("SUPERSEDED");
        assertPurged(inactive);
        verifyNoInteractions(sender);
    }

    @Test
    void sharedCapacityReturnsTheSameUnavailableResultBeforeCheckingAnyAccount() {
        fillCapacity();
        User inactive=account(); inactive.deactivate(); users.saveAndFlush(inactive);
        for (String email : List.of(user.getEmail(), "unknown@example.test", inactive.getEmail())) {
            assertThatThrownBy(() -> recoveries.requestCode(email, "192.0.2.88"))
                    .isInstanceOfSatisfying(PasswordRecoveryException.class, failure ->
                            assertThat(failure.getReason()).isEqualTo(PasswordRecoveryException.Reason.UNAVAILABLE));
        }
        assertThat(challengeCount()).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void resetNotificationSmtpFailureCannotUndoThePasswordOrItsAuthenticationVersion() {
        UUID codeTask=requestTask();
        String permission=recoveries.verifyCode(user.getEmail(), code(codeTask), "192.0.2.80").resetToken();
        recoveries.resetPassword(new UserPasswordResetRequestDTO(permission, NEW_PASSWORD, NEW_PASSWORD), "192.0.2.80");
        UUID notification=jdbc.queryForObject("SELECT id FROM password_recovery_email_tasks WHERE user_id=? AND task_type='RESET_NOTIFICATION'", UUID.class, user.getId());
        doThrow(new MailSendException("Simulated reset notice failure")).when(sender).sendPasswordResetNotification(anyString(), any());
        worker().dispatchPending();
        User reset=users.findById(user.getId()).orElseThrow();
        assertThat(reset.getAuthenticationVersion()).isEqualTo(1);
        assertThat(encoder.matches(NEW_PASSWORD, reset.getPasswordHash())).isTrue();
        assertThat(challengeCount()).isZero();
        assertThat(state(notification)).isEqualTo("PENDING");
        assertPurged(codeTask);
    }

    @Test
    void exhaustedCapacityRecordsFailedResetNoticeWithoutRevertingTheReset() {
        UUID codeTask=requestTask();
        String permission=recoveries.verifyCode(user.getEmail(), code(codeTask), "192.0.2.80").resetToken();
        fillCapacity();
        recoveries.resetPassword(new UserPasswordResetRequestDTO(permission, NEW_PASSWORD, NEW_PASSWORD), "192.0.2.80");
        UUID failed=jdbc.queryForObject("SELECT id FROM password_recovery_email_tasks WHERE user_id=? AND task_type='RESET_NOTIFICATION' AND status='FAILED'", UUID.class, user.getId());
        assertThat(users.findById(user.getId()).orElseThrow().getAuthenticationVersion()).isEqualTo(1);
        assertThat(encoder.matches(NEW_PASSWORD, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM password_recovery_email_tasks WHERE id=?", String.class, failed))
                .isEqualTo("DELIVERY_CAPACITY_EXHAUSTED");
        assertPurged(failed);
    }

    @Test
    void obsoleteFailedWorkerCannotDeleteTheChallengeCreatedByAResend() throws Exception {
        UUID oldTask=requestTask();
        CountDownLatch sending=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet()==1) {
                sending.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                throw new MailSendException("Obsolete worker simulated failure");
            }
            return null;
        }).when(sender).sendRecoveryCode(anyString(), any(), anyString());
        var worker=worker();
        try (var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            var old=threads.submit(worker::dispatchPending);
            try {
                assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
                clock.advance(Duration.ofSeconds(60));
                UUID current=requestTask();
                UUID currentChallenge=jdbc.queryForObject("SELECT challenge_id FROM password_recovery_challenges WHERE user_id=?", UUID.class, user.getId());
                release.countDown(); old.get(10, TimeUnit.SECONDS);
                assertThat(state(oldTask)).isEqualTo("SUPERSEDED");
                assertThat(state(current)).isEqualTo("SENT");
                assertThat(jdbc.queryForObject("SELECT challenge_id FROM password_recovery_challenges WHERE user_id=?", UUID.class, user.getId()))
                        .isEqualTo(currentChallenge);
            } finally { release.countDown(); }
        }
    }

    @Test
    void expiredLeaseWorkerCannotOverwriteTheNewClaimantResult() throws Exception {
        UUID id=requestTask();
        CountDownLatch firstSending=new CountDownLatch(1), secondSending=new CountDownLatch(1);
        CountDownLatch releaseFirst=new CountDownLatch(1), releaseSecond=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (calls.incrementAndGet()==1) {
                firstSending.countDown(); assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue(); return null;
            }
            secondSending.countDown(); assertThat(releaseSecond.await(10, TimeUnit.SECONDS)).isTrue();
            throw new MailSendException("Replacement worker simulated failure");
        }).when(sender).sendRecoveryCode(anyString(), any(), anyString());
        var firstWorker=worker(); var secondWorker=worker();
        try (var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=threads.submit(firstWorker::dispatchPending);
            try {
                assertThat(firstSending.await(10, TimeUnit.SECONDS)).isTrue();
                clock.advance(Duration.ofSeconds(properties.emailLeaseSeconds()+1));
                var second=threads.submit(secondWorker::dispatchPending);
                assertThat(secondSending.await(10, TimeUnit.SECONDS)).isTrue();
                UUID token=jdbc.queryForObject("SELECT lease_token FROM password_recovery_email_tasks WHERE id=?", UUID.class, id);
                releaseFirst.countDown(); first.get(10, TimeUnit.SECONDS);
                assertThat(state(id)).isEqualTo("SENDING");
                assertThat(jdbc.queryForObject("SELECT lease_token FROM password_recovery_email_tasks WHERE id=?", UUID.class, id)).isEqualTo(token);
                releaseSecond.countDown(); second.get(10, TimeUnit.SECONDS);
                assertThat(state(id)).isEqualTo("PENDING");
                assertThat(attempts(id)).isEqualTo(2);
                assertThat(challengeCount()).isEqualTo(1);
            } finally { releaseFirst.countDown(); releaseSecond.countDown(); }
        }
    }

    @Test
    void cleanupCommitsExpiredEmailBeforeWaitingOnARecoveryRateBucket() throws Exception {
        UUID expiredTask=requestTask();
        String address="192.0.2.99";
        Instant oldWindow=NOW.minus(Duration.ofHours(3));
        String bucket=secrets.rateLimitKey("request-ip", address);
        jdbc.update("INSERT INTO password_recovery_rate_limits(bucket_key,window_started_at,used_count,last_accepted_at) VALUES (?,?,0,?)",
                bucket, Timestamp.from(oldWindow), Timestamp.from(oldWindow));
        clock.advance(Duration.ofMinutes(21));
        // The trigger pauses cleanup after it acquired the expired task row, without timing guesses.
        jdbc.execute("""
                CREATE FUNCTION recovery_cleanup_test_barrier() RETURNS trigger AS $$
                BEGIN
                    IF NEW.last_error_code='RECOVERY_CODE_EXPIRED' THEN
                        PERFORM pg_advisory_xact_lock(742164640243);
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER recovery_cleanup_test_barrier BEFORE UPDATE ON password_recovery_email_tasks " +
                "FOR EACH ROW EXECUTE FUNCTION recovery_cleanup_test_barrier()");
        try (Connection blocker=dataSource.getConnection(); var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            int blockerId;
            try (var statement=blocker.createStatement(); var rows=statement.executeQuery("SELECT pg_backend_pid()")) {
                rows.next(); blockerId=rows.getInt(1);
            }
            try (var statement=blocker.createStatement()) { statement.execute("SELECT pg_advisory_lock(742164640243)"); }
            try {
                var cleanup=threads.submit(maintenance::removeExpiredState);
                int cleanupBackend=awaitBlockedBy(blockerId);
                var resend=threads.submit(() -> recoveries.requestCode(user.getEmail(), address));
                // Resend now owns the renewed rate bucket and is waiting for cleanup's task lock.
                awaitBlockedBy(cleanupBackend);
                try (var statement=blocker.createStatement()) { statement.execute("SELECT pg_advisory_unlock(742164640243)"); }
                cleanup.get(10, TimeUnit.SECONDS);
                resend.get(10, TimeUnit.SECONDS);
                assertThat(state(expiredTask)).isEqualTo("SUPERSEDED");
                assertPurged(expiredTask);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_email_tasks WHERE user_id=? AND status='PENDING'",
                        Integer.class, user.getId())).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT window_started_at FROM password_recovery_rate_limits WHERE bucket_key=?",
                        Timestamp.class, bucket).toInstant()).isEqualTo(clock.instant());
                assertThat(challengeCount()).isEqualTo(1);
                verifyNoInteractions(sender);
            } finally {
                try (var statement=blocker.createStatement()) { statement.execute("SELECT pg_advisory_unlock(742164640243)"); }
            }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS recovery_cleanup_test_barrier ON password_recovery_email_tasks");
            jdbc.execute("DROP FUNCTION IF EXISTS recovery_cleanup_test_barrier()");
        }
    }

    private int awaitBlockedBy(int blockerId) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        do {
            List<Integer> waiting=jdbc.query("SELECT pid FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))",
                    (row, index) -> row.getInt("pid"), blockerId);
            if (!waiting.isEmpty()) { return waiting.getFirst(); }
            Thread.onSpinWait();
        } while (System.nanoTime()<deadline);
        throw new AssertionError("Expected a coordinated PostgreSQL lock wait.");
    }

    private User account() {
        User account=users.saveAndFlush(new User("recovery-outbox+"+UUID.randomUUID()+"@example.test", encoder.encode(PASSWORD), UserRole.ADMINISTRATOR));
        fixtures.add(account); return account;
    }

    private UUID requestTask() {
        recoveries.requestCode(user.getEmail(), "192.0.2.80");
        return jdbc.queryForObject("SELECT id FROM password_recovery_email_tasks WHERE user_id=? AND task_type='RECOVERY_CODE' AND status='PENDING'", UUID.class, user.getId());
    }

    private String code(UUID taskId) {
        return jdbc.queryForObject("SELECT * FROM password_recovery_email_tasks WHERE id=?", (row, index) ->
                encryption.decrypt(taskId, row.getObject("user_id", UUID.class), row.getObject("challenge_id", UUID.class),
                        row.getString("task_type"), row.getBytes("encrypted_payload"), row.getBytes("payload_nonce")).code(), taskId);
    }

    private void fillCapacity() {
        transactions.executeWithoutResult(status -> {
            for (int count=0; count<properties.emailQueueCapacity(); count++) {
                outbox.enqueueResetNotification(user.getId(), UUID.randomUUID(), 0, user.getEmail(), null, clock.instant(), true);
            }
        });
    }

    private PasswordRecoveryMailDispatcher worker() {
        var worker=new PasswordRecoveryMailDispatcher(properties, emailTasks, challenges, encryption, secrets,
                senderProvider, clock, transactionManager);
        workers.add(worker); return worker;
    }

    private String state(UUID id) { return jdbc.queryForObject("SELECT status FROM password_recovery_email_tasks WHERE id=?", String.class, id); }
    private int attempts(UUID id) { return jdbc.queryForObject("SELECT attempt_count FROM password_recovery_email_tasks WHERE id=?", Integer.class, id); }
    private int taskCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_email_tasks WHERE user_id=?", Integer.class, user.getId()); }
    private int challengeCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_challenges WHERE user_id=?", Integer.class, user.getId()); }
    private void assertPurged(UUID id) {
        assertThat(jdbc.queryForObject("SELECT encrypted_payload IS NULL AND payload_nonce IS NULL AND lease_token IS NULL FROM password_recovery_email_tasks WHERE id=?",
                Boolean.class, id)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledClockConfiguration {
        @Bean @Primary MutableRecoveryEmailClock recoveryEmailClock() { return new MutableRecoveryEmailClock(); }
    }
    static final class MutableRecoveryEmailClock extends Clock {
        private final AtomicReference<Instant> now=new AtomicReference<>(NOW);
        void set(Instant value) { now.set(value); }
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }
}
