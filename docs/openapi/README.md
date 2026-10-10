# OpenAPI generado — incremento 0.9.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. El incremento **0.9.0** añade avisos generales, recordatorios P12 y su consulta privada sobre las citas 0.7.0 y los cambios administrativos P07 de 0.8.0; incluye recuperación con correo durable y preparación de configuración/empaquetado de nube. Conserva Bearer JWT y los permisos opacos de uso restringido para restablecer o establecer contraseñas. Frontend, urgencias, atención clínica e integración COMVEZCOL quedan fuera. Preparar el backend para Google Cloud no acredita despliegue ni aceptación de los dos procesos integrados.

La [guía de personal e invitaciones](../personal-e-invitaciones.md) concreta permisos, estados, URL de activación, credenciales externas, archivos y cuotas. La [guía de disponibilidad veterinaria](../disponibilidad-veterinaria.md) documenta turnos, permisos y ocupación real por citas. La [guía de citas y agenda](../citas-y-agenda.md) define estados, permisos, corte, cambios y P12; la [guía de mascotas y recuperación](../mascotas-y-recuperacion.md) describe el correo durable de recuperación. Recuperación e invitaciones están deshabilitadas por defecto hasta configurar sus canales y secretos. Esteban acordó Mailtrap Sandbox como evidencia remota suficiente para esta entrega; los ensayos anteriores de recuperación no demuestran los nuevos avisos P12 ni sustituyen la revisión del contrato con los dos frontends.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles.

El usuario de pruebas debe poder crear y eliminar sus propios esquemas en `grownupsvet_test`. Las pruebas de concurrencia y ciclo de vida de disponibilidad, transacciones de citas, entrega de avisos y recuperación durable crean esquemas con un UUID exclusivo, aplican Flyway y los eliminan al terminar. Usan conexiones/transacciones independientes y conservan activas las restricciones del historial; no necesitan modificar `grownupsvet_dev`. Ejecutar:

```powershell
.\mvnw.cmd verify
```

Las pruebas HTTP consultan el documento real con los filtros de seguridad activos y escriben `target/generated-openapi/openapi.json`. Conservan respuestas de registro, login, perfil, mascotas, recuperación, personal, invitaciones, disponibilidad y errores, obtenidas de cuentas ficticias. El permiso opaco en el ejemplo de recuperación se sustituye por un valor ficticio de la misma forma; los ejemplos de invitaciones no exponen el token. Verifican tipos, nulabilidad, campos obligatorios, credenciales de escritura únicamente, permisos, propiedad, revocación, uso único y rechazo de propiedades adicionales. Los controladores exclusivos de prueba se excluyen del documento.

La comprobación independiente del documento OpenAPI 3.1 y los ejemplos necesita Python y estas dependencias de desarrollo:

```powershell
python -m venv target/openapi-validation-env
& target/openapi-validation-env/Scripts/python.exe -m pip install -r scripts/requirements-openapi.txt
& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py
```

Después de que ambos comandos de verificación terminen correctamente, actualizar el snapshot:

```powershell
Copy-Item -LiteralPath target/generated-openapi/openapi.json -Destination docs/openapi/openapi.json
```

## Incremento 0.9.0 — avisos P12 y consulta privada

`GET /api/v1/appointments/{appointmentId}/notifications` añade la novena operación de citas. Propietario: solo avisos de su cita destinados a él; administrador: seguimiento autorizado, sin correos, destinatarios, payloads, leases o detalles SMTP privados. Veterinario y superadministrador reciben `403`; una cita ajena/inexistente recibe `404`. Conserva la paginación desde cero, máximo 100 elementos y orden estable por creación/identificador, con respuestas privadas y no almacenables.

`AppointmentNotificationResponseDTO` documenta `id`, `appointmentId`, `notificationType`, `status`, `scheduledAt`, `nextAttemptAt`, `sentAt`, `attemptCount` y `errorCode`, presentes incluso cuando admiten null. `AppointmentNotificationPageResponseDTO` contiene `items`, `page`, `size`, `totalElements` y `totalPages`. `nextAttemptAt` tiene valor solamente para `PENDING`; no sustituye la fecha programada. Los estados son `PENDING`, `SENDING`, `SENT`, `FAILED` y `SUPERSEDED`. `SENT` acredita aceptación SMTP y no recepción/lectura en un buzón.

