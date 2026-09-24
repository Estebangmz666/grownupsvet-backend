package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email;

import java.time.Instant;

public interface StaffInvitationEmailSender {
    void sendInvitation(String email, String activationUrl, Instant expiresAt);
}
