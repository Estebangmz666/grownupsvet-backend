# GrownupsVet backend

API REST académica para la gestión de usuarios, mascotas y citas veterinarias. Utiliza **Java 25, Spring Boot 4.1.1, PostgreSQL, Maven Wrapper y springdoc 3.1.0**. El contrato funcional vigente es **0.9.0**; la versión del artefacto Maven continúa en `0.0.1-SNAPSHOT`.

## Alcance funcional

| Proceso | Implementado en el backend |
|---|---|
| **P1 · Registro y gestión de usuarios y mascotas** | Registro de propietarios, login y cierre de sesión, recuperación de contraseña, perfil y foto propios, mascotas, administración de personal e invitaciones, perfiles profesionales, títulos y diplomas. |
| **P2 · Gestión de citas veterinarias** | Disponibilidad, solicitud y consulta de citas, confirmación/rechazo, cancelación, reprogramación, reasignación, inasistencia, corte diario, historial y avisos durables con recordatorio de 24 horas. |
| **P3 · Atención veterinaria y gestión clínica** | **Pendiente de contrato e implementación**. No se incluyen historias clínicas, atención, fórmulas ni controles clínicos. |

La implementación de una operación no significa que esté habilitada en todos los entornos. Recuperación, invitaciones y correo dependen de su configuración; la integración y evaluación de los frontends se verifican por separado.

Las citas usan `America/Bogota`, turnos de 30 minutos y opciones para propietarios desde mañana hasta 60 días. Los cambios administrativos requieren las versiones y condiciones del contrato. El propietario no dispone de cancelación o reprogramación autónomas. Las reglas completas están en [citas y agenda](docs/citas-y-agenda.md) y [disponibilidad veterinaria](docs/disponibilidad-veterinaria.md).

### Roles y autorización

Cada cuenta tiene un solo rol. Los roles tienen responsabilidades independientes y la autorización también comprueba el recurso solicitado.

| Rol | Responsabilidad implementada |
|---|---|
| `OWNER` | Gestionar su perfil, foto y mascotas; consultar veterinarios/opciones de turno y crear/consultar sus citas y avisos. |
| `VETERINARIAN` | Consultar su disponibilidad y citas asignadas; acceder al correo del propietario mediante una operación puntual auditada; gestionar su propia foto. |
| `ADMINISTRATOR` | Gestionar veterinarios, perfiles profesionales, disponibilidad y cambios/estados de citas; gestionar su propia foto. |
| `SUPER_ADMIN` | Crear y administrar cuentas de administradores; gestionar su propia foto. No hereda la gestión de veterinarios ni de citas. |

La autenticación usa JWT Bearer RS256 de 24 horas, sin refresh token. Cada petición protegida comprueba cuenta, estado, rol, permisos y revocación en PostgreSQL. Las contraseñas se almacenan con Argon2id. Véase [usuarios y permisos](docs/arquitectura-usuarios-y-permisos.md).

## Entorno académico desplegado

Corte documentado: **15 de octubre de 2026**.

| Recurso | Dirección |
|---|---|
| API en Cloud Run | [grownupsvet-backend-w5glrcsyda-uc.a.run.app](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app) |
| Swagger UI | [Abrir documentación interactiva](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html) |
| OpenAPI JSON | [Consultar contrato](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs) |

El backend está desplegado en **Google Cloud Run**, región `us-central1`, conectado mediante TLS a **PostgreSQL 18 de Render Free**. Se verificaron salud, contrato y acceso por roles. El entorno tiene **7 cuentas ficticias activas** —1 superadministrador, 2 administradores, 2 veterinarios y 2 propietarios— y **4 mascotas de demostración**. Las credenciales se entregan por un archivo privado independiente.

**El entorno desplegado incluye adaptaciones locales de despliegue que todavía no están versionadas en este repositorio.** Este README registra su estado observado; clonar el código publicado no reproduce por sí solo la configuración Cloud Run/Render. La ejecución de pruebas, el despliegue y la publicación de código tienen evidencias distintas.

Condiciones actuales del entorno:

- Los frontends y sus URLs de activación siguen pendientes de integración. No hay orígenes externos habilitados en CORS; Swagger utiliza el origen de la propia API.
- Las invitaciones de personal están deshabilitadas. Crear personal nuevo o reenviar invitaciones requiere configurar y verificar el formulario real; las cuentas precargadas permiten evaluar los cuatro roles.
- El correo usa **Mailtrap Sandbox**. Una tarea `SENT` acredita aceptación SMTP, no entrega ni lectura en un buzón real. Se verificó la aceptación SMTP de una solicitud de recuperación en nube.
- La base gratuita de Render vence el **14 de noviembre de 2026** y no dispone de backups configurados. Conservar datos exige exportarlos y comprobar su restauración antes del vencimiento; recrear una base no conserva su contenido.
- Las comprobaciones de demostración en nube no sustituyen la regresión completa, las pruebas de carga ni la evaluación de los portales en ese entorno.

## Contrato y documentación de la API

Con el backend local en su puerto predeterminado:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`.
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`.
- Salud: `/actuator/health/liveness` y `/actuator/health/readiness`.

El snapshot versionado conserva OpenAPI **3.1.0**, contrato **0.9.0**. Controladores, DTOs y anotaciones Java son la fuente editable: **no editar manualmente** [openapi.json](docs/openapi/openapi.json). El [procedimiento de regeneración y validación](docs/openapi/README.md) explica cómo comprobar contrato y ejemplos reales.

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
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Necesarias para demostrar envío de avisos con Mailtrap Sandbox. `dev` tiene defaults de host Sandbox y puerto 2525. |
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

- [Verificación del incremento 0.9.0](docs/verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md): suite y contrato de publicación, contenedor y límites del corte histórico.
- [Casos académicos de P1/P2](docs/evidencia-casos-academicos-procesos-1-2-2026-10-10.md): relación CP-001–CP-012 con pruebas; clínica prevista por separado.
- [Verificación en Mailtrap](docs/verificacion-mailtrap.md) y [recordatorio con reloj real](docs/verificacion-recordatorio-real-2026-10-10.md): procedimientos y evidencias locales fechadas.
- [Respaldo y restauración local](docs/verificacion-respaldo-restauracion-local-2026-10-10.md): alcance PostgreSQL local; no acredita respaldo/restauración del entorno Render.

## Organización del código

El proyecto se organiza por dominios: `authentication`, `user`, `pet`, `staff`, `availability`, `appointment` y `shared`, dentro de `edu.uniquindio.grownupsvet.grownupsvet_backend`. Los controladores reciben/devuelven DTOs, los servicios aplican reglas y transacciones, y los repositorios persisten los datos. La seguridad y los errores comunes se centralizan en `shared`.