Los avisos acordados informan confirmación, rechazo, cancelación humana o por corte y cambio de horario, conservando la reasignación excepcional. No se crea correo de inasistencia. Una cita `CONFIRMED` programa recordatorio 24 horas antes: en la frontera exacta sí se crea; confirmar con menos de 24 horas solo genera confirmación. Cambiar de programación invalida pendientes y recalcula el recordatorio sin crear uno retroactivo. Propietarios desactivados siguen recibiendo los cambios y recordatorios de sus citas confirmadas vigentes, sin obtener acceso a la API.

V17 conserva V1–V16 y añade `scheduled_at` y tipos P12 a la cola existente. La intención usa el evento persistido, destinatario y tipo; replay y sondeos no generan otra intención. La entrega conserva recuperación, leases, intentos acotados y SMTP fuera de transacción. La revisión de programación no cambia por un acceso auditado al correo. Las tareas nuevas se crean desde eventos efectivos; no hay backfill automático de citas anteriores. V18 añade correo durable/cifrado de recuperación, conservando sus rutas y guardas anteriores.

`FRONTEND_BASE_URL` es opcional para los avisos; vacío significa texto sin enlace, y no impide arrancar el backend. `CORS_ALLOWED_ORIGINS` permite los orígenes concretos de los portales, sin wildcard. El formulario de activación conserva su variable/ruta independiente. La preparación de `prod`, contenedor y nube no modifica por sí sola las URLs de clientes ni prueba integración.

Los cuatro ejemplos HTTP nuevos son `appointment-notification-page.json`, `appointment-notification-admin-page.json`, `appointment-notification-empty-page.json` y `appointment-notification-forbidden-error.json`. El validador independiente comprueba required, tipos, enums, nulabilidad, privacidad y respuestas de la nueva operación además de las regresiones de contratos anteriores.

El documento generado y validado registra **OpenAPI 3.1.0**, contrato **0.9.0**, **59 operaciones en 40 rutas** y **41 respuestas HTTP reales**. El snapshot se actualizó desde Java después de la última suite y validación, con SHA-256 idéntico a la salida generada. La suite definitiva contiene **508 pruebas**, sin fallos, errores ni omisiones. El [informe del incremento](../verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md) registra la evidencia y los límites.

Para revisar con ambos consumidores: distinguir fecha prevista de próximo intento; mostrar los estados de envío con su alcance real en Sandbox; manejar `401`/`403`/`404`; comprobar filtrado del propietario antes de paginar y administración sin campos privados. Registrar revisión de los tres e integración/aceptación separadamente. Estos resultados locales no acreditan Google Cloud desplegado, captura remota Mailtrap ni pantallas frontend.

## Incremento histórico 0.8.0 — cambios administrativos P07

`PATCH /api/v1/appointments/{appointmentId}/status` añade `CANCELLED` desde pendiente/confirmada antes del inicio y `NO_SHOW` desde confirmada al finalizar el turno, con motivo obligatorio y versión observada. `POST /api/v1/appointments/{appointmentId}/reschedulings` recibe ocho campos obligatorios de intención, destino, versiones, motivo y acuerdo previo. La reprogramación general solo ocurre antes del inicio original, hacia un turno publicado y libre con fecha local de mañana al día 60; conserva identidad, participantes y estado. Una solicitud vencida no puede eludir el corte reprogramándose.

Las operaciones siguen exclusivas del administrador con `APPOINTMENT_MANAGE`; no se añaden permisos ni autogestión al propietario. `NO_SHOW` y `RESCHEDULED` aparecen en los enums de estado/eventos y en las respuestas reales. La reprogramación utiliza idempotencia persistida y actor en el fingerprint; los fallos conservan la reserva original. V16 conserva las migraciones previas y amplía checks de estados, eventos, constancia e intenciones.

Los ejemplos nuevos incluyen cancelación, inasistencia, reprogramación, replay e historial administrativo. El validador independiente comprueba la operación nueva, campos/tipos/required del DTO, estados/eventos y respuestas HTTP. Los avisos existentes se invalidan por el último cambio real de horario/asignación, incluidos payloads antiguos; este incremento no añade nuevos mensajes P12.

