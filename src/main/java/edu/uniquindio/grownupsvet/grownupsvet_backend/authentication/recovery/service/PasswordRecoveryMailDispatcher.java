package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email.PasswordRecoveryEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailPayload;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailTask;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryEmailTaskRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Durable delivery with brief database leases; SMTP never runs inside a database transaction. */
@Component
public class PasswordRecoveryMailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryMailDispatcher.class);
    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryEmailTaskRepository tasks;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordRecoveryEmailEncryption encryption;
    private final PasswordRecoverySecrets secrets;
    private final ObjectProvider<PasswordRecoveryEmailSender> senders;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final TransactionTemplate withoutTransactions;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().daemon(true)
            .name("password-recovery-mail-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

    public PasswordRecoveryMailDispatcher(PasswordRecoveryProperties properties, PasswordRecoveryEmailTaskRepository tasks,
            PasswordRecoveryRepository recoveries, PasswordRecoveryEmailEncryption encryption, PasswordRecoverySecrets secrets,
            ObjectProvider<PasswordRecoveryEmailSender> senders, Clock clock, PlatformTransactionManager transactionManager) {
        this.properties=properties; this.tasks=tasks; this.recoveries=recoveries; this.encryption=encryption;
        this.secrets=secrets; this.senders=senders; this.clock=clock;
        this.transactions=new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.withoutTransactions=new TransactionTemplate(transactionManager);
        this.withoutTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void emailsQueued(PasswordRecoveryEmailsQueuedEvent event) { wakeWorker(); }

    @Scheduled(fixedDelayString = "${grownupsvet.security.password-recovery.email-dispatch-delay-millis:15000}",
            initialDelayString = "${grownupsvet.security.password-recovery.email-dispatch-delay-millis:15000}")
    public void pollPending() { if (properties.enabled()) { wakeWorker(); } }

    private void wakeWorker() {
        try {
            executor.execute(() -> {
                try { dispatchPending(); }
                catch (RuntimeException failure) {
                    LOGGER.warn("Recovery email queue will be retried: exceptionType={}", failure.getClass().getName());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // A rejected wake-up does not lose an intent; the next poll reads PostgreSQL again.
        }
    }

    public void dispatchPending() {
        if (!properties.enabled() || senders.getIfAvailable() == null) { return; }
        for (int count=0; count<50; count++) {
            PasswordRecoveryEmailTask task=claimNext();
            if (task==null) { return; }
            deliver(task);
        }
    }

    private PasswordRecoveryEmailTask claimNext() {
        return transactions.execute(transaction -> {
            Instant now=clock.instant();
            for (var candidate : tasks.candidates(now)) {
                // Account precedes task/challenge everywhere, including reset and resend.
                var account=tasks.lockAccount(candidate.userId()).orElse(null);
                if (account==null) { continue; }
                var task=tasks.lockDueTask(candidate.id(), now).orElse(null);
                if (task==null) { continue; }
                if (!isEligible(task, account, now)) {
                    tasks.terminal(task.id(), "SUPERSEDED", "RECOVERY_INTENT_NO_LONGER_CURRENT", now);
                    continue;
                }
                if (task.attemptCount()>=properties.maximumEmailAttempts()) {
                    finishExhausted(task, "DELIVERY_ATTEMPTS_EXHAUSTED", now);
                    continue;
                }
                return tasks.acquire(task, now, properties.emailLeaseSeconds());
            }
            return null;
        });
    }

    private void deliver(PasswordRecoveryEmailTask task) {
        try {
            // Decryption/revalidation is brief; its transaction ends before either SMTP call.
            PasswordRecoveryEmailPayload payload=currentPayload(task);
            if (payload==null) { return; }
            var sender=senders.getIfAvailable();
            if (sender==null) { fail(task, "EMAIL_CONFIGURATION_MISSING"); return; }
            withoutTransactions.executeWithoutResult(transaction -> {
                if ("RECOVERY_CODE".equals(task.taskType())) {
                    sender.sendRecoveryCode(payload.email(), payload.displayName(), payload.code());
                } else {
                    sender.sendPasswordResetNotification(payload.email(), payload.displayName());
                }
            });
            transactions.executeWithoutResult(transaction -> {
                if (tasks.lockAccount(task.userId()).isEmpty()) { return; }
                Instant now=clock.instant();
                if (tasks.lockOwnedTask(task.id(), task.leaseToken(), now).isPresent()) {
                    tasks.terminal(task.id(), "SENT", null, now);
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.warn("Recovery email delivery failed: taskId={} exceptionType={}", task.id(), failure.getClass().getName());
            fail(task, "SMTP_DELIVERY_FAILED");
        }
    }

    private PasswordRecoveryEmailPayload currentPayload(PasswordRecoveryEmailTask task) {
        return transactions.execute(transaction -> {
            var account=tasks.lockAccount(task.userId()).orElse(null);
            if (account==null) { return null; }
            Instant now=clock.instant();
            var current=tasks.lockOwnedTask(task.id(), task.leaseToken(), now).orElse(null);
            if (current==null) { return null; }
            if (!isEligible(current, account, now)) {
                tasks.terminal(task.id(), "SUPERSEDED", "RECOVERY_INTENT_NO_LONGER_CURRENT", now);
                return null;
            }
            var payload=encryption.decrypt(current.id(), current.userId(), current.challengeId(), current.taskType(),
                    current.encryptedPayload(), current.payloadNonce());
            if (!account.email().equals(payload.email()) || ("RECOVERY_CODE".equals(current.taskType())
                    && !secrets.matchesCode(current.challengeId(), payload.code(), recoveries.findForUser(current.userId())
                    .orElseThrow().codeDigest()))) {
                tasks.terminal(task.id(), "SUPERSEDED", "RECOVERY_INTENT_NO_LONGER_CURRENT", now);
                return null;
            }
            return payload;
        });
    }

    private boolean isEligible(PasswordRecoveryEmailTask task, PasswordRecoveryEmailTaskRepository.Account account, Instant now) {
        if (!account.active() || account.authenticationVersion()!=task.authenticationVersion()) { return false; }
        if ("RESET_NOTIFICATION".equals(task.taskType())) { return true; }
        var challenge=recoveries.findForUser(task.userId()).orElse(null);
        return task.expiresAt()!=null && now.isBefore(task.expiresAt()) && challenge!=null
                && task.challengeId().equals(challenge.challengeId())
                && challenge.authenticationVersion()==account.authenticationVersion()
                && challenge.emailAtRequest().equals(account.email()) && challenge.codeDigest()!=null
                && challenge.resetTokenHash()==null && challenge.failedAttempts()<5 && now.isBefore(challenge.codeExpiresAt());
    }

    private void fail(PasswordRecoveryEmailTask original, String code) {
        transactions.executeWithoutResult(transaction -> {
            if (tasks.lockAccount(original.userId()).isEmpty()) { return; }
            Instant now=clock.instant();
            var task=tasks.lockOwnedTask(original.id(), original.leaseToken(), now).orElse(null);
            if (task==null) { return; }
            if (task.attemptCount()>=properties.maximumEmailAttempts()) { finishExhausted(task, code, now); }
            else { tasks.retry(task.id(), code, now, properties.emailRetryDelaySeconds()); }
        });
    }

    private void finishExhausted(PasswordRecoveryEmailTask task, String code, Instant now) {
        tasks.terminal(task.id(), "FAILED", code, now);
        if ("RECOVERY_CODE".equals(task.taskType())) {
            // Exact challenge identity prevents an obsolete worker deleting a newer resend.
            recoveries.deleteFailedDelivery(task.userId(), task.challengeId());
        }
    }

    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline=System.nanoTime()+timeout.toNanos();
        while (executor.getActiveCount()>0 || !executor.getQueue().isEmpty()) {
            if (System.nanoTime()>=deadline) { return false; }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return true;
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) { executor.shutdownNow(); }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Persisted leases are recovered after expiry if shutdown interrupted an SMTP call.
    }
}
