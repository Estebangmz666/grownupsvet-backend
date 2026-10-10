# Verificación del incremento 0.9.0: procesos 1 y 2 y preparación de Google Cloud

Fecha del incremento: **9 de octubre de 2026**, `America/Bogota`. Objetivo de entrega: **18 de octubre de 2026**. Este informe corresponde a la implementación autorizada después del [plan](plan-implementacion-2026-10-09-procesos-1-2-google-cloud.md).

Documentación y revisión para publicación: **10 de octubre de 2026**. La última suite completa terminó a las **00:07:42 de Colombia**; las ejecuciones previas se conservan como evidencia histórica.

## Resultado y alcance

Se implementaron los avisos y recordatorios de citas P12, la persistencia cifrada del correo de recuperación y la configuración/containerización del backend para Google Cloud. Se conserva el alcance de **registro y gestión de usuarios/mascotas** y **gestión de citas veterinarias**. Clínica permanece fuera.

La verificación parte de `main` y del commit base `dffff9cc2d49ab6eaafd22ac7cab0ffba6870704`; el fetch previo a publicar confirmó ese mismo commit en `origin/main`, con cero commits exclusivos en ambos lados. Este informe describe el árbol verificado para la publicación autorizada por Esteban el 10/10; el commit y push efectivos y los estados de Jira se registran después en Git y en las tareas correspondientes. No hay un proyecto Google Cloud identificado, presupuesto confirmado, recursos provisionados o URL HTTPS desplegada; tampoco se verificaron los repositorios/pantallas de los dos frontends. Esteban aplazó expresamente el despliegue el 10/10; se conserva la preparación técnica y el objetivo de los dos procesos.

## Comportamiento implementado

### Citas

- El evento de negocio y la intención de correo se guardan en la misma transacción. Un rollback no deja una notificación de un cambio inexistente. La unicidad por evento, destinatario y tipo evita duplicar intenciones al repetir la operación.
- El propietario recibe avisos de confirmación, rechazo, cancelación administrativa o por corte diario y reprogramación; se conserva la reasignación excepcional. Inasistencia no envía correo. Cambios posteriores superan las tareas pendientes que ya no representan la programación vigente.
- Una confirmada programa recordatorio 24 horas antes. La frontera exacta es elegible; confirmar con menos de 24 horas omite el recordatorio atrasado. Reprogramar/reasignar supera el recordatorio anterior y solo programa uno nuevo cuando su momento aún no pasó. No se crean recordatorios retrospectivos de citas históricas.
- Antes de enviar se vuelven a comprobar tarea/lease, estado, horario y asignación. Un recordatorio ya programado y atrasado puede recuperarse mientras la cita siga vigente y futura. Reintentos SMTP son acotados; SMTP ocurre fuera de la transacción de negocio.
- Desactivar al propietario conserva los avisos y el recordatorio de sus citas vigentes. Su acceso continúa desactivado. El corte configurado para la entrega es **06:00 de Colombia**.
- `GET /api/v1/appointments/{appointmentId}/notifications` consulta estado de entrega con paginación: propietario de la cita, únicamente sus destinatarios; administrador, todos los avisos de esa cita. Veterinario y superadministrador carecen de ese acceso. No devuelve email, payload, token de lease ni errores internos. Usa las autoridades existentes y `Cache-Control: private, no-store`.
- Se conservan todas las reglas administrativas P07 de cancelación, reprogramación, acuerdo previo, inasistencia, historial, versiones e idempotencia.

### Recuperación

