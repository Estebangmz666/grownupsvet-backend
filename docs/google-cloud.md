# Despliegue del backend en Google Cloud

Corte de hoy: **26/10/2026, America/Bogota**. La evaluación es parcial:
P1/P2 web y P3 API publicado/verificado; no equivale a la entrega final
completa. La auditoría integral debe esperar la finalización de la versión
final. Los cortes fechados del 15/10 y 16/10 siguientes son antecedentes.

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

`scripts/deploy-google-cloud.ps1` usa **ImageOnly por omisión**. Sin
`-Execute` valida identificadores/imagen y prepara un plan sin red,
autenticación ni cambios en nube. `-ValidateOnly` explicita ese modo.
`OutputPlanPath` debe ser una ruta JSON absoluta fuera del repositorio y
nueva; no se sobrescriben expedientes. No pasar a ImageOnly la hashtable
histórica de configuración: se rechazan parámetros de entorno o secretos.

```powershell
$imageUpdate = @{
    ProjectId = $projectId
    Region = $region
    ServiceName = $serviceName
    ImageUri = $verifiedImageDigest # Artifact Registry con @sha256:...
    ExpectedContractVersion = $expectedContractVersion
    CurrentServiceConfigurationPath = $privateReviewedServiceJson
    OutputPlanPath = $privateNewPlanJson
}
.\scripts\deploy-google-cloud.ps1 @imageUpdate -ValidateOnly
# Tras CI, revisión de imagen/configuración y autorización de la publicación:
# elegir otra ruta de plan nueva si la validación anterior ya escribió una.
.\scripts\deploy-google-cloud.ps1 @imageUpdate -Execute
# O bien, si se autoriza activar la revisión exacta cuando el tráfico está fijado:
# .\scripts\deploy-google-cloud.ps1 @imageUpdate -Execute -ActivateRevision
```

Execute exige digest y versión de contrato esperada. Consulta el servicio
existente, valida un contenedor, perfiles productivos, máximo 1, CPU fuera
de solicitudes, probes e identidad explícita y referencias numéricas de
Secret Manager. Si se aporta una descripción revisada, contrasta su huella
con la configuración viva antes de actualizar. Lee el contrato público
antes y después. Por omisión, la única mutación es
`gcloud run services update --image`. No añade flags de entorno, secretos,
recursos o IAM. `-ActivateRevision` es una opción adicional exclusiva de
ImageOnly; sin Execute registra la intención y permanece offline.

