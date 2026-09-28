# OpenAPI generado — incremento 0.7.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. El incremento 0.7.0 añade ocupación de disponibilidad, solicitud, confirmación, consulta e historial de citas, reasignación administrativa y acceso justificado al correo del propietario. Conserva Bearer JWT y los permisos opacos de uso restringido para restablecer o establecer contraseñas. Frontend, urgencias, atención clínica e integración COMVEZCOL quedan fuera de este incremento.

La [guía de personal e invitaciones](../personal-e-invitaciones.md) concreta permisos, estados, URL placeholder, credenciales externas, archivos y cuotas. La [guía de disponibilidad veterinaria](../disponibilidad-veterinaria.md) documenta turnos, permisos y ocupación real por citas. La [guía de citas y agenda](../citas-y-agenda.md) define estados, permisos, corte, reasignación y contacto. La [guía de mascotas y recuperación](../mascotas-y-recuperacion.md) conserva el alcance anterior. Recuperación e invitaciones están deshabilitadas por defecto hasta configurar sus canales y secretos. El Sandbox de Mailtrap se configuró en desarrollo y Esteban confirmó los casos manuales de recuperación; esa evidencia no sustituye la revisión del contrato con los dos frontends.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles.

El usuario de pruebas debe poder crear y eliminar sus propios esquemas en `grownupsvet_test`. Las pruebas de concurrencia y ciclo de vida de disponibilidad, transacciones de citas y entrega de avisos crean esquemas con un UUID exclusivo, aplican Flyway y los eliminan al terminar. Usan conexiones/transacciones independientes y conservan activas las restricciones del historial; no necesitan modificar `grownupsvet_dev`. Ejecutar:

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