- La intención se persiste al crear el desafío o cambiar la contraseña y se despacha después del commit. El sondeo puede recuperar trabajo tras recrear el dispatcher; la cola ya no depende de memoria de una instancia.
- Código y contenido del correo se cifran con AES-GCM y una clave independiente, Base64 de 32 bytes, distinta del HMAC. Los identificadores forman parte de los datos autenticados. Un payload alterado no se acepta.
- Cuenta, versión de autenticación, desafío vigente, caducidad y lease se validan antes del envío. Reemplazar/consumir/vencer el desafío supera el código anterior; los estados terminales borran ciphertext y nonce. Se conservan cuotas, respuesta indistinguible para cuentas inexistentes/desactivadas, uso único y revocación del acceso anterior.
- Un primer fallo SMTP conserva el desafío para reintentar. Un fallo terminal solo puede eliminar el desafío correspondiente, sin borrar uno más nuevo.
- Si la cola está llena al completar un reset, la contraseña se cambia y queda una intención terminal `FAILED / DELIVERY_CAPACITY_EXHAUSTED`, sin contenido cifrado ni reintento automático. No afirmar que todos los avisos de reset se entregan bajo saturación.
- `scripts/configure-mailtrap.ps1` genera/preserva `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY` sin imprimir secretos. Su sintaxis se comprobó; no se ejecutó para modificar credenciales del usuario durante este incremento.

### Entorno y nube

- `FRONTEND_BASE_URL` y `CORS_ALLOWED_ORIGINS` permiten cambiar URL y orígenes exactos sin modificar Java. El backend inicial admite frontend vacío; los correos no agregan enlaces ficticios. Invitaciones habilitadas requieren `STAFF_ACTIVATION_URL` real en producción.
- `BACKEND_BASE_URL` fija el servidor público OpenAPI y el origen propio de Swagger. CORS se procesa antes de JWT; no concede permisos ni activa cookies.
- Perfil `prod`, escucha en `0.0.0.0:$PORT`, cierre ordenado, pool acotado y Cloud SQL Java Connector con identidad del runtime. Salud pública mínima mediante GET liveness/readiness; readiness incluye DB.
- Imagen Linux Java 25, usuario 10001, temporales escribibles y sin archivos secretos locales en el contexto Docker. Archivos persistentes de usuarios/personal permanecen en PostgreSQL.
- Script PowerShell para Cloud Run con imagen existente y referencias versionadas de Secret Manager. El modo offline no requiere CLI ni ejecuta acciones de nube. La propuesta inicial reserva CPU continua, mínimo/máximo 1 instancia, 2 GiB, 1 vCPU y concurrencia 8 para los workers; su capacidad y costo reales siguen pendientes de medición.
- `TRUSTED_PROXY_PATTERN=^$` ignora headers de proxies hasta comprobar la cadena real. No se acreditó identificación de IP cliente detrás de Google Cloud. El script reemplaza las variables ordinarias con su manifiesto: conservar overrides al redeploy.

V17 amplía tipos y fecha prevista de notificaciones; V18 crea la outbox de recuperación. V1–V16 se conservaron sin editar. Los workers 0.8 no procesan los tipos P12 ni la nueva outbox; un rollback exige una revisión 0.9 compatible con esquema y claves, sin borrar/deshacer migraciones.

## Verificación y evidencia

La suite `mvnw.cmd verify` para publicación terminó el **10/10/2026 a las 00:07:42 de Colombia** con **508 pruebas, 0 fallos, 0 errores y 0 omisiones**, y generó el JAR. Se corrigió una descripción OpenAPI histórica que todavía negaba el aviso P12 de reprogramación; se regeneró y validó el contrato desde Java. La primera suite había pasado 507; la revisión posterior detectó una inversión de bloqueos entre mantenimiento de correos vencidos y reenvío. Una regresión con conexiones PostgreSQL, trigger temporal y `pg_blocking_pids` reprodujo el deadlock antes de corregirlo. El mantenimiento ahora suspende cualquier transacción envolvente y cada purga JDBC independiente libera sus bloqueos antes de tocar otra tabla. Después pasaron los 43 tests focalizados de recuperación y las suites completas de 508. No se modificaron migraciones para resolverlo.