Para revisar el contrato con ambos consumidores: actualizar el manejo de `NO_SHOW` y eventos nuevos; gestionar `409` por versión, plazo, capacidad o intención; mostrar resultados de agenda/historial a propietarios y mantener acciones de cambio solo en gestión administrativa. Revisar los ejemplos generados y registrar observaciones/aceptación de los tres integrantes. Esa revisión compartida y la integración frontend permanecen pendientes aunque el contrato esté generado y validado.

El 08/10/2026 la suite completa pasó **462 pruebas** y la validación independiente comprobó **37 respuestas HTTP**, OpenAPI 3.1.0 y las 58 operaciones/39 rutas del contrato 0.8.0. Después de emparejar los ejemplos de aplicación/replay se repitieron las 40 pruebas HTTP de citas satisfactoriamente. El snapshot coincide con la salida generada. El [informe del incremento P07](../verificacion-cambios-citas-2026-10-08.md) distingue esa evidencia local de publicación, revisión y aceptación.

## Incremento 0.7.0 — citas, ocupación y reasignación

La especificación OpenAPI continúa en 3.1.0 y el contrato de la aplicación pasa a 0.7.0. El backend incorpora solicitud idempotente, consulta y eventos, confirmación/rechazo, corte diario recuperable, ocupación de disponibilidad, reasignación/reprogramación acordada, avisos durables y revelación auditada del correo. La respuesta de opciones de turno ahora incluye su `version`. `APPOINTMENT_WORKDAY_START_TIME` es obligatorio y la hora exacta se configura por entorno; no existe una hora comercial predeterminada. Para enviar avisos se requieren `MAIL_FROM` y SMTP.

Las pruebas generan ejemplos reales autenticados para creación, repetición, conflicto, páginas, eventos y acceso al correo. El validador independiente comprueba OpenAPI 3.1, ejemplos, campos requeridos, tipos, enums, nulabilidad y errores de las siete operaciones de citas. Los campos del historial permanecen presentes cuando su valor es nulo. El informe [del incremento](../verificacion-citas-2026-09-28.md) incluye la cantidad ejecutada y la trazabilidad a SCRUM-36, SCRUM-39 y SCRUM-79. La revisión compartida del contrato por los tres integrantes permanece pendiente.

## Incremento 0.6.0 — disponibilidad veterinaria

La versión de la especificación OpenAPI se conserva en 3.1.0; la versión del contrato de la aplicación pasa a 0.6.0. Las rutas nuevas cubren turnos individuales y lotes, lectura administrativa o propia, cambio de hora, cambio de estado, auditoría y consulta de opciones para propietarios. Las respuestas de propietario excluyen datos administrativos y solo consultan veterinarios activos con turnos publicados entre 2 horas y 60 días desde la consulta. Los turnos son fechas concretas de 30 minutos en `America/Bogota`; la ocupación por reservas queda para el incremento futuro de citas.

Las pruebas verifican el documento generado por springdoc. El validador independiente también valida respuestas reales de creación, páginas, auditoría y errores de disponibilidad. Copiar el snapshot únicamente después de que Maven y este validador terminen correctamente.

## Verificación del incremento 0.6.0

En la verificación final del 23 de septiembre de 2026, `mvnw.cmd verify` completó **396 pruebas**, sin fallos, errores ni omisiones, con Flyway V1–V11 sobre PostgreSQL de pruebas. Se comprobaron los límites exactos de consulta para propietarios (2 horas y 60 días), lotes de hasta 1000 turnos, cuadrícula y duración fija, aislamiento por rol, ocultamiento/restauración al deshabilitar/rehabilitar veterinarios, restricciones de base de datos e historial inmutable. Las pruebas nuevas cubren JSON estricto, cambios de hora y estado, carreras entre conexiones PostgreSQL distintas y rollback de un lote con conflicto tardío observado desde otra transacción.

