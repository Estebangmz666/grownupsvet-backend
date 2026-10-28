package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises actual MIME bytes and parsing without constructing an SMTP transport or opening sockets. */
class TransactionalEmailMimeEncodingTests {
    @Test
    void prescriptionInstructionsKeepTheirUnicodeAcrossBothMimeAlternatives() throws Exception {
        String instructions = "Prueba del sistema sin uso cl\u00ednico real; no administrar";
        var content = TransactionalEmailTemplates.appointmentNotice(
                "Recordatorio de tratamiento de GrownupsVet",
                "Prescripci\u00f3n para Ni\u00f1a. V\u00eda: simulada. Instrucciones del profesional: " + instructions,
                "https://portal.example.test");

        MimeMessage parsed = roundTrip(content);

        assertThat(text(parsed, "text/plain")).contains(instructions, "Prescripci\u00f3n", "Ni\u00f1a", "V\u00eda");
        assertThat(text(parsed, "text/html")).contains(instructions, "Prescripci\u00f3n", "Ni\u00f1a", "V\u00eda");
    }

    @Test
    void controlReasonKeepsItsAccentAcrossBothMimeAlternatives() throws Exception {
        String reason = "Control ficticio de aceptaci\u00f3n de software";
        var content = TransactionalEmailTemplates.appointmentNotice(
                "Recordatorio de control de GrownupsVet", "Recuerda el control: " + reason, "");

        MimeMessage parsed = roundTrip(content);

        assertThat(text(parsed, "text/plain")).contains(reason);
        assertThat(text(parsed, "text/html")).contains(reason);
    }

    @Test
    void appointmentSubjectAndDynamicTextKeepAccentsAndHtmlEscaping() throws Exception {
        var content = TransactionalEmailTemplates.appointmentNotice(
                "Actualizaci\u00f3n de tu cita veterinaria",
                "La cita de Mar\u00eda & Ni\u00f1a <prueba> fue actualizada.\nRevisi\u00f3n: \u00e1\u00e9\u00ed\u00f3\u00fa\u00f1.",
                "https://portal.example.test/demo?view=appointments&source=email");

        MimeMessage parsed = roundTrip(content);

        assertThat(text(parsed, "text/plain")).contains("Mar\u00eda & Ni\u00f1a <prueba>", "\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1");
        assertThat(text(parsed, "text/html")).contains("Mar\u00eda &amp; Ni\u00f1a &lt;prueba&gt;", "source=email")
                .doesNotContain("&amp;amp;", "<prueba>");
    }

    @Test
    void invitationAndRecoveryShareTheUtf8WireContract() throws Exception {
        var contents = new TransactionalEmailContent[] {
                TransactionalEmailTemplates.passwordRecoveryCode("Mar\u00eda P\u00e9rez", "004218"),
                TransactionalEmailTemplates.passwordResetNotification("Mar\u00eda P\u00e9rez"),
                TransactionalEmailTemplates.staffInvitation(
                        "https://portal.example.test/activate?token=fictional-only&source=staff",
                        Instant.parse("2026-10-30T14:00:00Z")) };

        for (var content : contents) {
            roundTrip(content);
        }
    }

    @Test
    void mimeEncodingPreservesAnAlreadyCorruptInputInsteadOfRewritingProfessionalData() throws Exception {
        String supplied = "Dato de prueba ya corrupto: cl\u00c3\u00adnico";
        var content = TransactionalEmailTemplates.appointmentNotice("Control", supplied, "");

        MimeMessage parsed = roundTrip(content);

        assertThat(text(parsed, "text/plain")).isEqualTo(supplied);
        assertThat(text(parsed, "text/html")).contains(supplied);
    }

    private static MimeMessage roundTrip(TransactionalEmailContent content) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.from", "sender@example.test");
        properties.setProperty("mail.host", "example.test");
        Session session = Session.getInstance(properties);
        MimeMessage message = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom("sender@example.test");
        helper.setTo("recipient@example.test");
        helper.setSubject(content.subject());
        helper.setText(content.plainText(), content.html());
        message.saveChanges();

        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        message.writeTo(wire);
        MimeMessage parsed = new MimeMessage(session, new ByteArrayInputStream(wire.toByteArray()));

        assertThat(parsed.getSubject()).isEqualTo(content.subject());
        if (content.subject().codePoints().anyMatch(value -> value > 127)) {
            assertThat(parsed.getHeader("Subject", null)).containsIgnoringCase("UTF-8");
        }
        assertThat(normalizeLineEndings(text(parsed, "text/plain")))
                .isEqualTo(normalizeLineEndings(content.plainText()));
        assertThat(normalizeLineEndings(text(parsed, "text/html")))
                .isEqualTo(normalizeLineEndings(content.html()));
        return parsed;
    }

    private static String text(Part part, String mediaType) throws Exception {
        Part required = findPart(part, mediaType);
        assertThat(required).as("MIME alternative %s", mediaType).isNotNull();
        assertThat(new ContentType(required.getContentType()).getParameter("charset")).isEqualToIgnoringCase("UTF-8");
        return (String) required.getContent();
    }

    private static Part findPart(Part part, String mediaType) throws Exception {
        if (part.isMimeType(mediaType)) { return part; }
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                Part match = findPart(multipart.getBodyPart(index), mediaType);
                if (match != null) { return match; }
            }
        }
        return null;
    }

    // RFC MIME line endings may canonicalize during serialization; Unicode values remain exact.
    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }
}
