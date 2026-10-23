package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email.TransactionalEmailTemplates;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "grownupsvet.staff.invitations", name = "enabled", havingValue = "true")
public class SmtpStaffInvitationEmailSender implements StaffInvitationEmailSender {
    private final JavaMailSender sender;
    private final StaffInvitationProperties properties;

    public SmtpStaffInvitationEmailSender(JavaMailSender sender, StaffInvitationProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void sendInvitation(String email, String activationUrl, Instant expiresAt) {
        var content = TransactionalEmailTemplates.staffInvitation(activationUrl, expiresAt);
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.senderAddress());
            helper.setTo(email);
            helper.setSubject(content.subject());
            helper.setText(content.plainText(), content.html());
        } catch (MessagingException exception) {
            throw new MailPreparationException("Could not prepare staff invitation email");
        }
        sender.send(message);
    }
}
