# Backend de procesos 1 y 2 en Google Cloud

**Decisión del 15/10/2026:** para la entrega académica se eligió Cloud Run con PostgreSQL gratuito de Render durante aproximadamente 30 días, dentro de los US$300 gratuitos totales y sin actualizar a una cuenta de pago. Consultar [la guía de esta alternativa](google-cloud-render.md). Esta página conserva la configuración de Cloud SQL; preparar archivos no significa que el despliegue se haya ejecutado.

Preparación para la entrega del 18 de octubre de 2026. Esta guía conserva Java 25, Spring Boot, PostgreSQL y JWT RS256. **Mailtrap Sandbox** sigue como opción predeterminada del script; el entorno Cloud Run usa **Gmail SMTP** desde el 16/10/2026. La revisión `grownupsvet-backend-00004-mzr` está lista, recibe el 100 % del tráfico y produjo un correo de recuperación cuya llegada a Recibidos confirmó el usuario. Consultar la [verificación de Gmail](verificacion-gmail-2026-10-16.md); el registro del 15/10 conserva su evidencia histórica de Sandbox.

Antecedente: el **10/10/2026 Esteban aplazó el despliegue**; no se crearon recursos de nube durante aquella publicación del backend. El despliegue posterior con Render se documenta por separado.

## 1. Comenzar sin una cuenta o proyecto preparado

