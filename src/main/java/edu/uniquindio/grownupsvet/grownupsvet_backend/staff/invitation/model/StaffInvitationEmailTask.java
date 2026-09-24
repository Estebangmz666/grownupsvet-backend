package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationEmailTask(UUID invitationId, byte[] encryptedToken, String status,
                                       int attempts, Instant nextAttemptAt) {
    public StaffInvitationEmailTask {
        encryptedToken = encryptedToken == null ? null : encryptedToken.clone();
    }
    @Override public byte[] encryptedToken() { return encryptedToken == null ? null : encryptedToken.clone(); }
    @Override public String toString() { return "StaffInvitationEmailTask[invitationId=" + invitationId + ", status=" + status + "]"; }
}
