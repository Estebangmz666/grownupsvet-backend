# Verificación del recordatorio con reloj real — 10 de octubre de 2026

Se verificó localmente que el worker periódico de P12 recupera un recordatorio persistido al abrir un nuevo contexto de Spring y lo envía a Mailtrap a la hora programada. La cuenta ficticia del propietario estaba desactivada y la cita permanecía confirmada.

La base de código fue `9659dd2`. El ensayo utilizó un harness temporal bajo `target/mailtrap-real-clock`, PostgreSQL `grownupsvet_test` con aislamiento propio y SMTP real del Sandbox **grownupsvet / 4905208**, con STARTTLS obligatorio. Se enviaron exactamente dos correos, con un intento por tarea. El harness no modificó código de producción, migraciones ni configuración persistente del equipo.

## Reloj y programación

Se utilizó el bean de producción `Clock.system(America/Bogota)`, definido en [ApplicationClockConfiguration.java](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/shared/configuration/ApplicationClockConfiguration.java), y el intervalo habitual de sondeo de **15 segundos** de [AppointmentEmailDispatcher.java](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/appointment/service/AppointmentEmailDispatcher.java). `reminderAdvance` conservó **PT24H**.

| Dato | Valor comprobado |
| --- | --- |
| RunId | `0940f180-6af3-4925-8747-4bef864831fc` |
| Cita | 11 de octubre de 2026, 10:30 de Bogotá (`2026-10-11T15:30:00Z`) |
| Recordatorio programado | 10 de octubre de 2026, 10:30 de Bogotá (`2026-10-10T15:30:00Z`) |
| Recordatorio marcado `SENT` en PostgreSQL | `2026-10-10T15:30:15.310735Z` |
| Propietario al enviar el recordatorio | `DISABLED` |
| Tareas finales | 2 `SENT`, un intento cada una |

La diferencia entre inicio de cita y programación del recordatorio es exactamente **24 horas**. La espera de este ensayo fue de aproximadamente **12 minutos reales**: se creó una cita para la misma hora del día siguiente. Se mantuvieron el reloj del sistema, el Clock de producción y la fecha programada; no fue una espera de 24 horas completas.

## Recorrido y persistencia

1. Se guardaron únicamente las precondiciones ficticias de administrador y veterinario; el propietario, la mascota, el turno, la cita y su confirmación se crearon mediante HTTP con seguridad JWT real y claves de prueba en memoria.
2. Se esperó `OWNER_CONFIRMED = SENT`. El recordatorio estaba `PENDING`, con fecha futura e intento 0.
3. El propietario ficticio desactivó su cuenta mediante `DELETE /api/v1/users/me` → **204**. Una consulta de perfil con el mismo JWT anterior devolvió **401**; PostgreSQL confirmó `DISABLED` y la cita siguió `CONFIRMED`.
4. Se cerró y volvió a abrir el **SpringApplicationContext en el mismo JVM**. El recordatorio conservó su identificador, su fecha programada y su estado `PENDING`, sin duplicar la confirmación.
5. Desde la apertura del segundo contexto, el harness hizo únicamente observaciones SQL. El worker periódico envió el recordatorio a las 10:30:15 de Bogotá. El harness no llamó `dispatchPending`, ningún corte manual ni endpoints HTTP después del reinicio del contexto.

El alcance del reinicio es el contexto y sus beans dentro del mismo JVM. La comprobación del JWT revocado se realizó antes del cierre; las claves RSA del harness se generaron en memoria por contexto.

## Captura en Mailtrap

El destinatario ficticio fue `real-owner-0940f180-6af3-4925-8747-4bef864831fc@example.test`; la mascota se identificó como `Mascota REAL 0940f180-6af3-4925-8747-4bef864831fc`.

| Tipo | Mensaje de Mailtrap | Asunto | Fecha mostrada por la UI | Tamaño mostrado |
| --- | --- | --- | --- | --- |
| `OWNER_CONFIRMED` | `5747390257` | Actualización de tu cita veterinaria | 10 oct., 15:17 | 538 bytes |
| `OWNER_REMINDER` | `5747394783` | Recordatorio de tu cita veterinaria | 10 oct., 15:30 | 526 bytes |

Se comprobaron la lista, los asuntos, los destinatarios y los encabezados visibles. Las fechas mostradas por Mailtrap tienen precisión de minutos y su zona horaria no se verificó; el momento exacto de finalización del transporte se conserva en `sentAt` de PostgreSQL. Los cuerpos quedaron **pendientes de inspección** por la limitación previa de la vista del navegador. La captura acredita el Sandbox; no acredita entrega a un buzón real del propietario.

- [Captura de confirmación](evidencia-recordatorio-real-2026-10-10/confirmacion.png).
- [Captura del recordatorio con reloj real](evidencia-recordatorio-real-2026-10-10/recordatorio-reloj-real.png).
- [Resultado sanitizado con estados antes y después del reinicio](evidencia-recordatorio-real-2026-10-10/resultado-sanitizado.json).

## Cierre

Después de verificar las capturas, se eliminó exclusivamente el aislamiento propio del ensayo. Una conexión SQL independiente confirmó su ausencia; el proceso terminó con código **0** y el puerto HTTP quedó cerrado. Los artefactos compartidos contienen únicamente datos ficticios y metadatos de verificación, sin credenciales, claves, JWT, contraseñas ni identificadores del proceso o de su configuración local.
