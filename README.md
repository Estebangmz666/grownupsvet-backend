# GrownupsVet backend

API REST para la gestión de usuarios, mascotas, citas y atención clínica veterinaria. Utiliza **Java 25, Spring Boot 4.1.1, PostgreSQL, Maven Wrapper y springdoc 3.1.0**. El contrato de estas fuentes es **0.10.0**; la versión del artefacto Maven continúa en `0.0.1-SNAPSHOT`. El [informe de P3](docs/verificacion-backend-p3-2026-10-25.md) distingue pruebas, publicación Git y aceptación del despliegue.

## Alcance funcional

| Proceso | Implementado en el backend |
|---|---|
| **P1 · Registro y gestión de usuarios y mascotas** | Registro de propietarios, login y cierre de sesión, recuperación de contraseña, perfil y foto propios, mascotas, administración de personal e invitaciones, perfiles profesionales, títulos y diplomas. |
| **P2 · Gestión de citas veterinarias** | Disponibilidad, solicitud y consulta de citas, confirmación/rechazo, cancelación, reprogramación, reasignación, inasistencia, corte diario, historial y avisos durables con recordatorio de 24 horas. |
| **P3 · Atención veterinaria y gestión clínica** | Borradores por cita confirmada, fórmula y controles estructurados, finalización atómica con cita COMPLETED e historia publicada autorizada. Recordatorios clínicos durables por correo, con inicio/fin y omisión de ocurrencias vencidas; implementación local verificada por pruebas, aún sin evidencia del nuevo despliegue. Adendas fuera del alcance aprobado. |

La implementación de una operación no significa que esté habilitada en todos los entornos. Recuperación, invitaciones y correo dependen de su configuración; la integración y evaluación de los frontends se verifican por separado.

Las citas usan `America/Bogota`, turnos de 30 minutos y opciones para propietarios desde mañana hasta 60 días. Los cambios administrativos requieren las versiones y condiciones del contrato. El propietario no dispone de cancelación o reprogramación autónomas. Las reglas completas están en [citas y agenda](docs/citas-y-agenda.md) y [disponibilidad veterinaria](docs/disponibilidad-veterinaria.md).

### Roles y autorización

Cada cuenta tiene un solo rol. Los roles tienen responsabilidades independientes y la autorización también comprueba el recurso solicitado.

| Rol | Responsabilidad implementada |
|---|---|
| `OWNER` | Gestionar su perfil, foto y mascotas; consultar veterinarios/opciones de turno, crear/consultar sus citas y avisos y leer historia publicada de sus mascotas, incluidas las archivadas. |
| `VETERINARIAN` | Consultar disponibilidad y citas asignadas, editar/finalizar su atención y leer antecedentes durante una asignación confirmada. Conserva lectura de lo que finalizó, sin acceso permanente a futuras atenciones ajenas. Acceso puntual auditado al correo y gestión de foto propios. |
| `ADMINISTRATOR` | Gestionar veterinarios, perfiles profesionales, disponibilidad y cambios/estados de citas; gestionar su propia foto. |
| `SUPER_ADMIN` | Crear y administrar cuentas de administradores; gestionar su propia foto. No hereda la gestión de veterinarios ni de citas. |

La autenticación usa JWT Bearer RS256 de 24 horas, sin refresh token. Cada petición protegida comprueba cuenta, estado, rol, permisos y revocación en PostgreSQL. Las contraseñas se almacenan con Argon2id. Véase [usuarios y permisos](docs/arquitectura-usuarios-y-permisos.md).

Los permisos clínicos nuevos requieren un nuevo login de OWNER/VETERINARIAN al publicar P3. Administración conserva acceso a citas, sin contenido clínico. El [contrato clínico](docs/atenciones-clinicas.md) y el [plan de cierre por incrementos](docs/plan-cierre-ejecucion-2026-10-25.md) documentan las decisiones aprobadas, los límites y la evidencia. [CI](docs/integracion-continua.md) valida pruebas y contrato; [observabilidad](docs/observabilidad.md) prepara Prometheus/Grafana y distingue contadores operativos de indicadores académicos evaluados.

## Entorno académico desplegado: corte histórico

Corte documentado: **16 de octubre de 2026**.

| Recurso | Dirección |
|---|---|
| API en Cloud Run | [grownupsvet-backend-w5glrcsyda-uc.a.run.app](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app) |
| Swagger UI | [Abrir documentación interactiva](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html) |
| OpenAPI JSON | [Consultar contrato](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs) |

El backend está desplegado en **Google Cloud Run**, región `us-central1`, conectado mediante TLS a **PostgreSQL 18 de Render Free**. El corte del 15/10 verificó salud, contrato, acceso por roles, **7 cuentas ficticias activas** —1 superadministrador, 2 administradores, 2 veterinarios y 2 propietarios— y **4 mascotas de demostración**. El 16/10 se añadió un propietario de prueba autorizado para comprobar recuperación mediante Gmail. Las credenciales se conservan en archivos privados independientes.

