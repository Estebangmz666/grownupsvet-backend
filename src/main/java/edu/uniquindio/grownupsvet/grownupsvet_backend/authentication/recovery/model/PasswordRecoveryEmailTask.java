package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model;

import java.time.Instant;
import java.util.UUID;

public record PasswordRecoveryEmailTask(UUID id, UUID userId, UUID challengeId, String taskType,
                                       long authenticationVersion, Instant expiresAt, int attemptCount,
                                       byte[] encryptedPayload, byte[] payloadNonce, UUID leaseToken) {
    @Override public String toString() { return "PasswordRecoveryEmailTask[contents=[REDACTED]]"; }
}
