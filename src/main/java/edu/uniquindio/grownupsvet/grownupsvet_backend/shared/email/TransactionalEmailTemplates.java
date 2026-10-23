package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Pure presentation of existing messages; delivery and business rules remain with their senders. */
public final class TransactionalEmailTemplates {
    private static final DateTimeFormatter INVITATION_EXPIRATION = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' uuuu 'a las' HH:mm", Locale.forLanguageTag("es-CO"))
            .withZone(ZoneId.of("America/Bogota"));
    private static final String INVALID_ACTION_URL =
            "Email action URL must be an absolute HTTP(S) URL without user information";

    private TransactionalEmailTemplates() { }

    public static TransactionalEmailContent passwordRecoveryCode(String displayName, String code) {
        Objects.requireNonNull(code, "Recovery code is required");
        String introduction = "Usa este código de 6 dígitos para recuperar tu contraseña de GrownupsVet:";
        String validity = "El código tiene una vigencia máxima de 10 minutos desde la solicitud. No lo compartas con nadie.";
        String unexpectedRequest = "Si no solicitaste recuperar tu contraseña, puedes ignorar este correo.";
        String plainText = """
                %s

                %s

                %s

                %s
                %s

                Equipo de GrownupsVet
                """.formatted(greeting(displayName), introduction, code, validity, unexpectedRequest);
        String body = paragraph(greeting(displayName)) + paragraph(introduction)
                + "<p style=\"margin:24px 0;padding:20px 12px;background-color:#eef6ff;border:1px solid #c9e2f8;"
                + "border-radius:8px;text-align:center;font-family:Consolas,monospace;font-size:32px;"
                + "font-weight:700;line-height:1.4;letter-spacing:6px;color:#17324d;\">"
                + escapeHtml(code) + "</p>" + paragraph(validity) + paragraph(unexpectedRequest);
        return content("Código para recuperar tu contraseña de GrownupsVet", "Recupera tu contraseña",
                "Recupera el acceso a tu cuenta de GrownupsVet.", plainText, body);
    }

    public static TransactionalEmailContent passwordResetNotification(String displayName) {
        String confirmation = "Tu contraseña de GrownupsVet fue restablecida.";
        String sessions = "Tus sesiones anteriores se cerraron. Puedes iniciar sesión con tu nueva contraseña.";
        String unexpectedChange = "Si no realizaste este cambio, recupera el acceso desde la aplicación y contacta al equipo de GrownupsVet.";
        String plainText = """
                %s

                %s
                %s

                %s

                Equipo de GrownupsVet
                """.formatted(greeting(displayName), confirmation, sessions, unexpectedChange);
        return content("Tu contraseña de GrownupsVet fue restablecida", "Tu contraseña fue restablecida",
                "Te avisamos de un cambio de contraseña en tu cuenta.", plainText,
                paragraph(greeting(displayName)) + paragraph(confirmation) + paragraph(sessions)
                        + paragraph(unexpectedChange));
    }

    public static TransactionalEmailContent staffInvitation(String activationUrl, Instant expiresAt) {
        String actionUrl = validateActionUrl(activationUrl);
        Objects.requireNonNull(expiresAt, "Invitation expiration is required");
        String introduction = "Te invitaron a formar parte del personal de GrownupsVet.";
        String instructions = "Abre este enlace para elegir tu contraseña y activar tu cuenta:";
        String expiration = "El enlace es de un solo uso y vence el "
                + INVITATION_EXPIRATION.format(expiresAt) + " (hora de Bogotá).";
        String expirationReference = "Referencia UTC: " + expiresAt + ". Su vigencia es de 48 horas desde su emisión.";
        String security = "No compartas este enlace. GrownupsVet no te enviará una contraseña por correo.";
        String unexpectedInvitation = "Si no esperabas esta invitación, contacta a la administración de la clínica.";
        String plainText = """
                Hola,

                %s
                %s

                %s

                %s
                %s
                %s
                %s

                Equipo de GrownupsVet
                """.formatted(introduction, instructions, actionUrl, expiration, expirationReference,
                security, unexpectedInvitation);
        return content("Activa tu cuenta de personal en GrownupsVet", "Activa tu cuenta de personal",
                "Activa tu cuenta de personal con un enlace de un solo uso.", plainText,
                paragraph("Hola,") + paragraph(introduction) + paragraph(instructions)
                        + action("Activar mi cuenta", actionUrl) + paragraph(expiration)
                        + paragraph(expirationReference) + paragraph(security) + paragraph(unexpectedInvitation));
    }

