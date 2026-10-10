# Transporte SMTP de recuperación V18 — 10/10/2026

Se comprobó el transporte actual del correo de recuperación sobre el backend publicado en `9659dd2e348b7ff81e0c73546b5b77ee56744ad6`, sin modificar código de producción. La clasificación de esta evidencia es **`TransportOnlyVerifyResetPending`**: acredita la solicitud y el transporte al Sandbox; la verificación del código y el restablecimiento completo siguen pendientes.

| Comprobación | Resultado observado |
|---|---|
| Registro público de una cuenta ficticia | `POST /api/v1/auth/registrations` → HTTP `201`; propietario activo creado por la API. |
| Solicitud pública de recuperación | `POST /api/v1/auth/password-recoveries` con su correo → HTTP `202` y `Cache-Control: no-store`. |
| Outbox de V18 | Una tarea `RECOVERY_CODE`, estado `SENT`, `attempt_count=1`, sin error; `sent_at=2026-10-10T15:29:15.258887Z` (10:29:15 Bogotá). |
| Transporte | SMTP autenticado con STARTTLS obligatorio hacia Mailtrap Sandbox; máximo un intento y un correo en esta ejecución. |
| Cabeceras en Mailtrap | Mensaje `5747394247`; asunto «Código para recuperar tu contraseña de GrownupsVet»; destinatario `recovery-transport-e5878a47-cdf8-4f21-a3b6-56ed3309d231@example.test`; tamaño mostrado de 698 bytes. |
| Tiempo | `Clock.system(America/Bogota)` de producción, sin reloj simulado ni llamada manual al dispatcher. |
| Limpieza | Proceso terminó con código `0`; una conexión JDBC independiente, de solo lectura, confirmó que el esquema propio ya no existía y que el puerto local estaba cerrado. |

La cabecera mostró `2026-10-10 15:29`; no se comprobó la zona horaria que utiliza esa representación de Mailtrap. La hora Bogotá de la tabla se deriva del instante UTC almacenado por el backend.

La prueba temporal se ejecutó únicamente sobre un esquema UUID propio en `grownupsvet_test`, con API en loopback, RSA privada de prueba y dos claves criptográficas aleatorias independientes en memoria para HMAC y AES-GCM. No reutilizó secretos de recuperación del entorno del operador. Las invitaciones, el bootstrap de personal y el remitente de citas estuvieron deshabilitados. El mecanismo real de recuperación admitió y envió la tarea; el harness solo observó metadatos del outbox, sin leer el OTP, el desafío ni el payload cifrado.

El [reporte saneado](evidencia-recuperacion-smtp-2026-10-10/reporte-transporte.json) contiene únicamente una lista permitida de metadatos. No incluye contraseñas, claves, OTP, tokens, cuerpo, payload cifrado, PID, nombre de esquema ni URL de la API temporal. La [captura de cabeceras](evidencia-recuperacion-smtp-2026-10-10/encabezado-recuperacion.png) recorta el contenido anterior al cuerpo del mensaje.

Esta ejecución **no acredita** lectura visual del cuerpo, recepción en un buzón externo real, verificación del OTP, consumo del permiso de restablecimiento, cambio de contraseña, nuevo login ni revocación real de sesiones después del cambio. Tampoco acredita la integración del frontend o el cierre de todo P1. Es independiente de los seis correos de citas documentados en [la verificación anterior](verificacion-mailtrap-2026-10-10.md) y de la prueba de recordatorio periódico con reinicio; no deben sumarse como ejecuciones de un mismo flujo.

El contrato observado proviene de [UserPasswordRecoveryController](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/authentication/recovery/controller/UserPasswordRecoveryController.java), el transporte de [SmtpPasswordRecoveryEmailSender](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/authentication/recovery/email/SmtpPasswordRecoveryEmailSender.java) y la persistencia de [V18](../src/main/resources/db/migration/V18__persist_encrypted_password_recovery_emails.sql). No se volvió a ejecutar Maven ni se amplió la suite automatizada durante esta prueba de transporte.
