package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
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
        String body = """
                %s

                Usa este código de 6 dígitos para recuperar tu contraseña de GrownupsVet:

                %s

                El código caduca en 10 minutos. No lo compartas con nadie.
                Si no solicitaste recuperar tu contraseña, puedes ignorar este correo.

                Equipo de GrownupsVet
                """.formatted(greeting(displayName), code);
        send(email, "Código para recuperar tu contraseña de GrownupsVet", body);
    }

    @Override
    public void sendPasswordResetNotification(String email, String displayName) {
        String body = """
                %s

                Tu contraseña de GrownupsVet fue restablecida.
                Tus sesiones anteriores se cerraron. Puedes iniciar sesión con tu nueva contraseña.

                Si no realizaste este cambio, recupera el acceso desde la aplicación y contacta al equipo de GrownupsVet.

                Equipo de GrownupsVet
                """.formatted(greeting(displayName));
        send(email, "Tu contraseña de GrownupsVet fue restablecida", body);
    }

    private void send(String email, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.senderAddress());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, false);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Could not prepare password recovery email", exception);
        }
        mailSender.send(message);
    }

    private String greeting(String displayName) {
        return displayName == null || displayName.isBlank() ? "Hola," : "Hola " + displayName.strip() + ",";
    }
}