El OpenAPI **3.1.0**, versión **0.6.0**, contiene **50 operaciones en 32 rutas**. **25 ejemplos de respuestas HTTP** pasaron el validador independiente de JSON Schema y OpenAPI. Los siete esquemas de disponibilidad también rechazaron objetos vacíos, omisiones de campos obligatorios, tipos numéricos incorrectos y enums inválidos; se conserva la nulabilidad documentada de eventos de creación. El snapshot se actualizó desde el documento generado tras completar ambas verificaciones. La [evidencia detallada](../verificacion-personal-disponibilidad-2026-09-23.md) identifica las pruebas y sus límites. La ocupación real por reservas, las pantallas frontend y la revisión compartida del contrato permanecen fuera de esta verificación.

Los incrementos anteriores 0.3.0 y 0.4.0 cubren acceso/perfil y mascotas/recuperación. El incremento 0.5.0 aportó el backend de personal a SCRUM-33/SCRUM-78; la revisión compartida permanece separada de esta verificación automatizada.

## Verificación del incremento 0.5.0

El 13 de septiembre de 2026, `mvnw.cmd verify` completó **334 pruebas**, sin fallos, errores ni omisiones, con Flyway V1–V9 y Hibernate sobre PostgreSQL de pruebas. Incluye invitaciones reales en persistencia con correo simulado, uso único concurrente, vencimiento, cuotas, cancelación, reintentos, protección de credenciales, estados y privacidad del personal. El validador PDF se comprobó con archivos malformados, acciones, límites y un documento comprimido que expandiría 512 MiB. Además, su ejecución desde el JAR empaquetado pasó una comprobación independiente.

El OpenAPI **3.1.0**, versión **0.5.0**, contiene **42 operaciones en 26 rutas** y pasó `openapi-spec-validator`. **18 respuestas HTTP** pasaron JSON Schema: las trece anteriores más administrador creado, veterinario creado, ficha profesional, invitación aceptada y error de invitación. Se verificaron tipos numéricos/booleanos, enums, nulabilidad y exclusión de rutas de prueba. El snapshot se actualizó desde la salida generada después de completar estas verificaciones.

No se ejecutaron las migraciones sobre `grownupsvet_dev`, no se enviaron invitaciones a destinatarios reales y no se implementó el formulario frontend. El enlace contiene el placeholder y el TODO aprobados; antes de usarlo con destinatarios debe configurarse el portal real. Las plantillas Thymeleaf y la consulta automática de COMVEZCOL permanecen aplazadas.

## Verificación del incremento 0.4.0

El 10 de septiembre de 2026, `mvnw.cmd verify` completó **264 pruebas**, sin fallos, errores ni omisiones. Incluye 53 casos HTTP de mascotas, 17 de recuperación, la recuperación deshabilitada, configuración/secretos y cinco casos de correo SMTP real exclusivamente en loopback. Las pruebas ejercitan PostgreSQL local, aislamiento entre propietarios, PATCH parcial/concurrente, cuotas, vencimiento, uso único y rechazo de JWT anteriores al restablecimiento.

El OpenAPI 3.1.0 generado, versión 0.4.0, contiene **16 operaciones** y pasó `openapi-spec-validator`; **13 respuestas HTTP** pasaron la comprobación independiente de JSON Schema. Se comprobó además que no contiene rutas exclusivas de pruebas y que sus booleanos, enteros y enums anulables coinciden con el contrato. El snapshot se copió al repositorio solo después de estas verificaciones.

Las pruebas automatizadas no incluyen Mailtrap remoto, pantallas frontend, despliegue ni revisión de los otros integrantes. Las migraciones V5/V6 se verificaron automáticamente en `grownupsvet_test`.

Posteriormente, el 10 de septiembre de 2026, se comprobó conexión STARTTLS y autenticación SMTP con el Sandbox de Mailtrap. El desarrollador reportó el arranque local y Swagger funcionando, un `204` de `POST /api/v1/auth/password-resets` (captura compartida) y `authentication_version = 1` mediante consulta SQL. En la conversación posterior, Esteban confirmó que probó satisfactoriamente los casos de contraseña nueva y anterior. Se registra como verificación manual reportada por el desarrollador; no sustituye la revisión del contrato por los tres integrantes. No se incorporan credenciales, códigos ni tokens reales a esta evidencia.
