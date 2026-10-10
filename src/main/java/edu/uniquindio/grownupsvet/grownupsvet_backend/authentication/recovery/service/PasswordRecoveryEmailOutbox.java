package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailPayload;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryEmailTaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordRecoveryEmailOutbox {
    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryEmailTaskRepository tasks;
    private final PasswordRecoveryEmailEncryption encryption;
    private final ApplicationEventPublisher events;

    public PasswordRecoveryEmailOutbox(PasswordRecoveryProperties properties, PasswordRecoveryEmailTaskRepository tasks,
                                      PasswordRecoveryEmailEncryption encryption, ApplicationEventPublisher events) {
        this.properties=properties; this.tasks=tasks; this.encryption=encryption; this.events=events;
    }

    public boolean hasCapacity() {
        requireTransaction();
        return tasks.hasCapacity(properties.emailQueueCapacity());
    }

    public void enqueueCode(UUID userId, UUID challengeId, long version, String email, String displayName,
                            String code, Instant expiresAt, Instant now) {
        enqueue(userId, challengeId, version, "RECOVERY_CODE", new PasswordRecoveryEmailPayload(email, displayName, code),
                expiresAt, now, true);
    }

    public void enqueueResetNotification(UUID userId, UUID challengeId, long version, String email, String displayName,
                                         Instant now, boolean admitted) {
        enqueue(userId, challengeId, version, "RESET_NOTIFICATION", new PasswordRecoveryEmailPayload(email, displayName, null),
                null, now, admitted);
    }

    private void enqueue(UUID userId, UUID challengeId, long version, String type, PasswordRecoveryEmailPayload payload,
                         Instant expiresAt, Instant now, boolean admitted) {
        requireTransaction();
        UUID id = UUID.randomUUID();
        var encrypted = admitted ? encryption.encrypt(id, userId, challengeId, type, payload) : null;
        tasks.insert(id, userId, challengeId, version, type, encrypted == null ? null : encrypted.ciphertext(),
                encrypted == null ? null : encrypted.nonce(), expiresAt, now, admitted);
        if (admitted) { events.publishEvent(new PasswordRecoveryEmailsQueuedEvent(id)); }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Recovery email intents require the business transaction.");
        }
    }
}