| Comprobación | Resultado y alcance |
|---|---|
| Maven con JDK 25 y PostgreSQL de pruebas | Ejecución para publicación: **508/0/0/0**, `target/procesos-1-2-publication-verify.log`; JAR empaquetado. Regresión antes/después en `target/recovery-maintenance-before.log` y `target/recovery-maintenance-after.log` |
| Contrato generado | OpenAPI 3.1.0, aplicación 0.9.0, **59 operaciones / 40 rutas**, sin rutas de prueba |
| Validación independiente | **41 respuestas HTTP reales** contra sus esquemas; campos requeridos, enteros, enums y nulabilidad en 7 esquemas de disponibilidad y 7 de citas, operaciones/replay y consulta de avisos. Log `target/procesos-1-2-publication-openapi-validation.log` |
| Snapshot | Copiado desde `target/generated-openapi/openapi.json` después de la última suite y validación; SHA-256 idéntico: `AE2997F6CEDC9B5A48E290489C6E61EC1C6B0C3F2451C38DA15B3FB039B731B1`. Fuente editable en Java, sin edición manual del JSON |
| PowerShell | Parser: 0 errores en `deploy-google-cloud.ps1` y `configure-mailtrap.ps1`. Validación offline positiva con referencias ficticias y casos inválidos rechazados; `CloudActionsExecuted=false` |
| Docker | Imagen `grownupsvet-backend:0.9.0-local`, Linux amd64 / Java 25; reconstruida para publicación con la descripción OpenAPI corregida, `target/procesos-1-2-publication-docker-build.log`. ID local/manifest: `sha256:8f71d7ea390a39ad05ce00395bec50dfbceffeddd2135685a5ae767e3f57297e`; sin publicación en un registro de imágenes |
| Smoke HTTP aislado | Repetido satisfactoriamente para publicación: PostgreSQL 18.2 temporal, Flyway V1–V18, readiness/liveness `UP`, OpenAPI 0.9.0, CORS y recorrido registro/login/mascota, UID 10001, 2 GiB/1 vCPU. Credenciales/claves ficticias. `target/procesos-1-2-publication-container-smoke.log` comprueba además la descripción P12 corregida |
| PDF desde JAR Linux | Dos inspecciones concurrentes con JVM hijas aceptaron PDF válido; PDF malformado rechazado. No usa las clases del host para validar |
| Limpieza del smoke | Se eliminaron solo contenedores/red con etiqueta propia verificada y archivos temporales de claves/env ficticios; no quedan recursos de ese smoke |

Los logs y helpers de `target` son evidencia local generada y están ignorados por Git. El smoke usa configuración aislada de desarrollo: acredita el runtime del contenedor y esos recorridos, no una conexión Cloud SQL, secretos reales, SMTP remoto o despliegue productivo.

Durante verificación se corrigieron dos fixtures: un veterinario desactivado debe responder 401, y la prueba de «turno de hoy» ahora usa reloj fijo para no cambiar de significado cerca de medianoche. No se modificaron las reglas productivas por esos fallos.

## Pendiente para entregar el 18

1. Despliegue aplazado por Esteban el 10/10. Al retomarlo, crea/verifica cuenta y proyecto, envía **ID** y presupuesto máximo; comprobar créditos/facturación efectivos. Seguir la [guía Google Cloud](google-cloud.md).
2. Aprovisionar SQL, registro, identidad y secretos; publicar revisión/imagen comprobadas y desplegar. Conservar la misma clave JWT y claves de cifrado entre revisiones.
3. Verificar HTTPS, Cloud SQL, proxy/IP, permisos, uploads, restauración y reinicio con trabajo pendiente. Comprobar workers sin tráfico y recordatorio real de 24 horas.
4. Capturar mensajes en **Mailtrap Sandbox** con configuración real, siguiendo la [guía de comprobación](verificacion-mailtrap.md). Las pruebas usan SMTP simulado y no prueban llegada a Sandbox ni a buzones externos.
5. Recibir las URLs de ambos frontends y del formulario de activación; configurar entorno, integrar P1/P2 y validar errores, permisos, accesibilidad, revisión compartida y guion académico.
6. Registrar publicación Git y actualización Jira del incremento con referencias verificadas: publicar el backend no permite cerrar aceptación de procesos integrados.

No hay entrega remota ni integración frontend acreditadas por este informe. La preparación técnica comprobada permite continuar con esas dependencias sin rehacer las reglas confirmadas.
