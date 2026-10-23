package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpPasswordRecoveryEmailSenderTests {
    @Test
    void deliversTheRecoveryCodeAsUtf8PlainTextAndHtmlThroughLocalSmtp() throws Exception {
        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            createSender(smtpServer).sendRecoveryCode("recipient@example.test", "María Pérez", "004218");

            MimeMessage message = smtpServer.receivedMessage();
            assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("recovery@example.test");
            assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                    .isEqualTo("recipient@example.test");
            assertThat(message.getSubject()).isEqualTo("Código para recuperar tu contraseña de GrownupsVet");
            assertAlternativeParts(message);
            assertThat(text(message, "text/plain")).contains("Hola María Pérez,", "004218", "10 minutos",
                    "No lo compartas", "puedes ignorar este correo");
            assertThat(text(message, "text/html")).contains("María Pérez", "004218", "GrownupsVet");
            assertThat(message.getSubject()).doesNotContain("004218");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void greetsWithoutANameWhenNoDisplayNameIsAvailable(String displayName) throws Exception {
        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            createSender(smtpServer).sendRecoveryCode("recipient@example.test", displayName, "004218");

            MimeMessage message = smtpServer.receivedMessage();
            assertThat(text(message, "text/plain")).startsWith("Hola,")
                    .doesNotContain("Hola null", "Hola ,");
            assertThat(text(message, "text/html")).contains("Hola,").doesNotContain("Hola null", "Hola ,");
        }
    }

    @Test
    void deliversTheResetNotificationWithoutASecret() throws Exception {
        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            createSender(smtpServer).sendPasswordResetNotification("recipient@example.test", "María Pérez");

            MimeMessage message = smtpServer.receivedMessage();
            assertThat(message.getSubject()).isEqualTo("Tu contraseña de GrownupsVet fue restablecida");
            assertAlternativeParts(message);
            assertThat(text(message, "text/plain")).contains("Hola María Pérez,", "fue restablecida",
                    "sesiones anteriores se cerraron", "Si no realizaste este cambio")
                    .doesNotContain("004218", "Usa este código", "resetToken", "token=");
            assertThat(text(message, "text/html")).contains("María Pérez", "fue restablecida")
                    .doesNotContain("004218", "Usa este código", "resetToken", "token=");
        }
    }

    @Test
    void treatsDynamicDisplayNamesAsTextInBothWireAlternatives() throws Exception {
        String displayName = "María <script>alert(\"x\")</script> & Pérez";
        try (LocalSmtpServer smtpServer = new LocalSmtpServer()) {
            createSender(smtpServer).sendRecoveryCode("recipient@example.test", displayName, "004218");
            MimeMessage message = smtpServer.receivedMessage();
            assertThat(text(message, "text/plain")).contains(displayName, "004218");
            assertThat(text(message, "text/html")).contains("&lt;script&gt;", "&amp;", "004218")
                    .doesNotContain("<script>", "</script>");
        }
    }

    @Test
    void propagatesTransportFailuresWithoutAnAdditionalSend() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Controlled transport failure")).when(sender).send(any(MimeMessage.class));
        PasswordRecoveryProperties properties = mock(PasswordRecoveryProperties.class);
        when(properties.senderAddress()).thenReturn("recovery@example.test");
        assertThatThrownBy(() -> new SmtpPasswordRecoveryEmailSender(sender, properties)
                .sendRecoveryCode("recipient@example.test", "María Pérez", "004218"))
                .isInstanceOf(MailSendException.class);
        verify(sender).send(any(MimeMessage.class));
    }

    private static void assertAlternativeParts(MimeMessage message) throws Exception {
        Multipart alternatives = (Multipart) requiredPart(message, "multipart/alternative").getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        assertThat(alternatives.getBodyPart(0).isMimeType("text/plain")).isTrue();
        assertThat(alternatives.getBodyPart(1).isMimeType("text/html")).isTrue();
        assertThat(alternatives.getBodyPart(0).getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(alternatives.getBodyPart(1).getContentType()).containsIgnoringCase("charset=UTF-8");
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

    private PasswordRecoveryEmailSender createSender(LocalSmtpServer smtpServer) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpServer.host());
        mailSender.setPort(smtpServer.port());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties smtpProperties = new Properties();
        // Avoid workstation DNS lookup in EHLO and Message-ID generation; this test is loopback only.
        smtpProperties.setProperty("mail.smtp.localhost", "localhost");
        smtpProperties.setProperty("mail.host", "localhost");
        smtpProperties.setProperty("mail.smtp.connectiontimeout", "5000");
        smtpProperties.setProperty("mail.smtp.timeout", "5000");
        smtpProperties.setProperty("mail.smtp.writetimeout", "5000");
        mailSender.setJavaMailProperties(smtpProperties);
        PasswordRecoveryProperties properties = mock(PasswordRecoveryProperties.class);
        when(properties.senderAddress()).thenReturn("recovery@example.test");
        return new SmtpPasswordRecoveryEmailSender(mailSender, properties);
    }

    /** An SMTP receiver bound exclusively to loopback; no external delivery is possible. */
    private static final class LocalSmtpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<byte[]> receivedMessage;

        private LocalSmtpServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(5000);
            executor = Executors.newSingleThreadExecutor();
            receivedMessage = executor.submit(this::receive);
        }

        private String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private MimeMessage receivedMessage() throws Exception {
            byte[] message = receivedMessage.get(10, TimeUnit.SECONDS);
            return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(message));
        }

        private byte[] receive() throws IOException {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.ISO_8859_1));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.ISO_8859_1));
                reply(writer, "220 localhost test SMTP");
                StringBuilder message = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("EHLO ") || line.startsWith("HELO ")
                            || line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:") || line.equals("RSET")) {
                        reply(writer, "250 OK");
                    } else if (line.equals("DATA")) {
                        reply(writer, "354 End with <CRLF>.<CRLF>");
                        while ((line = reader.readLine()) != null && !line.equals(".")) {
                            message.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                        }
                        reply(writer, "250 Accepted");
                    } else if (line.equals("QUIT")) {
                        reply(writer, "221 Bye");
                        break;
                    } else {
                        throw new IOException("Unexpected SMTP command during local delivery test");
                    }
                }
                return message.toString().getBytes(StandardCharsets.ISO_8859_1);
            }
        }

        private void reply(BufferedWriter writer, String response) throws IOException {
            writer.write(response);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}
