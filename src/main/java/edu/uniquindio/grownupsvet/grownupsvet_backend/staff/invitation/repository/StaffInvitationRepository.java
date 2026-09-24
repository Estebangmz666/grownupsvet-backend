package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitationEmailTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All mutating callers lock the target User before invitations and email tasks. */
@Repository
public class StaffInvitationRepository {
    private final JdbcTemplate jdbc;

    public StaffInvitationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<StaffInvitation> findByTokenHash(String tokenHash) {
        return jdbc.query("SELECT * FROM staff_invitations WHERE token_hash = ?", this::invitation, tokenHash)
                .stream().findFirst();
    }

    public Optional<StaffInvitation> findById(UUID invitationId) {
        return jdbc.query("SELECT * FROM staff_invitations WHERE id = ?", this::invitation, invitationId)
                .stream().findFirst();
    }

    public Optional<StaffInvitation> findByIdForUpdate(UUID invitationId) {
        return jdbc.query("SELECT * FROM staff_invitations WHERE id = ? FOR UPDATE", this::invitation, invitationId)
                .stream().findFirst();
    }

    public Optional<StaffInvitationEmailTask> findEmailTaskForUpdate(UUID invitationId) {
        return jdbc.query("SELECT * FROM staff_invitation_email_tasks WHERE invitation_id = ? FOR UPDATE",
                (row, number) -> new StaffInvitationEmailTask(row.getObject("invitation_id", UUID.class),
                        row.getBytes("encrypted_token"), row.getString("status"), row.getInt("attempts"),
                        row.getTimestamp("next_attempt_at").toInstant()), invitationId).stream().findFirst();
    }

    public List<UUID> findPendingDeliveryIds(Instant now, int limit) {
        return jdbc.query("""
                SELECT task.invitation_id FROM staff_invitation_email_tasks task
                JOIN staff_invitations invitation ON invitation.id = task.invitation_id
                WHERE task.status = 'PENDING' AND (task.next_attempt_at <= ? OR invitation.expires_at <= ?)
                ORDER BY task.next_attempt_at, task.invitation_id LIMIT ?
                """, (row, number) -> row.getObject(1, UUID.class), timestamp(now), timestamp(now), limit);
    }

    public Optional<Instant> latestInvitationAt(UUID userId) {
        return jdbc.query("SELECT created_at FROM staff_invitations WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                (row, number) -> row.getTimestamp(1).toInstant(), userId).stream().findFirst();
    }

    public int invitationsSince(UUID userId, Instant since) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staff_invitations WHERE user_id = ? AND created_at > ?",
                Integer.class, userId, timestamp(since));
        return count == null ? 0 : count;
    }

    public void create(StaffInvitation invitation, UUID actorId, byte[] encryptedToken) {
        jdbc.update("""
                INSERT INTO staff_invitations(id, user_id, invited_by, token_hash, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                """, invitation.id(), invitation.userId(), actorId, invitation.tokenHash(),
                timestamp(invitation.createdAt()), timestamp(invitation.expiresAt()));
        jdbc.update("""
                INSERT INTO staff_invitation_email_tasks(invitation_id, encrypted_token, status, next_attempt_at)
                VALUES (?, ?, 'PENDING', ?)
                """, invitation.id(), encryptedToken, timestamp(invitation.createdAt()));
    }

    public void cancelPendingInvitations(UUID userId, Instant now) {
        // User serialization makes this iteration stable, including against the delivery worker.
        List<UUID> pending = jdbc.query("""
                SELECT id FROM staff_invitations WHERE user_id = ? AND status = 'PENDING' ORDER BY id FOR UPDATE
                """, (row, number) -> row.getObject(1, UUID.class), userId);
        for (UUID invitationId : pending) {
            jdbc.update("UPDATE staff_invitations SET status = 'CANCELLED' WHERE id = ?", invitationId);
            finishEmailTask(invitationId, "CANCELLED", now);
        }
    }

    public void consume(UUID invitationId, Instant now) {
        jdbc.update("UPDATE staff_invitations SET status = 'CONSUMED', consumed_at = ? WHERE id = ?",
                timestamp(now), invitationId);
        finishEmailTask(invitationId, "CANCELLED", now);
    }

    public void expire(UUID invitationId, Instant now) {
        jdbc.update("UPDATE staff_invitations SET status = 'EXPIRED' WHERE id = ? AND status = 'PENDING'", invitationId);
        finishEmailTask(invitationId, "EXPIRED", now);
    }

    public void finishEmailTask(UUID invitationId, String status, Instant now) {
        jdbc.update("""
                UPDATE staff_invitation_email_tasks
                SET status = ?, encrypted_token = NULL, completed_at = ?
                WHERE invitation_id = ? AND status = 'PENDING'
                """, status, timestamp(now), invitationId);
    }

    public void delivered(UUID invitationId, int attempts, Instant now) {
        jdbc.update("""
                UPDATE staff_invitation_email_tasks SET status = 'SENT', encrypted_token = NULL,
                attempts = ?, last_failure_code = NULL, completed_at = ? WHERE invitation_id = ?
                """, attempts, timestamp(now), invitationId);
    }

    public void failedAttempt(UUID invitationId, int attempts, Instant nextAttemptAt,
                              String failureCode, boolean exhausted, Instant now) {
        if (exhausted) {
            jdbc.update("""
                    UPDATE staff_invitation_email_tasks SET status = 'FAILED', encrypted_token = NULL,
                    attempts = ?, last_failure_code = ?, completed_at = ? WHERE invitation_id = ?
                    """, attempts, failureCode, timestamp(now), invitationId);
        } else {
            jdbc.update("""
                    UPDATE staff_invitation_email_tasks SET attempts = ?, next_attempt_at = ?, last_failure_code = ?
                    WHERE invitation_id = ?
                    """, attempts, timestamp(nextAttemptAt), failureCode, invitationId);
        }
    }

    private StaffInvitation invitation(ResultSet row, int number) throws SQLException {
        return new StaffInvitation(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class),
                row.getString("token_hash"), row.getString("status"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("expires_at").toInstant());
    }

    private Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }
}
