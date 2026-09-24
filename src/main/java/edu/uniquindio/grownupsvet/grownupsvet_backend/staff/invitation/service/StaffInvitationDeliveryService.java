package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email.StaffInvitationEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitationEmailTask;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.repository.StaffInvitationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class StaffInvitationDeliveryService {
    private final UserRepository users;
    private final StaffInvitationRepository invitations;
    private final StaffInvitationProperties properties;
    private final StaffInvitationSecrets secrets;
    private final ObjectProvider<StaffInvitationEmailSender> senderProvider;
    private final Clock clock;

    public StaffInvitationDeliveryService(UserRepository users, StaffInvitationRepository invitations,
            StaffInvitationProperties properties, StaffInvitationSecrets secrets,
            ObjectProvider<StaffInvitationEmailSender> senderProvider, Clock clock) {
        this.users = users;
        this.invitations = invitations;
        this.properties = properties;
        this.secrets = secrets;
        this.senderProvider = senderProvider;
        this.clock = clock;
    }

    /** Delivery is at least once; the same token can be mailed again after an uncertain SMTP acknowledgement. */
    @Transactional
    public void deliver(UUID invitationId) {
        StaffInvitation observed = invitations.findById(invitationId).orElse(null);
        if (observed == null) { return; }
        User user = users.findByIdForUpdate(observed.userId()).orElse(null);
        if (user == null) { return; }
        StaffInvitation invitation = invitations.findByIdForUpdate(invitationId).orElse(null);
        if (invitation == null) { return; }
        StaffInvitationEmailTask task = invitations.findEmailTaskForUpdate(invitationId).orElse(null);
        if (task == null || !"PENDING".equals(task.status())) { return; }
        Instant now = clock.instant();
        if (!now.isBefore(invitation.expiresAt())) {
            invitations.expire(invitationId, now);
            return;
        }
        if (!"PENDING".equals(invitation.status()) || user.getStatus() != UserStatus.PENDING_ACTIVATION
                || user.getPasswordHash() != null) {
            invitations.finishEmailTask(invitationId, "CANCELLED", now);
            return;
        }
        if (!properties.enabled() || now.isBefore(task.nextAttemptAt())) { return; }
        int attempts = task.attempts() + 1;
        try {
            String token = secrets.decrypt(invitationId, task.encryptedToken());
            // TO-DO: replace with front portal production URL.
            String activationUrl = properties.activationUrl() + "?token=" + token;
            senderProvider.getObject().sendInvitation(user.getEmail(), activationUrl, invitation.expiresAt());
            invitations.delivered(invitationId, attempts, clock.instant());
        } catch (RuntimeException exception) {
            // Provider exception messages can contain SMTP credentials, addresses or message bodies.
            Instant failedAt = clock.instant();
            long delaySeconds = Math.min(3600, 60L << Math.min(attempts - 1, 6));
            Instant nextAttempt = failedAt.plusSeconds(delaySeconds);
            boolean exhausted = attempts >= properties.maxDeliveryAttempts() || !nextAttempt.isBefore(invitation.expiresAt());
            invitations.failedAttempt(invitationId, attempts, nextAttempt,
                    exception instanceof MailException ? "SMTP_DELIVERY_FAILED" : "INVITATION_DELIVERY_FAILED",
                    exhausted, failedAt);
        }
    }
}
