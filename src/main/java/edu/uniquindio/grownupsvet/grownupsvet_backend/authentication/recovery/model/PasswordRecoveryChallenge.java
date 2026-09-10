package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model;

import java.time.Instant;
import java.util.UUID;

/** Internal database snapshot; never used as an API response. */
public record PasswordRecoveryChallenge(UUID userId, UUID challengeId, String emailAtRequest,
                                        long authenticationVersion, byte[] codeDigest,
                                        Instant codeExpiresAt, int failedAttempts,
                                        String resetTokenHash, Instant resetExpiresAt) {
    @Override public String toString() { return "PasswordRecoveryChallenge[contents=[REDACTED]]"; }
}