El corte histórico desplegado usó adaptaciones locales de configuración. Esta rama las consolida junto con P3 y sus controles de entrega; las variables privadas, secretos y configuración real del servicio siguen siendo necesarios. La ejecución de pruebas, la publicación Git y el despliegue tienen evidencias distintas.

Condiciones observadas en ese corte (deben verificarse de nuevo para entregar P3):

- Los frontends y sus URLs de activación siguen pendientes de integración. No hay orígenes externos habilitados en CORS; Swagger utiliza el origen de la propia API.
- Las invitaciones de personal están deshabilitadas. Crear personal nuevo o reenviar invitaciones requiere configurar y verificar el formulario real; las cuentas precargadas permiten evaluar los cuatro roles.
- El correo usa **Gmail SMTP** (`smtp.gmail.com:587`, STARTTLS). La revisión `grownupsvet-backend-00004-mzr` está lista y recibe el 100 % del tráfico. Una recuperación solicitada a la API en nube quedó `SENT` al primer intento y el usuario confirmó su recepción en **Recibidos**. Son evidencias distintas de aceptación SMTP y llegada al buzón. La evidencia del 15/10 corresponde históricamente a Mailtrap Sandbox.
- La base gratuita de Render vence el **14 de noviembre de 2026** y no dispone de backups configurados. Conservar datos exige exportarlos y comprobar su restauración antes del vencimiento; recrear una base no conserva su contenido.
- Las comprobaciones de demostración en nube no sustituyen la regresión completa, las pruebas de carga ni la evaluación de los portales en ese entorno.

