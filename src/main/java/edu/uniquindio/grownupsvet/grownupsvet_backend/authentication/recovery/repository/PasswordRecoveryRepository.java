package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryChallenge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PasswordRecoveryRepository {
    public static final Duration RATE_WINDOW = Duration.ofHours(1);
    private final JdbcTemplate jdbc;

    public PasswordRecoveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Caller owns a transaction. Row locking also serializes first concurrent use of a bucket. */
    public long consumeRateLimit(String key, int maximum, Duration minimumInterval, Instant now) {
        jdbc.update("""
                INSERT INTO password_recovery_rate_limits
                    (bucket_key, window_started_at, used_count, last_accepted_at)
                VALUES (?, ?, 0, ?) ON CONFLICT (bucket_key) DO NOTHING
                """, key, timestamp(now), timestamp(now.minus(RATE_WINDOW)));
        RateLimit limit = jdbc.queryForObject("""
                SELECT window_started_at, used_count, last_accepted_at
                FROM password_recovery_rate_limits WHERE bucket_key = ? FOR UPDATE
                """, (row, index) -> new RateLimit(row.getTimestamp(1).toInstant(), row.getInt(2),
                row.getTimestamp(3).toInstant()), key);
        Instant start = limit.windowStartedAt();
        int count = limit.usedCount();
        if (!now.isBefore(start.plus(RATE_WINDOW))) {
            start = now;
            count = 0;
        }
        long wait = count >= maximum ? secondsUntil(now, start.plus(RATE_WINDOW)) : 0;
        wait = Math.max(wait, secondsUntil(now, limit.lastAcceptedAt().plus(minimumInterval)));
        if (wait > 0) { return wait; }
        jdbc.update("""
                UPDATE password_recovery_rate_limits
                SET window_started_at = ?, used_count = ?, last_accepted_at = ? WHERE bucket_key = ?
                """, timestamp(start), count + 1, timestamp(now), key);
        return 0;
    }

    public Optional<PasswordRecoveryChallenge> findForUser(UUID userId) {
        return jdbc.query("SELECT * FROM password_recovery_challenges WHERE user_id = ? FOR UPDATE",
                (row, index) -> challenge(row), userId).stream().findFirst();
    }

    /** Lookup only; the service locks the account then checks the token again under that lock. */
    public Optional<UUID> findUserForResetToken(String tokenHash) {
        return jdbc.query("SELECT user_id FROM password_recovery_challenges WHERE reset_token_hash = ?",
                (row, index) -> row.getObject(1, UUID.class), tokenHash).stream().findFirst();
    }

    public void replaceChallenge(UUID userId, UUID challengeId, String email, long version,
                                 byte[] digest, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO password_recovery_challenges
                    (user_id, challenge_id, email_at_request, authentication_version,
                     code_digest, code_expires_at, failed_attempts, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                ON CONFLICT (user_id) DO UPDATE SET challenge_id = EXCLUDED.challenge_id,
                    email_at_request = EXCLUDED.email_at_request,
                    authentication_version = EXCLUDED.authentication_version,
                    code_digest = EXCLUDED.code_digest, code_expires_at = EXCLUDED.code_expires_at,
                    failed_attempts = 0, reset_token_hash = NULL, reset_expires_at = NULL,
                    updated_at = EXCLUDED.updated_at
                """, userId, challengeId, email, version, digest, timestamp(expiresAt), timestamp(now));
    }

    public void recordFailedAttempt(UUID userId, Instant now) {
        jdbc.update("""
                UPDATE password_recovery_challenges SET failed_attempts = LEAST(failed_attempts + 1, 5),
                    code_digest = CASE WHEN failed_attempts >= 4 THEN NULL ELSE code_digest END,
                    updated_at = ? WHERE user_id = ?
                """, timestamp(now), userId);
    }

    public void grantReset(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        jdbc.update("""
                UPDATE password_recovery_challenges SET code_digest = NULL,
                    reset_token_hash = ?, reset_expires_at = ?, updated_at = ? WHERE user_id = ?
                """, tokenHash, timestamp(expiresAt), timestamp(now), userId);
    }

    public void deleteForUser(UUID userId) {
        jdbc.update("DELETE FROM password_recovery_challenges WHERE user_id = ?", userId);
    }

    public void deleteFailedDelivery(UUID userId, UUID challengeId) {
        jdbc.update("""
                DELETE FROM password_recovery_challenges WHERE user_id = ? AND challenge_id = ?
                AND code_digest IS NOT NULL AND reset_token_hash IS NULL
                """,
                userId, challengeId);
    }

    public void deleteExpired(Instant cutoff) {
        jdbc.update("DELETE FROM password_recovery_rate_limits WHERE window_started_at < ?", timestamp(cutoff));
        jdbc.update("""
                DELETE FROM password_recovery_challenges
                WHERE code_expires_at < ? AND (reset_expires_at IS NULL OR reset_expires_at < ?)
                """, timestamp(cutoff), timestamp(cutoff));
    }

    private PasswordRecoveryChallenge challenge(ResultSet row) throws SQLException {
        Timestamp resetExpiresAt = row.getTimestamp("reset_expires_at");
        return new PasswordRecoveryChallenge(row.getObject("user_id", UUID.class),
                row.getObject("challenge_id", UUID.class), row.getString("email_at_request"),
                row.getLong("authentication_version"), row.getBytes("code_digest"),
                row.getTimestamp("code_expires_at").toInstant(), row.getInt("failed_attempts"),
                row.getString("reset_token_hash"), resetExpiresAt == null ? null : resetExpiresAt.toInstant());
    }

    private long secondsUntil(Instant now, Instant end) {
        if (!now.isBefore(end)) { return 0; }
        return Math.max(1, (Duration.between(now, end).toMillis() + 999) / 1000);
    }

    private Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }
    private record RateLimit(Instant windowStartedAt, int usedCount, Instant lastAcceptedAt) { }
}
