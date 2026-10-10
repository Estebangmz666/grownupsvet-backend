package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PasswordRecoveryEmailTaskRepository {
    private static final long ADMISSION_LOCK = 734164640241L;
    private final JdbcTemplate jdbc;

    public PasswordRecoveryEmailTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** All admission checks happen before identifying the requested account, in the business transaction. */
    public boolean hasCapacity(int capacity) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", row -> { }, ADMISSION_LOCK);
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_recovery_email_tasks WHERE status IN ('PENDING','SENDING')",
                Long.class) < capacity;
    }

    public void insert(UUID id, UUID userId, UUID challengeId, long authenticationVersion, String type,
                       byte[] ciphertext, byte[] nonce, Instant expiresAt, Instant now, boolean admitted) {
        jdbc.update("""
                INSERT INTO password_recovery_email_tasks
                    (id,user_id,challenge_id,authentication_version,task_type,status,encrypted_payload,payload_nonce,
                     expires_at,next_attempt_at,last_error_code,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, userId, challengeId, authenticationVersion, type, admitted ? "PENDING" : "FAILED",
                admitted ? ciphertext : null, admitted ? nonce : null, timestamp(expiresAt), timestamp(now),
                admitted ? null : "DELIVERY_CAPACITY_EXHAUSTED", timestamp(now), timestamp(now));
    }

    public void supersedeRecoveryCodes(UUID userId, Instant now) {
        jdbc.update("""
                UPDATE password_recovery_email_tasks SET status='SUPERSEDED',encrypted_payload=NULL,payload_nonce=NULL,
                    lease_token=NULL,lease_expires_at=NULL,last_error_code='CHALLENGE_REPLACED_OR_CONSUMED',updated_at=?
                WHERE user_id=? AND task_type='RECOVERY_CODE' AND status IN ('PENDING','SENDING')
                """, timestamp(now), userId);
    }

    public List<Candidate> candidates(Instant now) {
        return jdbc.query("""
                SELECT id,user_id FROM password_recovery_email_tasks
                WHERE (status='PENDING' AND next_attempt_at<=?) OR (status='SENDING' AND lease_expires_at<=?)
                ORDER BY next_attempt_at,created_at,id LIMIT 25
                """, (row, index) -> new Candidate(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class)),
                timestamp(now), timestamp(now));
    }

    public Optional<Account> lockAccount(UUID userId) {
        return jdbc.query("SELECT id,email,status,authentication_version FROM users WHERE id=? FOR UPDATE SKIP LOCKED",
                (row, index) -> new Account(row.getString("email"), "ACTIVE".equals(row.getString("status")),
                        row.getLong("authentication_version")), userId).stream().findFirst();
    }

    public Optional<PasswordRecoveryEmailTask> lockDueTask(UUID id, Instant now) {
        return jdbc.query("""
                SELECT * FROM password_recovery_email_tasks WHERE id=?
                AND ((status='PENDING' AND next_attempt_at<=?) OR (status='SENDING' AND lease_expires_at<=?))
                FOR UPDATE SKIP LOCKED
                """, (row, index) -> task(row), id, timestamp(now), timestamp(now)).stream().findFirst();
    }

    public Optional<PasswordRecoveryEmailTask> lockOwnedTask(UUID id, UUID token, Instant now) {
        return jdbc.query("""
                SELECT * FROM password_recovery_email_tasks WHERE id=? AND status='SENDING'
                AND lease_token=? AND lease_expires_at>? FOR UPDATE
                """, (row, index) -> task(row), id, token, timestamp(now)).stream().findFirst();
    }

    public PasswordRecoveryEmailTask acquire(PasswordRecoveryEmailTask task, Instant now, long leaseSeconds) {
        UUID token = UUID.randomUUID();
        jdbc.update("""
                UPDATE password_recovery_email_tasks SET status='SENDING',attempt_count=attempt_count+1,
                    lease_token=?,lease_expires_at=?,updated_at=? WHERE id=?
                """, token, timestamp(now.plusSeconds(leaseSeconds)), timestamp(now), task.id());
        return new PasswordRecoveryEmailTask(task.id(), task.userId(), task.challengeId(), task.taskType(),
                task.authenticationVersion(), task.expiresAt(), task.attemptCount()+1,
                task.encryptedPayload(), task.payloadNonce(), token);
    }

    public void terminal(UUID id, String state, String error, Instant now) {
        if (!List.of("SENT", "FAILED", "SUPERSEDED").contains(state)) {
            throw new IllegalArgumentException("Unexpected recovery email terminal state.");
        }
        jdbc.update("""
                UPDATE password_recovery_email_tasks SET status=?,encrypted_payload=NULL,payload_nonce=NULL,
                    lease_token=NULL,lease_expires_at=NULL,last_error_code=?,updated_at=?,sent_at=? WHERE id=?
                """, state, error, timestamp(now), "SENT".equals(state) ? timestamp(now) : null, id);
    }

    public void retry(UUID id, String error, Instant now, long retryDelaySeconds) {
        jdbc.update("""
                UPDATE password_recovery_email_tasks SET status='PENDING',lease_token=NULL,lease_expires_at=NULL,
                    last_error_code=?,next_attempt_at=?,updated_at=? WHERE id=?
                """, error, timestamp(now.plusSeconds(retryDelaySeconds)), timestamp(now), id);
    }

    public void purgeTerminalBefore(Instant cutoff) {
        jdbc.update("DELETE FROM password_recovery_email_tasks WHERE status IN ('SENT','FAILED','SUPERSEDED') AND updated_at<?",
                timestamp(cutoff));
    }

    public void supersedeExpiredCodes(Instant now) {
        jdbc.update("""
                UPDATE password_recovery_email_tasks SET status='SUPERSEDED',encrypted_payload=NULL,payload_nonce=NULL,
                    lease_token=NULL,lease_expires_at=NULL,last_error_code='RECOVERY_CODE_EXPIRED',updated_at=?
                WHERE task_type='RECOVERY_CODE' AND status IN ('PENDING','SENDING') AND expires_at<=?
                """, timestamp(now), timestamp(now));
    }

    private PasswordRecoveryEmailTask task(ResultSet row) throws SQLException {
        Timestamp expiry = row.getTimestamp("expires_at");
        return new PasswordRecoveryEmailTask(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class),
                row.getObject("challenge_id", UUID.class), row.getString("task_type"), row.getLong("authentication_version"),
                expiry == null ? null : expiry.toInstant(), row.getInt("attempt_count"), row.getBytes("encrypted_payload"),
                row.getBytes("payload_nonce"), row.getObject("lease_token", UUID.class));
    }

    private Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    public record Candidate(UUID id, UUID userId) { }
    public record Account(String email, boolean active, long authenticationVersion) {
        @Override public String toString() { return "RecoveryEmailAccount[contents=[REDACTED]]"; }
    }
}