La [verificación de Gmail del 16/10/2026](docs/verificacion-gmail-2026-10-16.md) registra autenticación y backend local disponibles, recepción de una prueba SMTP directa y recepción del correo de recuperación generado en nube. Conserva imagen, revisión, resultados y límites; no se leyó el código de recuperación ni se restableció la contraseña. Consultar [Gmail local](docs/ejecucion-local.md#gmail-en-la-terminal-actual) y [Gmail en Cloud Run](docs/google-cloud.md#gmail-para-destinatarios-reales). Sandbox sigue disponible para pruebas aisladas.

## Contrato y documentación de la API

Con el backend local en su puerto predeterminado:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`.
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`.
- Salud: `/actuator/health/liveness` y `/actuator/health/readiness`.

El snapshot versionado conserva OpenAPI **3.1.0**, contrato **0.10.0**. Controladores, DTOs y anotaciones Java son la fuente editable: **no editar manualmente** [openapi.json](docs/openapi/openapi.json). El [procedimiento de regeneración y validación](docs/openapi/README.md) explica cómo comprobar contrato y ejemplos reales.

Las rutas protegidas reciben `Authorization: Bearer <accessToken>`. Los errores utilizan `application/problem+json`, con `errorCode` y `fieldErrors` para los clientes. Registro, autenticación y operaciones públicas tienen sus condiciones específicas en OpenAPI; la existencia de una ruta no concede acceso a datos ajenos.

| Guía | Contenido |
|---|---|
| [Bases del contrato](docs/contrato-api-bases-propuestas.md) | Decisiones de registro, perfil, sesión y validaciones. |
| [Mascotas y recuperación](docs/mascotas-y-recuperacion.md) | Propiedad de recursos, actualización, recuperación y correo durable. |
| [Personal e invitaciones](docs/personal-e-invitaciones.md) | Roles, estados, activación, documentos y cuotas. |
| [Disponibilidad veterinaria](docs/disponibilidad-veterinaria.md) | Turnos, lotes, permisos, ocupación e historial. |
| [Citas y agenda](docs/citas-y-agenda.md) | Estados, plazos, versiones, cambios, avisos y recordatorios. |
| [Guion de demostración](docs/guion-demostracion-procesos-1-2.md) | Recorrido propuesto de P1/P2 desde Swagger. |
| [Datos de demostración](docs/datos-demostracion.md) | Helper reproducible para la API local con personal y credenciales aportados por el operador. |

Estas guías conservan decisiones y verificaciones fechadas de cada incremento. Sus pendientes históricos deben interpretarse junto con el corte de entorno indicado arriba.

### Avances posteriores al corte histórico

El 25/10 el frontend compatible con `COMPLETED` se publicó en [Cloudflare Pages](https://grownupsvet-frontend.pages.dev/). El gateway existente usa la API desde el mismo origen; no requiere abrir CORS directo de Cloud Run. El CORE P3 pasó CI remoto y la [protección de main](docs/verificacion-proteccion-integracion-2026-10-25.md) bloqueó una prueba controlada con contrato divergente.

Se obtuvo un [backup de Render y se comprobó su restauración](docs/verificacion-respaldo-render-2026-10-25.md), incluidas las migraciones clínicas. Quedó programada otra copia para el **13/11/2026 a las 09:00 de Bogotá**: requiere el computador y Codex encendidos. El respaldo y las credenciales permanecen fuera de Git.

La [observabilidad elegida](docs/observabilidad.md) usa Grafana OSS y Prometheus locales, sin suscripción ni vencimiento. El perfil `monitoring` protege el scrape remoto con una credencial máquina independiente de los JWT humanos. La publicación de la imagen final y M1–M9 siguen sujetos a su verificación desplegada.

## Ejecución local en Windows

Ejecutar los comandos desde la carpeta `grownupsvet-backend`.

### Requisitos

1. **JDK 25**, con `JAVA_HOME` apuntando a su raíz y `java`/`javac` disponibles. Configurar el mismo JDK en el IDE y su ejecutor Maven.
2. **PostgreSQL 18** funcionando; el destino local predeterminado es `localhost:5432`.
3. Bases separadas `grownupsvet_dev` y **`grownupsvet_test`, exclusiva para pruebas**, con un usuario autorizado para crear los objetos de Flyway. Las pruebas también crean y eliminan esquemas propios.
4. Variables de entorno de base de datos y firma JWT disponibles en el proceso que arrancará la aplicación. Usar Maven Wrapper; no hace falta instalar Maven por separado.

Si las bases no existen, crearlas con una cuenta autorizada; ejecutar cada sentencia por separado y fuera de una transacción:

```sql
CREATE DATABASE grownupsvet_dev;
CREATE DATABASE grownupsvet_test;
```

### Configuración

| Variable | Uso local |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales del PostgreSQL local. |
| `JWT_KEYSTORE_PATH`, `JWT_KEYSTORE_PASSWORD` | Ruta y contraseña de un almacén PKCS12 externo al repositorio, con una clave privada RSA válida de al menos 2048 bits. |
| `JWT_KEY_ALIAS` | Alias de la clave; el valor predeterminado es `grownupsvet-jwt-signing-2026-09`. |
| `JWT_KEY_PASSWORD` | Solo si la clave tiene una contraseña distinta de la del almacén. |
| `APPOINTMENT_WORKDAY_START_TIME` | **Obligatoria en `dev`**. Para esta entrega: `06:00`, en `America/Bogota`. |
| `MAIL_HOST`, `MAIL_PORT` | `dev` conserva Mailtrap Sandbox/2525 por defecto; para Gmail, `smtp.gmail.com`/587 con STARTTLS obligatorio. |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Credenciales y remitente del proveedor elegido. Con Gmail: dirección completa, contraseña de aplicación y la misma dirección como remitente. |
| `PASSWORD_RECOVERY_ENABLED` | Por defecto `false`; habilitarla requiere `PASSWORD_RECOVERY_HMAC_SECRET`, `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY` y el canal de correo configurado. |
| `STAFF_INVITATIONS_ENABLED` | Por defecto `false`; habilitarla requiere `STAFF_INVITATION_ENCRYPTION_KEY`, correo y `STAFF_ACTIVATION_URL` del formulario real. |

`FRONTEND_BASE_URL` configura enlaces del portal; `CORS_ALLOWED_ORIGINS` declara orígenes exactos, sin comodines. Sin origen configurado no se habilita acceso desde sitios externos. Las invitaciones conservan una URL de activación independiente.

No guardar credenciales, JWT, claves privadas ni archivos PKCS12 en Git. Configurar los valores privados por el mecanismo local habitual; si se cambian variables de Windows, abrir nuevamente la terminal o el IDE para que las reciba.

Comprobar las herramientas y la preparación del proceso:

```powershell
java -version
javac -version
.\mvnw.cmd -version
$env:APPOINTMENT_WORKDAY_START_TIME = "06:00"
.\scripts\check-local-configuration.ps1
```

El diagnóstico no arranca servicios ni valida credenciales PostgreSQL, firma JWT o entrega SMTP. La [guía de ejecución local](docs/ejecucion-local.md) explica sus resultados y límites.

### Gmail en la terminal actual

Desde PowerShell en Windows, guardar la cuenta y su contraseña de aplicación con entrada oculta:

```powershell
.\scripts\configure-gmail.ps1
# Alternativa con formulario local: .\scripts\configure-gmail.ps1 -ShowWindow
```

El helper guarda una credencial `PSCredential` en `%USERPROFILE%\.grownupsvet\gmail\smtp-credential.clixml`, con la contraseña protegida mediante DPAPI para el usuario de Windows. No la guarda en Git ni en el registro de variables de entorno. Solo configura el correo del proceso actual; conserva DB, JWT y claves HMAC/cifrado. No envía mensajes ni despliega la nube.

En cada terminal nueva, cargar la credencial privada antes del arranque normal:

```powershell
. .\scripts\configure-gmail.ps1 -UseSavedCredential
```

Arrancar el backend desde esa misma terminal. Cargar esta configuración no actualiza un backend ya iniciado ni un IDE abierto; la recepción se comprueba por separado con un flujo real autorizado. El procedimiento completo está en [ejecución local](docs/ejecucion-local.md#gmail-en-la-terminal-actual).

### Arranque

```powershell
.\mvnw.cmd spring-boot:run
```

El perfil predeterminado `dev` conecta a `grownupsvet_dev`. Flyway aplica las migraciones pendientes V1–V18 y Hibernate valida el esquema; las migraciones ya aplicadas se conservan sin editar. Comprobar el arranque y readiness antes de utilizar Swagger.

Una sobrescritura `SPRING_DATASOURCE_URL` puede cambiar el destino del perfil; revisarla antes de arrancar o probar. La variable antigua `DB_URL` no se utiliza.

## Pruebas y evidencias

Con PostgreSQL disponible, `grownupsvet_test` exclusiva para pruebas y las credenciales locales configuradas:

```powershell
.\mvnw.cmd verify
```

`verify` ejecuta la suite y genera `target/grownupsvet-backend-0.0.1-SNAPSHOT.jar`. Las pruebas de integración usan el perfil `test`, PostgreSQL real y claves RSA efímeras; no necesitan leer el PKCS12 privado local. No dirigirlas a la base desplegada ni a una base con datos que deban conservarse.

Para iniciar el JAR con la configuración local ya preparada y el puerto libre:

```powershell
java -jar .\target\grownupsvet-backend-0.0.1-SNAPSHOT.jar
```

### Registro académico de calidad

[TestRail R1](https://grownupsvet.testrail.io/index.php?/runs/view/1) conserva **84 casos: 54 Passed y 30 Blocked**, con corte del **14/10/2026** y exportación verificada el **15/10/2026**. Consultar cada caso, sus precondiciones y adjuntos; el acceso requiere una cuenta autorizada.

Los 30 bloqueados corresponden a 23 controles de interfaz/interacción, 5 de clínica P3, 1 de preferencias visuales y 1 de integración continua. No equivalen a casos aprobados ni se resuelven únicamente desplegando la API. CI sigue pendiente en SCRUM-87 y preferencias en SCRUM-88.

La regresión histórica local del 14/10 registró **541 pruebas JUnit satisfactorias en 51 informes**, con ampliaciones locales de pruebas aún no publicadas. Ese número no se atribuye a un clon del código versionado ni a una nueva ejecución en Cloud Run. Los casos de TestRail, las pruebas JUnit y las aserciones de Postman son unidades distintas y no se suman.

Evidencias versionadas para ampliar la revisión:

- [Gmail local y Cloud Run](docs/verificacion-gmail-2026-10-16.md): 20 pruebas del validador, 1 de generación OpenAPI, recepción real y metadatos del envío; los 41 ejemplos existentes se validaron sin repetir sus recorridos HTTP.
- [Verificación del incremento 0.9.0](docs/verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md): suite y contrato de publicación, contenedor y límites del corte histórico.
- [Casos académicos de P1/P2](docs/evidencia-casos-academicos-procesos-1-2-2026-10-10.md): relación CP-001–CP-012 con pruebas; clínica prevista por separado.
- [Verificación en Mailtrap](docs/verificacion-mailtrap.md) y [recordatorio con reloj real](docs/verificacion-recordatorio-real-2026-10-10.md): procedimientos y evidencias locales fechadas.
- [Respaldo y restauración local](docs/verificacion-respaldo-restauracion-local-2026-10-10.md): alcance PostgreSQL local; no acredita respaldo/restauración del entorno Render.

## Organización del código

El proyecto se organiza por dominios: `authentication`, `user`, `pet`, `staff`, `availability`, `appointment` y `shared`, dentro de `edu.uniquindio.grownupsvet.grownupsvet_backend`. Los controladores reciben/devuelven DTOs, los servicios aplican reglas y transacciones, y los repositorios persisten los datos. La seguridad y los errores comunes se centralizan en `shared`.


## Verificación de P3

El [informe del 25/10/2026](docs/verificacion-backend-p3-2026-10-25.md) registra **695 pruebas aprobadas**, contrato y ejemplos HTTP validados, garantías de concurrencia y los pendientes operativos de producción. Los resultados históricos de otras secciones mantienen sus fechas de corte.