    public static TransactionalEmailContent appointmentNotice(String subject, String body, String frontendBaseUrl) {
        Objects.requireNonNull(subject, "Appointment email subject is required");
        Objects.requireNonNull(body, "Appointment email body is required");
        String actionUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? null : validateActionUrl(frontendBaseUrl);
        String plainText = body + (actionUrl == null ? "" : "\nPuedes consultar los detalles en: " + actionUrl);
        return content(subject, subject, "Tienes una actualización de GrownupsVet.", plainText,
                paragraph(body) + (actionUrl == null ? "" : action("Consultar la aplicación", actionUrl)));
    }

    private static TransactionalEmailContent content(String subject, String heading, String preheader,
            String plainText, String body) {
        String html = """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f7fb;color:#17324d;font-family:Arial,Helvetica,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;border-collapse:collapse;background-color:#f3f7fb;">
                    <tr><td align="center" style="padding:24px 12px;">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;table-layout:fixed;border-collapse:collapse;background-color:#ffffff;border:1px solid #dde7ef;">
                        <tr><td style="padding:24px;background-color:#0878D1;color:#ffffff;font-size:22px;font-weight:700;line-height:1.4;">GrownupsVet</td></tr>
                        <tr><td style="padding:28px 24px 12px;">
                          <h1 style="margin:0 0 24px;color:#17324d;font-size:24px;font-weight:700;line-height:1.35;overflow-wrap:anywhere;word-break:break-word;">%s</h1>
                          %s
                        </td></tr>
                        <tr><td style="padding:20px 24px;border-top:1px solid #dde7ef;color:#51627a;font-size:14px;line-height:1.6;">Equipo de GrownupsVet</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(subject), escapeHtml(preheader), escapeHtml(heading), body);
        return new TransactionalEmailContent(subject, plainText, html);
    }

    private static String greeting(String displayName) {
        return displayName == null || displayName.isBlank() ? "Hola," : "Hola " + displayName.strip() + ",";
    }

    private static String paragraph(String text) {
        return "<p style=\"margin:0 0 18px;font-size:16px;line-height:1.65;overflow-wrap:anywhere;word-break:break-word;\">"
                + escapeHtml(text).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>") + "</p>";
    }

    private static String action(String label, String url) {
        String escapedUrl = escapeHtml(url);
        return "<p style=\"margin:24px 0;\"><a href=\"" + escapedUrl
                + "\" style=\"display:inline-block;padding:14px 24px;background-color:#0878D1;border:1px solid #0878D1;"
                + "border-radius:6px;color:#ffffff;font-size:16px;font-weight:700;line-height:1.4;text-align:center;text-decoration:none;\">"
                + escapeHtml(label) + "</a></p>"
                + paragraph("Si el botón no funciona, copia y pega este enlace en tu navegador:")
                + "<p style=\"margin:0 0 24px;font-size:14px;line-height:1.6;overflow-wrap:anywhere;word-break:break-all;\"><a href=\""
                + escapedUrl + "\" style=\"color:#0878D1;text-decoration:underline;overflow-wrap:anywhere;word-break:break-all;\">"
                + escapedUrl + "</a></p>";
    }

    private static String validateActionUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (uri.isAbsolute() && uri.getHost() != null && !uri.getHost().isBlank()
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getRawUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Do not retain a malformed invitation URL or its token in an exception cause.
        }
        throw new IllegalArgumentException(INVALID_ACTION_URL);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
