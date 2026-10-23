# Mejora de plantillas de correo: verificación local

23 de octubre de 2026, America/Bogota. Base backend
`0bd38ed7bfe9a4cc715bb9f1e21dbcafc53c31cc`. Este corte acredita implementación
y comprobación local; todavía no acredita publicación ni recepción en un buzón real.

## Resultado

Los tres emisores existentes ahora envían HTML y texto plano mediante
`multipart/alternative`, UTF-8. Recuperación destaca el código conservando
ceros iniciales y aclara que los diez minutos cuentan desde la solicitud.
El aviso de contraseña restablecida conserva el cierre de sesiones y sus
advertencias. La invitación usa el enlace recibido, un botón y su alternativa
copiable, con vencimiento en Bogotá y referencia UTC. Los siete tipos de
aviso de citas conservan el contenido calculado por el dispatcher y solo
añaden un botón cuando existe la URL frontend configurada.

La factory pura `TransactionalEmailTemplates` comparte el diseño, sin nuevas
dependencias. Escapa texto y atributos HTML, valida enlaces HTTP(S) absolutos
sin credenciales y no agrega recursos remotos ni seguimiento. El record de
contenido oculta sus campos en `toString()`. No se cambian destinatarios,
asuntos, interfaces de dominio, endpoints, configuración, SQL, leases,
vigencia, cifrado o reintentos. El dispatcher conserva la segunda consulta
de vigencia inmediatamente anterior a componer y enviar el mensaje.

El [plan](plan-plantillas-correo-2026-10-23.md) precede al código y relaciona
las fuentes y la consulta Jira de solo lectura. P3 está fuera del alcance.
No se actualizaron estados, comentarios o asignaciones de incidencias.

## Pruebas ejecutadas

La ejecución final Maven terminó correctamente el 23/10 a las **12:25:34 Bogotá**:
**74 pruebas, cero fallos, errores u omisiones**, cinco clases:

| Clase | Pruebas | Alcance |
| --- | ---: | --- |
| `TransactionalEmailTemplatesTests` | 31 | Escapes, URL, contenido, vencimiento, código, disposición fluida y redacción de `toString()` |
| `SmtpPasswordRecoveryEmailSenderTests` | 7 | Partes alternativas y UTF-8; transporte SMTP real limitado a loopback; fallos del transporte |
| `SmtpStaffInvitationEmailSenderTests` | 4 | Composición MIME, enlace exacto, fecha, rechazo de enlace inválido y propagación del fallo |
| `AppointmentEmailDispatcherIntegrationTests` | 21 | Cola, estados vigentes, obsoletos, transacciones, leases y reintentos con SMTP sustituido |
| `PasswordRecoveryEmailOutboxIntegrationTests` | 11 | Persistencia cifrada, retrasos/reintentos y vencimiento con SMTP sustituido |

Las dos integraciones usaron PostgreSQL local, base `grownupsvet_test`, con
esquemas aislados. No se enviaron mensajes a SMTP externo. Los avisos de fallo
de transporte del log corresponden a escenarios de error intencionales.
Los 42 casos focalizados anteriores están incluidos en esos 74; no se suman.

Se repitió el mismo conjunto sobre una exportación aislada del índice Git,
con solo este incremento sobre HEAD y sin incorporar los cambios locales
previos ajenos: **72 pruebas correctas a las 12:32:45 Bogotá**, cero fallos,
errores u omisiones. Citas aporta 19 casos en esa base; los otros dos pertenecen
a los cambios previos conservados en el árbol original. Este segundo resultado
comprueba que el incremento publicable no depende de esas modificaciones;
no se suma al total anterior. [Registro del corte aislado](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/maven-scoped-source.log).

Comando PowerShell:

```powershell
mvn -B '-Dtest=TransactionalEmailTemplatesTests,SmtpPasswordRecoveryEmailSenderTests,SmtpStaffInvitationEmailSenderTests,AppointmentEmailDispatcherIntegrationTests,PasswordRecoveryEmailOutboxIntegrationTests' '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/grownupsvet_test' test
```

El [registro final](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/maven-email-integration.log)
conserva el resultado. Las credenciales locales se consumieron del entorno,
sin copiarlas al código o a esta documentación.

## Previsualización y preservación

Se generaron seis muestras desde las clases Java compiladas, usando datos
ficticios. En navegador local se revisaron recuperación, cambio de contraseña,
invitación y cita, además de un nombre de 150 caracteres sin espacios y un
enlace largo. A 390 px no se observó desbordamiento horizontal; recuperación
también se inspeccionó en escritorio. No se pulsaron enlaces de activación
y no se usaron tokens reales. El servidor de previsualización no envía correo.

[Recuperación móvil](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/correo-recuperacion-movil.png),
[invitación móvil](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/correo-invitacion-movil.png)
y [aviso de cita](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/correo-cita-movil.png).
La carpeta externa `previews/` incluye HTML y texto de cada muestra.

Había 33 archivos previos modificados o nuevos en el backend. Se guardaron
sus bytes y el parche anterior antes de adaptar pruebas. **32 conservaron
su SHA-256 exacto**. En el test previo de citas solo se reemplazaron dos
lecturas directas de String por una lectura recursiva de la parte `text/plain`
y se añadió ese helper; se conservaron los demás cambios anteriores. El
[control de preservación](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/backend-plantillas-correo-2026-10-23/preexisting-preservation.json)
y su parche específico permiten comprobarlo.

Esta evidencia no acredita renderizado en Gmail/Outlook, recepción externa,
entrega en producción ni aceptación completa de los recorridos de cuenta.
El incremento mejora las representaciones de los correos existentes.
