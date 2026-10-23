package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpStaffInvitationEmailSenderTests {
    @Test
    void composesUtf8PlainTextAndHtmlInvitationWithoutInventingAPassword() throws Exception {
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
        Multipart alternatives = (Multipart) requiredPart(message, "multipart/alternative").getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        assertThat(alternatives.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(alternatives.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat(alternatives.getBodyPart(0).getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(alternatives.getBodyPart(1).getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(text(message, "text/plain")).contains(url, "48 horas", "un solo uso", "2026-09-15T14:00:00Z")
                .doesNotContain("contraseña temporal", properties.encryptionKey());
        assertThat(text(message, "text/html")).contains(url, "GrownupsVet", "Bogotá")
                .doesNotContain("contraseña temporal", properties.encryptionKey());
        assertThat(message.getSubject()).doesNotContain("A".repeat(43));
        verify(mailSender).send(message);
    }

    @Test
    void preservesTheActivationUrlInPlainTextAndEscapesItsHtmlAttribute() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        String url = "https://portal.example.invalid/activate-account?token=" + "A".repeat(43) + "&source=a%26b";
        new SmtpStaffInvitationEmailSender(sender, properties())
                .sendInvitation("recipient@example.test", url, Instant.parse("2026-09-15T14:00:00Z"));
        message.saveChanges();
        assertThat(text(message, "text/plain")).contains(url);
        assertThat(text(message, "text/html")).contains("?token=" + "A".repeat(43) + "&amp;source=a%26b")
                .doesNotContain("&amp;amp;source", "javascript:");
    }

    @Test
    void rejectsAnUnsafeActivationLinkBeforePreparingOrSendingMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        assertThatThrownBy(() -> new SmtpStaffInvitationEmailSender(sender, properties())
                .sendInvitation("recipient@example.test", "javascript:alert(1)", Instant.parse("2026-09-15T14:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void propagatesTransportFailuresWithoutRetryingInsideTheSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Controlled transport failure")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> new SmtpStaffInvitationEmailSender(sender, properties())
                .sendInvitation("recipient@example.test", "https://portal.example.invalid/activate-account?token=" + "A".repeat(43),
                        Instant.parse("2026-09-15T14:00:00Z")))
                .isInstanceOf(MailSendException.class);
        verify(sender).send(any(MimeMessage.class));
    }

    private static StaffInvitationProperties properties() {
        return new StaffInvitationProperties(true, "https://portal.example.invalid/activate-account",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "staff@example.test", 5);
    }

    private static String text(Part part, String mediaType) throws Exception {
        return (String) requiredPart(part, mediaType).getContent();
    }

    private static Part requiredPart(Part part, String mediaType) throws Exception {
        Part result = findPart(part, mediaType);
        assertThat(result).as("MIME part %s", mediaType).isNotNull();
        return result;
    }

    private static Part findPart(Part part, String mediaType) throws Exception {
        if (part.isMimeType(mediaType)) return part;
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                Part found = findPart(multipart.getBodyPart(index), mediaType);
                if (found != null) return found;
            }
        }
        return null;
    }
}
