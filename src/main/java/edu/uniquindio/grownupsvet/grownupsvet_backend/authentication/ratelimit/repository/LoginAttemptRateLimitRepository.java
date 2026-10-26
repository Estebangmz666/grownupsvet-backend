package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Repository
public class LoginAttemptRateLimitRepository {
    private final JdbcTemplate jdbc;

    public LoginAttemptRateLimitRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Caller holds a short admission transaction. Every request locks the origin first, then its pair. */
    public long admit(String originKey, int originMaximum, Duration originWindow,
                      String identifierOriginKey, int identifierOriginMaximum, Duration identifierOriginWindow,
                      Instant now) {
        Bucket origin = lockBucket(originKey, originWindow, now);
        long originWait = origin.usedCount() >= originMaximum ? secondsUntil(now, origin.expiresAt()) : 0;
        // Reject before creating an attacker-selected pair when shared origin capacity is exhausted.
        if (originWait > 0) { return originWait; }
        Bucket pair = lockBucket(identifierOriginKey, identifierOriginWindow, now);
        if (pair.usedCount() >= identifierOriginMaximum) { return secondsUntil(now, pair.expiresAt()); }
        increment(originKey);
        increment(identifierOriginKey);
        return 0;
    }

    public int removeExpired(Instant now) {
        return jdbc.update("""
                DELETE FROM login_attempt_rate_limits WHERE bucket_key IN (
                    SELECT bucket_key FROM login_attempt_rate_limits WHERE expires_at <= ?
                    ORDER BY expires_at, bucket_key LIMIT 1000 FOR UPDATE SKIP LOCKED)
                """, Timestamp.from(now));
    }

    private Bucket lockBucket(String key, Duration window, Instant now) {
        // A no-op conflict update also locks an existing bucket. Separate INSERT/SELECT statements
        // would allow expiry cleanup to delete the row between admission's two statements.
        Bucket bucket = jdbc.queryForObject("""
                INSERT INTO login_attempt_rate_limits(bucket_key, window_started_at, used_count, expires_at)
                VALUES (?, ?, 0, ?) ON CONFLICT (bucket_key)
                DO UPDATE SET bucket_key = EXCLUDED.bucket_key RETURNING used_count, expires_at
                """, (row, index) -> new Bucket(row.getInt(1), row.getTimestamp(2).toInstant()),
                key, Timestamp.from(now), Timestamp.from(now.plus(window)));
        if (!now.isBefore(bucket.expiresAt())) {
            Instant expiresAt = now.plus(window);
            jdbc.update("""
                    UPDATE login_attempt_rate_limits SET window_started_at = ?, used_count = 0, expires_at = ?
                    WHERE bucket_key = ?
                    """, Timestamp.from(now), Timestamp.from(expiresAt), key);
            return new Bucket(0, expiresAt);
        }
        return bucket;
    }

    private void increment(String key) {
        jdbc.update("UPDATE login_attempt_rate_limits SET used_count = used_count + 1 WHERE bucket_key = ?", key);
    }

    private static long secondsUntil(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        return Math.max(1, remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1));
    }

    private record Bucket(int usedCount, Instant expiresAt) { }
}