Requiere PowerShell 7.2 o posterior. El límite se interpreta por nivel:
`run.googleapis.com/maxScale=1` del servicio puede coexistir con
`autoscaling.knative.dev/maxScale=20` histórico de la revisión; Cloud Run
aplica el menor. El helper acepta y conserva ambos valores, sin cambiar el
20 por 1. Rechaza un cap explícito de servicio distinto de 1 y, si falta,
exige cap de revisión 1. El manifiesto saneado muestra los dos valores por
separado. [Máximos de servicio y revisión](https://docs.cloud.google.com/run/docs/configuring/max-instances).

El expediente `.before.json`, `.image.json`, `.after.json` y `.result.json` omite valores
de entorno y secretos; conserva nombres/referencias y una huella de la
configuración. La comparación incluye perfiles, variables, secretos,
montajes, probes, recursos, CPU, límites, identidad y configuración de
conexión/JWT/TLS. Solo ignora imagen y anotaciones identificadas del cliente
de despliegue. Se exige imagen esperada, revisión lista, 100 % del tráfico
en esa revisión y contrato esperado. Si el tráfico está fijado a una
revisión anterior, el modo predeterminado falla y conserva evidencia sin
ejecutar una activación. Si el servicio ya dirigía tráfico a latest, Cloud
Run conserva esa política al actualizar la imagen; el helper verifica su
resolución y no emite otro comando de tráfico.

Con `-ActivateRevision`, solo después de verificar revisión lista no vacía,
digest exacto y huella de configuración, el helper puede ejecutar una vez
`gcloud run services update-traffic --to-revisions=<revision-exacta>=100`.
Captura el destino del resultado de su propia actualización; nunca usa
`--to-latest`, `LATEST` ni elimina tags. Admite una sola asignación100%
sin tags y rechaza splits/tags antes de cambiar la imagen. Si la nueva
revisión ya recibe ese tráfico, no realiza activación adicional.

Las relecturas comparan generación, imagen, huella, revisión creada/lista
y tráfico de spec/status antes de actualizar, antes/después de activar y
después de comprobar el contrato. Una divergencia observada aborta sin
reintento ni restauración automática. Los checkpoints opcionales
`.pre-activation.json` y `.activation.json` conservan la secuencia; el
resultado distingue `ActivationAttempted` de `Verified`. No se acredita
actualización completa con tráfico antiguo. Si un comando falla antes de
una nueva lectura, `.after.json` conserva el último estado observado;
`Stage` y `Verified=false` impiden tratarlo como confirmación posterior al fallo.
El [plan previo de esta
opción](plan-activacion-revision-cloud-run-2026-10-28.md) registra garantías,
pruebas y límites. [CLI de tráfico](https://docs.cloud.google.com/sdk/gcloud/reference/run/services/update-traffic).

La descripción JSON original puede contener valores privados: conservarla
fuera de Git y no pegarla en logs. El hash global no certifica la seguridad
de cada valor ni elimina la ventana entre una lectura y una mutación.
Conserva configuración y señala divergencias observadas; no constituye
una transacción entre llamadas CLI ni prueba que hayan terminado las
peticiones anteriores durante la propagación. La aceptación funcional y el scrape siguen teniendo
sus pruebas propias. [CLI update](https://docs.cloud.google.com/sdk/gcloud/reference/run/services/update).

### Plantilla histórica: preparación offline exclusivamente

`-DeploymentMode ConfigurationTemplate` conserva la validación de la
plantilla inicial. Requiere identificadores, remitente, presupuesto y
**referencias** `nombre:versión-numérica`, nunca secretos en claro.
Su `-Execute` está bloqueado: no puede reemplazar un servicio existente.
Cambios de configuración real se preparan y verifican como una operación
separada, preservando los valores efectivos y el gasto acordado.

Preparar una hashtable de parámetros con `ProjectId`, `Region`, `ServiceName`, `ImageUri`, `CloudSqlInstanceConnectionName`, `ServiceAccountEmail`, `DatabaseName`, `DatabaseUsername`, `MailFrom`, `ApprovedMonthlyBudgetUsd` y `SecretBindings`. `MailProvider` admite `Mailtrap` (predeterminado, Sandbox 2525) o `Gmail` (SMTP 587); no modifica el proveedor de base de datos. Los nombres de bindings necesarios son:

- `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `JWT_KEYSTORE_PASSWORD` y `/var/secrets/jwt/jwt-signing.p12`.
- Con recuperación habilitada: `PASSWORD_RECOVERY_HMAC_SECRET`, `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY`.
- Con `-EnableInvitations`: `STAFF_INVITATION_ENCRYPTION_KEY` y `StaffActivationUrl` real.
- Con `-EnableBootstrap`: `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`; sin esa opción, no incluir esos bindings.
- `JWT_KEY_PASSWORD` es opcional si la clave usa otra contraseña; `JwtKeyAlias` debe coincidir con el PKCS12.
- Con `-EnableMonitoring`: perfil `monitoring` y referencia `MONITORING_TOKEN_SHA256` al hash del token máquina, nunca al Bearer original.
- Con `-EnableLoginRateLimit`: `LOGIN_RATE_LIMIT_HMAC_SECRET` en Secret Manager, modo PEER con `TRUSTED_PROXY_PATTERN=^$` y límites 10/15 minutos por correo+peer y 60/minuto por peer. La plantilla no activa estos controles en nube.

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
.\scripts\deploy-google-cloud.ps1 @deploymentParameters -DeploymentMode ConfigurationTemplate -ValidateOnly
```

La plantilla offline no crea recursos, cambia variables ni habilita acceso
público. El presupuesto declarado no configura un límite automático de gasto.
La única ruta ejecutable del helper es ImageOnly sobre un servicio existente;
no crea proyecto, base, secretos, imagen ni permisos.

El generador anterior usaba `--env-vars-file`, que podía reemplazar variables
no representadas por sus parámetros. Esa aplicación de la plantilla se
retiró: no reutilizar manifiestos históricos para modificar producción. Las
referencias a secretos se preparan por nombre y versión numérica; no se leen
sus valores ni se imprimen credenciales.

Bootstrap inicial requiere una operación específica acordada y de una sola
vez. ImageOnly preserva sus variables existentes: no retira bindings ni
aprovisiona cuentas. Su desactivación y retiro de referencias se verifican
por separado.

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
