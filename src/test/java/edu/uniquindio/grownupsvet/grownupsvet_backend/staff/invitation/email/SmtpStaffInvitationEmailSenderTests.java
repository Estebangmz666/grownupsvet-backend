package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpStaffInvitationEmailSenderTests {
    @Test
    void composesAReadablePlainTextInvitationWithoutInventingAPassword() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        StaffInvitationProperties properties = new StaffInvitationProperties(true,
                "https://portal.example.invalid/activate-account",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "staff@example.test", 5);
        String url = "https://portal.example.invalid/activate-account?token=" + "A".repeat(43);
        new SmtpStaffInvitationEmailSender(mailSender, properties)
                .sendInvitation("recipient@example.test", url, Instant.parse("2026-09-15T14:00:00Z"));
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Activa tu cuenta de personal en GrownupsVet");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("staff@example.test");
        assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress()).isEqualTo("recipient@example.test");
        assertThat(message.isMimeType("text/plain")).isTrue();
        assertThat(message.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat((String) message.getContent()).contains(url, "48 horas", "un solo uso", "2026-09-15T14:00:00Z")
                .doesNotContain("contraseña temporal", properties.encryptionKey());
        verify(mailSender).send(message);
    }
}
