# Plan de mejora de plantillas de correo

Fecha: **23 de octubre de 2026**, zona `America/Bogota`.

El usuario autorizó volver al backend para mejorar las plantillas. Este plan
precede a la implementación y limita el incremento a presentación y contenido
de correos existentes. Backend base `0bd38ed7bfe9a4cc715bb9f1e21dbcafc53c31cc`.
Los 33 archivos con cambios locales previos se preservan; no se reescriben ni
se incluyen por asumir que pertenecen a este incremento. P3 queda fuera de alcance.

## Trazabilidad

La [selección Jira de solo lectura](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/jira-seleccion.json)
consultada el **23/10 a las 17:22:48 UTC** conserva 19 incidencias. Las
referencias relevantes son [SCRUM-9](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-9)
y [SCRUM-15](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-15)
(En curso); [SCRUM-30](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-30),
[SCRUM-33](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-33) y
[SCRUM-47](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-47)
(Finalizado); [SCRUM-32](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-32),
[SCRUM-49](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-49) y
[SCRUM-80](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-80)
(En curso). Este incremento mejora la presentación de correos ya existentes;
no reabre ni cierra historias, no modifica Jira y no acredita por sí mismo
validación integrada, revisión compartida o recepción SMTP real.

## Diagnóstico actual

Hay tres emisores SMTP y diez variantes de mensaje. Todos usan Jakarta Mail,
`JavaMailSender`, `MimeMessageHelper`, UTF-8 y `setText(..., false)`; no existen
plantillas HTML en resources ni dependencia de Thymeleaf en `pom.xml`.

| Emisor y fuente | Variantes y contenido existente |
| --- | --- |
| [SmtpPasswordRecoveryEmailSender.java:26](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/authentication/recovery/email/SmtpPasswordRecoveryEmailSender.java) | Código de recuperación de seis dígitos, saludo opcional y advertencia de seguridad. Notificación posterior de contraseña restablecida y sesiones anteriores cerradas, sin código o token. |
| [SmtpStaffInvitationEmailSender.java:27](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/staff/invitation/email/SmtpStaffInvitationEmailSender.java) | Invitación de personal, enlace opaco de un uso y vencimiento de 48 horas desde emisión; nunca envía una contraseña. |
| [AppointmentEmailDispatcher.java:123](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/appointment/service/AppointmentEmailDispatcher.java) | `OWNER_CONFIRMED`, `OWNER_REJECTED`, `OWNER_CANCELLED`, `OWNER_RESCHEDULED`, `OWNER_REASSIGNED`, `OWNER_REMINDER` y `ADMIN_REASSIGNMENT`; cuerpo/subject derivados del estado vigente. |

La recuperación envía el código, no el token posterior de restablecimiento,
y no dispone de una URL de recuperación configurada para este mensaje.
La invitación recibe el enlace ya construido por
[StaffInvitationDeliveryService.java:64](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/staff/invitation/service/StaffInvitationDeliveryService.java).
Los avisos de cita solo añaden la base frontend si ya está configurada;
no tienen un enlace profundo de cita en la fuente actual. Las fechas de cita
ya usan español y Bogotá; la invitación muestra actualmente el Instant UTC.
No se inventan correos de bienvenida, activación completada o inasistencia.

La compatibilidad actual se basa en SMTP de Spring/Jakarta Mail, con canal
configurable, STARTTLS y límites existentes. Hay una prueba SMTP local real
de recuperación, un test de composición de invitación y pruebas de colas de
citas. Esto no demuestra renderizado en todos los clientes ni entrega a un
buzón real de la nueva versión.

## Incremento acordado

Crear una factory pura, sin Spring, I/O o dependencia adicional de plantillas,
y un record de contenido común en `shared/email`:

- `TransactionalEmailContent(subject, plainText, html)`.
- `TransactionalEmailTemplates.passwordRecoveryCode(displayName, code)`.
- `TransactionalEmailTemplates.passwordResetNotification(displayName)`.
- `TransactionalEmailTemplates.staffInvitation(activationUrl, expiresAt)`.
- `TransactionalEmailTemplates.appointmentNotice(subject, body, frontendBaseUrl)`.

Los tres emisores se integran separadamente mediante `multipart/alternative`
UTF-8, incluyendo ambas representaciones. Se conservan interfaces de dominio,
destinatarios, remitentes, subjects existentes, eventos, reglas de vigencia,
reintentos, leases y compatibilidad de tareas persistidas. No se modifica
OpenAPI, SQL, configuración, cifrado, envío externo o flujos de autenticación.

La presentación compartida tendrá tabla de hasta 600 px y ancho fluido,
estilos inline, `lang="es"`, tipografía de sistema, encabezado textual
GrownupsVet y color de marca `#0878D1`. Contenido legible sin imágenes, scripts,
formularios, fuentes externas o seguimiento. El preheader no incluirá códigos
o tokens. Se añadirán títulos, párrafos breves, bloque visible para el código
y botón accesible únicamente cuando exista un enlace válido.

Todo texto dinámico se escapará para HTML; los atributos de enlace también.
Una CTA solo acepta URL absoluta HTTP(S), con host y sin credenciales. URL
inválida genera `IllegalArgumentException`; una base frontend vacía conserva
el aviso de cita sin CTA ni enlace. Nunca se construye un dominio supuesto.
Botón y alternativa de copiar/pegar usan el mismo enlace escapado en HTML.
El texto plano conserva las cadenas originales sin doble escape.

El código conserva ceros iniciales. Su texto aclarará **vigencia máxima de
10 minutos desde la solicitud**, sin prometer tiempo adicional desde recepción.
La invitación mostrará vencimiento legible en Bogotá y referencia UTC,
conservando un uso y 48 horas desde emisión. La notificación de cambio de
contraseña conserva sus advertencias, sin nueva CTA. En citas se mantiene
el subject/cuerpo y la concatenación del enlace de texto existente.

## Verificación y preservación

Las pruebas puras cubrirán escape de nombres/cuerpos/subjects/enlaces,
rechazo de esquemas o credenciales no admitidos, URL opcional de citas,
contenido alternativo, código con ceros, ausencia de secretos en el aviso de
cambio de contraseña y vencimientos que cambian de fecha al pasar a Bogotá.
La integración SMTP verificará `multipart/alternative`, UTF-8, partes de texto
y HTML coherentes, sin alterar pruebas de comportamiento de colas.

Los tests previos `AppointmentEmailDispatcherIntegrationTests`,
`PasswordRecoveryHttpIntegrationTests` y `StaffInvitationHttpIntegrationTests`
ya tienen cambios locales. La adaptación MIME de citas debe conservar esos
cambios; los tests SMTP específicos de recuperación e invitación estaban
sin modificaciones al diagnóstico. Las ejecuciones Maven se coordinarán para
no solapar compilaciones; las pruebas del renderer no enviarán correo.

La aceptación exige pruebas automatizadas pertinentes y previsualización
local con datos ficticios en escritorio y móvil. Se diferenciarán esa evidencia,
la entrega SMTP local y cualquier prueba posterior autorizada de buzón real.
No se escribirán estados/comentarios Jira ni se declarará terminada la mejora
antes de integrar y verificar los tres emisores.
