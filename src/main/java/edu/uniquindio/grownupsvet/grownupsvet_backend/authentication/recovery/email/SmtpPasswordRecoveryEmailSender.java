package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email.TransactionalEmailContent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email.TransactionalEmailTemplates;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "grownupsvet.security.password-recovery", name = "enabled", havingValue = "true")
public class SmtpPasswordRecoveryEmailSender implements PasswordRecoveryEmailSender {
    private final JavaMailSender mailSender;
    private final PasswordRecoveryProperties properties;

    public SmtpPasswordRecoveryEmailSender(JavaMailSender mailSender, PasswordRecoveryProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendRecoveryCode(String email, String displayName, String code) {
        send(email, TransactionalEmailTemplates.passwordRecoveryCode(displayName, code));
    }

    @Override
    public void sendPasswordResetNotification(String email, String displayName) {
        send(email, TransactionalEmailTemplates.passwordResetNotification(displayName));
    }

    private void send(String email, TransactionalEmailContent content) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.senderAddress());
            helper.setTo(email);
            helper.setSubject(content.subject());
            helper.setText(content.plainText(), content.html());
        } catch (MessagingException exception) {
            throw new MailPreparationException("Could not prepare password recovery email", exception);
        }
        mailSender.send(message);
    }
}