1. Esteban abre [Comenzar en Google Cloud](https://cloud.google.com/free), inicia sesión con su cuenta de Google y completa directamente los requisitos de registro/facturación que aparezcan. Contraseñas y datos de pago no se comparten por chat ni se guardan en el repositorio.
2. Comprobar créditos y condiciones de la cuenta en la consola. La oferta pública no demuestra elegibilidad individual ni vuelve gratuitos todos los recursos. En **Administrar recursos → Crear proyecto**, escribir **GrownupsVet**; seleccionar la cuenta de facturación correspondiente y, si se ofrece para una cuenta personal, **Sin organización**. Crear el proyecto y copiar su **ID**, no solamente el nombre visible. Consultar los [pasos oficiales de creación](https://docs.cloud.google.com/resource-manager/docs/creating-managing-projects).
3. Cotizar Cloud Run activo y Cloud SQL, almacenamiento/backups, imágenes, secretos, logs y red. Acordar presupuesto mensual y tiempo de disponibilidad después de la entrega. Las alertas de presupuesto **no detienen automáticamente** el gasto. No crear una instancia SQL facturable antes de aprobar esos datos.
4. Usar una región común para API, SQL y registro. `us-central1` es el candidato del plan, no una región ya provisionada. No aceptar una edición SQL grande por defecto sin revisar su cotización.
5. Si se usará la CLI, instalarla desde [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) y autenticar la cuenta autorizada. El script del proyecto no instala herramientas, autentica cuentas ni cambia la configuración global de `gcloud`.

Referencias: [oferta/condiciones](https://cloud.google.com/free), [calculadora](https://cloud.google.com/products/calculator), [Cloud SQL](https://cloud.google.com/sql/pricing), [presupuestos](https://docs.cloud.google.com/billing/docs/how-to/budgets).

## 2. Prerrequisitos que se aprovisionan por separado

- APIs de Cloud Run, Cloud SQL Admin, Artifact Registry y Secret Manager habilitadas en el proyecto elegido.
- Registro Docker de Artifact Registry e imagen publicada desde un commit comprobado. El script de despliegue consume una imagen existente; no publica código fuente ni construye/pushea por su cuenta.
- Instancia Cloud SQL PostgreSQL y base de aplicación vacía, usuario exclusivo y privilegios para el esquema y Flyway. No copiar credenciales/datos personales de `grownupsvet_dev`. Conservar backups y comprobar una restauración en un destino aislado.
- Cuenta de servicio de ejecución, con `roles/cloudsql.client` y acceso Secret Manager **solo a los secretos necesarios**. La persona que despliega debe poder administrar Cloud Run y usar esa cuenta de servicio. Usar la identidad del runtime; no descargar una llave JSON.
- Secretos ya creados, con versiones numéricas. Para JWT, guardar el PKCS12 como secreto binario; su ruta en el contenedor será `/var/secrets/jwt/jwt-signing.p12`. Contraseña y alias deben corresponder al archivo.

El conector Java oficial `com.google.cloud.sql:postgres-socket-factory:1.30.0` está fijado en Maven; utiliza credenciales de aplicación de la cuenta de servicio. El JDBC sigue siendo PostgreSQL, con `socketFactory`, `cloudSqlInstance` y refresco `lazy` en Hikari. El conector no crea una ruta de red: esta guía usa IP pública SQL y autenticación/cifrado del conector, sin listas abiertas de IP cliente. Si se elige IP privada, preparar y verificar primero la conectividad VPC correspondiente.

[Conector 1.30.0](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/blob/v1.30.0/docs/jdbc.md), [Cloud Run a PostgreSQL](https://docs.cloud.google.com/sql/docs/postgres/connect-run), [secretos](https://docs.cloud.google.com/run/docs/configuring/services/secrets).

## 3. Configuración de aplicación

Activar explícitamente `SPRING_PROFILES_ACTIVE=prod`. `dev` conserva PostgreSQL local, Sandbox y configuración de desarrollo; las pruebas siguen usando su base/perfil separados. `prod` escucha en `0.0.0.0` y `PORT` (8080 si no se suministra), Hikari máximo 5 por defecto, mínimo 0, y timeouts finitos. Flyway aplica únicamente migraciones nuevas; Hibernate valida el esquema.

| Variable | Valor/origen |
|---|---|
| `DB_NAME`, `DB_USERNAME` | Base y usuario de Cloud SQL; `DB_PASSWORD` desde Secret Manager |
| `SPRING_DATASOURCE_URL` | Override opcional; por defecto `jdbc:postgresql:///${DB_NAME}`. No usar la antigua `DB_URL` ni incluir contraseña en la URL |
| `CLOUD_SQL_INSTANCE_CONNECTION_NAME` | ID exacto `proyecto:región:instancia` de la consola |
| `CLOUD_SQL_IP_TYPES` | `PUBLIC` inicialmente; `PRIVATE` solo con conectividad preparada |
| `DB_MAXIMUM_POOL_SIZE` | 5 por defecto; revisar capacidad y revisiones solapadas antes de ampliarlo |
| `JWT_KEYSTORE_PATH` | `/var/secrets/jwt/jwt-signing.p12`, archivo montado y legible por usuario 10001 |
| `JWT_KEYSTORE_PASSWORD`, `JWT_KEY_PASSWORD` si difiere | Secret Manager. Conservar `JWT_KEY_ALIAS`, emisor y audiencia entre revisiones |
| `MAIL_HOST`, `MAIL_PORT` | Sandbox: `sandbox.smtp.mailtrap.io`, 2525. Gmail: `smtp.gmail.com`, 587 |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Sandbox: credenciales propias de Mailtrap y remitente válido. Gmail: dirección Gmail completa, contraseña de aplicación y esa misma dirección como remitente. Usuario/contraseña desde Secret Manager; STARTTLS obligatorio y timeouts 5 s |
| `PASSWORD_RECOVERY_ENABLED` | Habilitar para completar P1 |
| `PASSWORD_RECOVERY_HMAC_SECRET`, `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY` | Secretos independientes; clave de payload cifrado estable y distinta del HMAC |
| `STAFF_INVITATIONS_ENABLED`, `STAFF_INVITATION_ENCRYPTION_KEY` | Habilitar cuando el formulario real esté listo; secreto estable entre revisiones |
| `APPOINTMENT_WORKDAY_START_TIME` | 06:00, `America/Bogota`, confirmado para esta entrega; fallback solo en `prod` |
| `APPOINTMENT_REMINDER_ADVANCE` | `PT24H`; el backend valida el acuerdo de 24 horas |
| `FRONTEND_BASE_URL` | Base opcional del portal del propietario, inicialmente vacía |
| `CORS_ALLOWED_ORIGINS` | Orígenes HTTPS exactos separados por coma de ambos portales; inicialmente vacío y cerrado a otros navegadores |
| `STAFF_ACTIVATION_URL` | URL completa real del formulario de gestión. Es obligatoria al habilitar invitaciones; no se inventa la ruta frontend |
| `BACKEND_BASE_URL` | URL pública HTTPS de la API, opcional en primer despliegue. Fija el servidor OpenAPI y permite su propio origen para Swagger tras terminar TLS en el proxy |
| `TRUSTED_PROXY_PATTERN` | `^$` inicialmente: no confiar en headers de un proxy no verificado. Configurar únicamente una cadena real comprobada |

El perfil productivo rechaza SMTP incompleto, hosts distintos de Mailtrap Sandbox o Gmail, URLs de ejemplo/locales al usarlas, activación ausente con invitaciones habilitadas y confianza indiscriminada en proxies. Gmail exige puerto 587 y que usuario y remitente identifiquen la misma cuenta; el script selecciona 2525 para Sandbox. Puede arrancar sin URL frontend y sin orígenes CORS; los avisos no agregan enlaces rotos. Invitar personal deshabilitado en esa primera revisión técnica **no acredita P1 completo**.

CORS resuelve preflight antes de JWT y autoriza métodos/headers del contrato, incluido `Authorization`; no usa cookies ni `Access-Control-Allow-Credentials`. Un origen permitido no otorga sesión o permisos. `BACKEND_BASE_URL` permite solo el origen de la propia API, no un wildcard.

Después del primer despliegue, obtener su URL, pasarla como `BackendBaseUrl` y crear la siguiente revisión con **la misma imagen**. Al recibir los frontends, modificar `FrontendBaseUrl`, `CorsAllowedOrigins` y `StaffActivationUrl` mediante parámetros/env: no editar Java ni reconstruir la imagen. La API de activación permanece igual.

## 4. Contenedor, memoria y procesos de fondo

`Dockerfile` compila con Temurin Java 25 y ejecuta el JAR con JRE 25 como usuario 10001. La imagen no incluye credenciales, tests ni `target` local. `/app/tmp` es escribible; fotos/diplomas persistentes permanecen en PostgreSQL `BYTEA`.

El validador PDF ejecuta hasta dos JVM hijas y crea temporales. Se conservan límites de cada hijo; el heap principal se acota al 35 % de la memoria del contenedor. **2 GiB, 1 vCPU y concurrencia 8 son configuración inicial para medir**, no una garantía de capacidad. Comprobar uploads válidos/malformados/concurrentes desde el JAR Linux y limpieza de temporales. La imagen no utiliza un runtime sin ejecutable `java`.

Los sondeos de citas, correo, corte e invitaciones necesitan CPU fuera de las solicitudes. Cloud Run usa `--no-cpu-throttling`, mínimo 1 y máximo 1 **a nivel de servicio**. No usar mínimo 0 o CPU limitada a peticiones mientras se prometen recordatorios/corte operativos. Reinicios y revisiones pueden ocurrir: comprobar recuperación desde las colas PostgreSQL y estabilidad de claves, no contar con el filesystem como estado durable.

Salud pública limitada a **GET** `/actuator/health/liveness` y `/actuator/health/readiness`, sin detalles/componentes/secretos. Liveness verifica estado interno; readiness también verifica DB. Los demás recursos de Actuator conservan autenticación y solo se expone `health`. El script configura probes contra esos paths en el puerto principal.

[Contrato de contenedor](https://docs.cloud.google.com/run/docs/container-contract), [CPU/facturación](https://docs.cloud.google.com/run/docs/configuring/billing-settings), [mínimos](https://docs.cloud.google.com/run/docs/configuring/min-instances), [salud Spring](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

## 5. Script de despliegue y validación offline

`scripts/deploy-google-cloud.ps1` requiere identificadores, imagen existente, remitente, presupuesto aprobado y **referencias** de secretos `nombre:versión-numérica`, nunca sus valores. Sin `-Execute` solo valida localmente; `-ValidateOnly` explicita ese modo y no requiere `gcloud`, autenticación o red.

Preparar una hashtable de parámetros con `ProjectId`, `Region`, `ServiceName`, `ImageUri`, `CloudSqlInstanceConnectionName`, `ServiceAccountEmail`, `DatabaseName`, `DatabaseUsername`, `MailFrom`, `ApprovedMonthlyBudgetUsd` y `SecretBindings`. `MailProvider` admite `Mailtrap` (predeterminado, Sandbox 2525) o `Gmail` (SMTP 587); no modifica el proveedor de base de datos. Los nombres de bindings necesarios son:

- `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `JWT_KEYSTORE_PASSWORD` y `/var/secrets/jwt/jwt-signing.p12`.
- Con recuperación habilitada: `PASSWORD_RECOVERY_HMAC_SECRET`, `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY`.
- Con `-EnableInvitations`: `STAFF_INVITATION_ENCRYPTION_KEY` y `StaffActivationUrl` real.
- Con `-EnableBootstrap`: `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`; sin esa opción, no incluir esos bindings.
- `JWT_KEY_PASSWORD` es opcional si la clave usa otra contraseña; `JwtKeyAlias` debe coincidir con el PKCS12.

### Gmail para destinatarios reales

1. Usar la cuenta Gmail acordada con verificación en dos pasos activa y una contraseña de aplicación. Guardar la dirección completa y la contraseña de aplicación directamente como secretos de Secret Manager, fuera de Git y del chat; no usar la contraseña normal de Google. Mantener el acceso de la cuenta de servicio limitado a esos secretos.
2. Referenciar las **versiones numéricas** creadas mediante `SecretBindings['MAIL_USERNAME']` y `SecretBindings['MAIL_PASSWORD']`, con formato `nombre-secreto:version`. Conservar los secretos Sandbox para poder seleccionar ese canal posteriormente. No reemplazar referencias por valores ni usar `latest`.
3. Establecer `MailProvider = 'Gmail'` y `MailFrom` con la **misma dirección Gmail** guardada en el secreto del usuario. El script selecciona `smtp.gmail.com:587`; la aplicación mantiene autenticación y STARTTLS obligatorio.
4. Construir/publicar una imagen que incluya el validador compatible con Gmail, validar los parámetros offline y desplegar la revisión autorizada. Cambiar solo secretos/variables no elimina las restricciones de una imagen anterior.
5. Comprobar salud y un flujo de correo con un destinatario de prueba acordado. Registrar aceptación SMTP y recepción real en el buzón (incluida la carpeta de spam) como evidencias distintas. Hasta completar esa comprobación, no declarar entregas reales verificadas.

Ejemplo de selección sobre la hashtable privada ya preparada; los nombres y versiones son referencias ilustrativas, no credenciales:

```powershell
$deploymentParameters.MailProvider = 'Gmail'
$deploymentParameters.MailFrom = $gmailAddress # dirección real de la cuenta acordada
$deploymentParameters.SecretBindings['MAIL_USERNAME'] = 'grownupsvet-mail-username:2'
$deploymentParameters.SecretBindings['MAIL_PASSWORD'] = 'grownupsvet-mail-password:2'
```

Para pruebas aisladas, `MailProvider = 'Mailtrap'` conserva Sandbox y exige sus propias referencias de usuario/contraseña y un remitente válido. No mezclar las credenciales de ambos proveedores. El modo `ValidateOnly` comprueba los parámetros/referencias y muestra proveedor, host y puerto; no lee secretos, autentica SMTP ni envía mensajes.

**Resultado del 16/10/2026:** Gmail se activó con versiones numéricas `2` de los dos secretos SMTP existentes, sin permisos IAM nuevos. Se preservaron las demás variables y los recursos de Cloud Run: 1 vCPU, 2 GiB, mínimo 1, máximo 1 y concurrencia 8. Readiness/liveness respondieron `200 UP`, OpenAPI `200` con contrato `0.9.0` y 41 rutas, y mascotas sin JWT respondió `401`. La recuperación en nube respondió `202`, quedó `SENT` al primer intento y el usuario confirmó recepción real. Detalles, digest y límites en el [registro de verificación](verificacion-gmail-2026-10-16.md).

Fuentes oficiales: [Gmail SMTP y TLS](https://developers.google.com/workspace/gmail/imap/imap-smtp), [contraseñas de aplicación](https://support.google.com/accounts/answer/185833?hl=es), [Secret Manager en Cloud Run](https://docs.cloud.google.com/run/docs/configuring/services/secrets). Google revoca las contraseñas de aplicación al cambiar la contraseña de la cuenta; si ocurre, crear una nueva versión del secreto y actualizar la referencia de la revisión.

Una vez preparada esa hashtable local, las invocaciones son:

```powershell
.\scripts\deploy-google-cloud.ps1 @deploymentParameters -ValidateOnly
# Solo después de revisar proyecto, presupuesto, secretos, imagen y autorización de despliegue:
.\scripts\deploy-google-cloud.ps1 @deploymentParameters -Execute
```

El modo de ejecución despliega la revisión y consulta su URL. No crea proyecto, instancia SQL, usuario, secretos, registro, permisos, presupuesto o imagen, ni cobra/configura pagos por su cuenta. Sí realiza una operación Cloud Run que puede generar costo, cambia variables/revisión y habilita invocación pública, conservando JWT de Spring. El presupuesto declarado no es un límite de gasto implementado por el script.

El manifiesto de variables es completo: `--env-vars-file` reemplaza las variables ordinarias del servicio, y conserva únicamente las representadas por los parámetros del script. Antes de reutilizarlo sobre un servicio con overrides manuales adicionales, incorporarlos al manifiesto y revisar la configuración prevista; no asumir que conservará esos valores por sí solo. Los bindings existentes adicionales se conservan salvo credenciales del bootstrap cuando este se deshabilita. El archivo temporal de variables no contiene valores de secretos y se elimina al terminar. No imprimir la línea completa de comandos ni capturar credenciales.

Bootstrap inicial debe acordarse con identidad real y ejecutarse una sola vez. Al aprovisionar, volver a desplegar con `EnableBootstrap` desactivado y sin sus bindings; el script retira esos bindings del servicio y no borra los secretos de Secret Manager ni la cuenta creada.

[Referencia CLI de deploy](https://docs.cloud.google.com/sdk/gcloud/reference/run/deploy).

## 6. Verificación antes de declarar entregado

1. Construcción/pruebas del commit y OpenAPI generado/validado, y ejecución del contenedor con configuración aislada. Registrar resultados; no sustituirlos por la presencia del Dockerfile.
2. HTTPS público, health 200 sin detalles, petición protegida sin JWT 401 y permisos/propiedad conservados. Servidor OpenAPI apunta a la URL pública configurada.
3. CORS de los dos orígenes/preflight y rechazo de un tercero; prueba inicial con frontend vacío y actualización posterior sin rebuild.
4. Registro/login/mascota, recuperación y activación con el proveedor elegido; comprobar códigos/uso único/revocación y que el formulario realmente consuma el token de activación. Con Gmail, verificar recepción en un buzón real; con Sandbox, registrar únicamente captura de prueba.
5. Agenda/cambios de citas, destinatarios, recordatorio real preparado 24 horas antes, inactividad HTTP y reinicio con pendientes; identificar el proveedor y distinguir aceptación SMTP de recepción en el buzón. Una captura de Sandbox no acredita entrega externa.
6. Comprobar IP del cliente detrás del proxy y que un `X-Forwarded-For` arbitrario no permite eludir cuotas. Hasta identificar una cadena confiable se ignoran forwarded headers; no tomar el primer valor enviado por el cliente.
7. Backup y restauración aislada; preparar rollback únicamente hacia una revisión **0.9 compatible** con el esquema, tipos de tareas y secretos vigentes. Los workers **0.8 no reconocen los nuevos tipos P12 de V17 ni procesan la outbox de recuperación V18**: no volver genéricamente a esa imagen cuando existen trabajos nuevos, ni mantener un worker antiguo consumiendo esas colas. No borrar tablas ni deshacer V17/V18 para degradar la aplicación; conservar Flyway y probar la revisión compatible antes de cambiar tráfico.
8. Integración y accesibilidad pertinentes de ambos portales, revisión de los tres integrantes, guion y datos ficticios. Conservar commit/digest/revisión Cloud Run, resultados y limitaciones, sin publicar secretos o tokens reales.

El cierre de infraestructura requiere proyecto/presupuesto y despliegue externo comprobados; el cierre P1/P2 requiere además portales y aceptación. La clínica permanece fuera del alcance del 18.

## 7. Evidencia histórica de la preparación inicial

La comprobación central de la preparación del 09/10/2026 produjo los siguientes resultados; no son pruebas de un servicio Google Cloud ni verificaciones de Gmail. Para las verificaciones posteriores de Cloud Run con Render, consultar el [registro del 15/10/2026](verificacion-despliegue-cloud-run-render-2026-10-15.md) y la [transición a Gmail del 16/10/2026](verificacion-gmail-2026-10-16.md):

| Comprobación | Resultado y límite |
|---|---|
| Maven | Suite para publicación de **508 pruebas**, sin fallos, errores u omisiones, después de corregir la carrera de mantenimiento/recuperación y la descripción OpenAPI de reprogramaciones. `target/procesos-1-2-publication-verify.log`; detalles en el [informe](verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md) |
| Imagen local | `grownupsvet-backend:0.9.0-local` reconstruida como Linux `amd64`, Java 25, usuario sin privilegios, con la descripción corregida. `target/procesos-1-2-publication-docker-build.log` acredita `BUILD SUCCESS` y creación de la imagen; todavía no acredita publicación en Artifact Registry |
| Script PowerShell | Parser con **0 errores**; validación offline de backend inicial y configuración completa aceptada, siempre `CloudActionsExecuted=false`. Presupuesto ausente, identidad de servicio inválida/incompatible, URL placeholder, CORS wildcard y confianza arbitraria en proxies fueron rechazados |
| HTTP/PDF dentro de la imagen | Repetida satisfactoriamente sobre la imagen para publicación con **2 GiB/1 vCPU**: readiness/liveness `UP`, OpenAPI 0.9.0, Flyway V1–V18, UID 10001, CORS, recorrido registro/login/mascota y descripción P12 corregida. PDF válido, dos inspecciones concurrentes con JVM hijas y rechazo de PDF malformado comprobados. Registro local `target/procesos-1-2-publication-container-smoke.log`; no acredita ejecución en Google Cloud |
| Google Cloud | Cuenta/proyecto, presupuesto y recursos reales todavía no disponibles/verificados. No se crearon recursos ni se ejecutó un despliegue cloud |
| Entrega integrada | Falta comprobar entorno HTTPS, proxy/IP, Sandbox externo, backup/restauración, ambos portales y aceptación compartida |

Los archivos de `target` son registros locales generados; conservar el informe de verificación y la identificación del commit/digest/revisión que finalmente se publique. Actualizar las filas pendientes con su evidencia cuando cada comprobación termine, sin convertir datos de prueba o validación offline en recursos reales.
