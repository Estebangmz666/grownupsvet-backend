package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TransactionalEmailTemplatesTests {
    @Test
    void recoveryKeepsLeadingZeroesAndMeasuresValidityFromTheRequest() {
        TransactionalEmailContent content = TransactionalEmailTemplates.passwordRecoveryCode("María Pérez", "004218");

        assertThat(content.subject()).isEqualTo("Código para recuperar tu contraseña de GrownupsVet");
        assertThat(content.plainText()).contains("Hola María Pérez,", "004218",
                "vigencia máxima de 10 minutos desde la solicitud", "No lo compartas",
                "puedes ignorar este correo");
        assertThat(content.html()).contains("004218", "Hola María Pérez,",
                "vigencia máxima de 10 minutos desde la solicitud").doesNotContain("href=");
        assertThat(content.html().substring(0, content.html().indexOf("<table"))).doesNotContain("004218");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void missingDisplayNameUsesANaturalGreeting(String displayName) {
        for (TransactionalEmailContent content : new TransactionalEmailContent[] {
                TransactionalEmailTemplates.passwordRecoveryCode(displayName, "004218"),
                TransactionalEmailTemplates.passwordResetNotification(displayName) }) {
            assertThat(content.plainText()).startsWith("Hola,\n").doesNotContain("Hola null", "Hola ,");
            assertThat(content.html()).contains("Hola,").doesNotContain("Hola null", "Hola ,");
        }
    }

    @Test
    void dynamicNamesAreEscapedOnlyInHtml() {
        String name = "  Ana <script>alert(1)</script> & \"Luz\" O'Neil  ";

        TransactionalEmailContent content = TransactionalEmailTemplates.passwordRecoveryCode(name, "004218");

        assertThat(content.plainText()).contains("Hola " + name.strip() + ",").doesNotContain("&lt;script&gt;");
        assertThat(content.html()).contains("Ana &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Luz&quot; O&#39;Neil")
                .doesNotContain("<script>", "&amp;lt;script");
    }

    @Test
    void resetNotificationKeepsSecurityAdviceWithoutAddingASecretOrAction() {
        TransactionalEmailContent content = TransactionalEmailTemplates.passwordResetNotification("María");

        assertThat(content.subject()).isEqualTo("Tu contraseña de GrownupsVet fue restablecida");
        assertThat(content.plainText()).contains("sesiones anteriores se cerraron", "nueva contraseña",
                "Si no realizaste este cambio", "contacta al equipo de GrownupsVet")
                .doesNotContain("004218", "token=", "Usa este código", "https://");
        assertThat(content.html()).contains("sesiones anteriores se cerraron", "Si no realizaste este cambio")
                .doesNotContain("href=", "004218", "token=");
    }

    @Test
    void invitationShowsTheActualExpiryInBogotaAndUtcWithoutRestartingItsLifetime() {
        String url = "https://portal.example.test/activate-account?token=fictional-only&source=staff";
        TransactionalEmailContent content = TransactionalEmailTemplates.staffInvitation(url,
                Instant.parse("2026-09-15T00:00:00Z"));

        assertThat(content.subject()).isEqualTo("Activa tu cuenta de personal en GrownupsVet");
        assertThat(content.plainText()).contains(url, "14 de septiembre de 2026 a las 19:00 (hora de Bogotá)",
                "2026-09-15T00:00:00Z", "un solo uso", "48 horas desde su emisión",
                "No compartas este enlace", "no te enviará una contraseña")
                .doesNotContain("48 horas desde que recibas", "contraseña temporal", "&amp;");
        assertThat(content.html()).contains("14 de septiembre de 2026 a las 19:00", "2026-09-15T00:00:00Z",
                "Activar mi cuenta", "copia y pega", "token=fictional-only&amp;source=staff")
                .doesNotContain("token=fictional-only&source=staff");
        assertThat(content.html().substring(0, content.html().indexOf("<table"))).doesNotContain("fictional-only");
    }

    @Test
    void invitationEscapesUrlAttributeAndVisibleFallbackWithoutChangingThePlainUrl() {
        String url = "https://portal.example.test/activate?token=fictional-only&label='prueba'";
        TransactionalEmailContent content = TransactionalEmailTemplates.staffInvitation(url, Instant.EPOCH);

        assertThat(content.plainText()).contains(url);
        assertThat(content.html()).contains(
                "href=\"https://portal.example.test/activate?token=fictional-only&amp;label=&#39;prueba&#39;\"")
                .doesNotContain("&amp;amp;", "href='", "label='prueba'");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "javascript:alert(1)", "data:text/html,hello", "//portal.example.test/activate",
            "/activate", "https://user:secret@portal.example.test/activate?token=fictional-only",
            "https://@portal.example.test/activate", "https:///missing-host", "https://portal.example.test/\r\nInjected"})
    void invitationRejectsUnsafeOrIncompleteActionUrlsWithoutLeakingTheirContents(String url) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                        TransactionalEmailTemplates.staffInvitation(url, Instant.EPOCH))
                .withMessage("Email action URL must be an absolute HTTP(S) URL without user information")
                .withNoCause();
    }

    @Test
    void appointmentKeepsExistingPlainTextAndEscapesTheSubjectBodyAndLinkInHtml() {
        String subject = "Actualización <urgente> & \"cita\"";
        String body = "La cita de Luna <img src=x onerror=alert(1)> fue actualizada.\nVeterinario: O'Neil & Pérez.";
        String url = "https://portal.example.test/demo?view=appointments&source=email";

        TransactionalEmailContent content = TransactionalEmailTemplates.appointmentNotice(subject, body, url);

        assertThat(content.subject()).isEqualTo(subject);
        assertThat(content.plainText()).isEqualTo(body + "\nPuedes consultar los detalles en: " + url);
        assertThat(content.html()).contains("Actualización &lt;urgente&gt; &amp; &quot;cita&quot;",
                "Luna &lt;img src=x onerror=alert(1)&gt;", "<br>Veterinario: O&#39;Neil &amp; Pérez.",
                "Consultar la aplicación", "view=appointments&amp;source=email")
                .doesNotContain("<img", "<urgente>", "&amp;amp;");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void appointmentWithoutConfiguredFrontendKeepsTheBodyAndHasNoInventedAction(String url) {
        TransactionalEmailContent content = TransactionalEmailTemplates.appointmentNotice("Cita confirmada", "Cuerpo.", url);

        assertThat(content.plainText()).isEqualTo("Cuerpo.");
        assertThat(content.html()).contains("Cuerpo.").doesNotContain("href=", "Consultar la aplicación", "https://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "https://user:secret@portal.example.test/", "/demo", "https:///demo"})
    void appointmentFailsClosedForAnInvalidConfiguredFrontend(String url) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                        TransactionalEmailTemplates.appointmentNotice("Cita", "Cuerpo", url))
                .withNoCause();
    }

    @Test
    void absoluteLocalHttpIsAcceptedWithoutInventingAnHttpsReplacement() {
        String url = "http://127.0.0.1:8085/demo";
        TransactionalEmailContent content = TransactionalEmailTemplates.appointmentNotice("Cita", "Cuerpo", url);

        assertThat(content.plainText()).endsWith(url);
        assertThat(content.html()).contains("href=\"" + url + "\"").doesNotContain("https://127.0.0.1");
    }

    @Test
    void allMessagesHaveAFluidSpanishLayoutWithoutRemoteAssetsOrActiveContent() {
        TransactionalEmailContent[] messages = {
                TransactionalEmailTemplates.passwordRecoveryCode("María", "004218"),
                TransactionalEmailTemplates.passwordResetNotification("María"),
                TransactionalEmailTemplates.staffInvitation("https://portal.example.test/activate?token=fictional-only", Instant.EPOCH),
                TransactionalEmailTemplates.appointmentNotice("Cita", "Cuerpo", "https://portal.example.test") };

        for (TransactionalEmailContent content : messages) {
            assertThat(content.plainText()).isNotBlank().doesNotContain("<html", "<table", "&lt;");
            assertThat(content.html()).contains("<html lang=\"es\">", "charset=\"UTF-8\"", "name=\"viewport\"",
                    "role=\"presentation\"", "max-width:600px", "width:100%", "#0878D1", "<h1", "GrownupsVet")
                    .doesNotContain("<img", "<script", "<form", "<iframe", "@import", "<link", "<style", "url(");
        }
    }

    @Test
    void contentToStringDoesNotExposeRecoveryCodesActivationLinksOrPersonalData() {
        TransactionalEmailContent recovery = TransactionalEmailTemplates.passwordRecoveryCode("María Pérez", "004218");
        TransactionalEmailContent invitation = TransactionalEmailTemplates.staffInvitation(
                "https://portal.example.test/activate?token=fictional-secret", Instant.EPOCH);
        TransactionalEmailContent appointment = TransactionalEmailTemplates.appointmentNotice(
                "Cita de Luna", "Información personal de prueba", "https://portal.example.test");

        for (TransactionalEmailContent content : new TransactionalEmailContent[] { recovery, invitation, appointment }) {
            assertThat(content.toString()).contains("[REDACTED]")
                    .doesNotContain("004218", "María", "Pérez", "token=", "fictional-secret", "https://",
                            "Cita de Luna", "Información personal", "<html");
        }
    }
}
