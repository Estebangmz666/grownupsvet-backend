package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
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
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.senderAddress());
            helper.setTo(email);
            helper.setSubject("Activa tu cuenta de personal en GrownupsVet");
            helper.setText("""
                    Hola,

                    Te invitaron a formar parte del personal de GrownupsVet.
                    Abre este enlace para elegir tu contraseña y activar tu cuenta:

                    %s

                    El enlace es de un solo uso y vence el %s (UTC), 48 horas después de su emisión.
                    No compartas este enlace. GrownupsVet no te enviará una contraseña por correo.
                    Si no esperabas esta invitación, contacta a la administración de la clínica.

                    Equipo de GrownupsVet
                    """.formatted(activationUrl, expiresAt), false);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Could not prepare staff invitation email");
        }
        sender.send(message);
    }
}
