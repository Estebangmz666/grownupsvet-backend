# GrownupsVet backend

Proyecto Maven con Java 25, Spring Boot 4.1.1, PostgreSQL y springdoc 3.1.0.

## Modelo inicial

`user.model.User` representa la cuenta de acceso, compartida por los roles. El primer incremento funcional implementa el registro de propietarios y guarda sus datos personales en `user.model.OwnerProfile`: nombre completo, fecha de nacimiento y teléfono internacional. Las reglas aceptadas están recogidas en las [bases del contrato](docs/contrato-api-bases-propuestas.md).

El alcance inicial permite al propietario consultar su perfil, editar únicamente el teléfono y desactivar su propia cuenta. La foto es opcional y todos los roles pueden gestionar solamente la suya; se almacena procesada en PostgreSQL en una tabla separada. Correo, nombre completo y fecha de nacimiento no tienen edición por el propietario. El perfil profesional del veterinario será administrado mediante operaciones posteriores exclusivas del administrador. Las correcciones administrativas, el cambio de correo y un posible flujo de soporte o PQR quedan aplazados. La contraseña pertenece a los flujos de autenticación y recuperación, separados de la edición del perfil. Registro, inicio y cierre de sesión, perfil, foto y desactivación están implementados.

| Campo | Persistencia | Regla |
|---|---|---|
| `id` | UUID | Generado por JPA al persistir. |
| `email` | Texto, máximo 254 caracteres | Único; se almacena en minúsculas y sin espacios alrededor. |
| `passwordHash` | Texto, máximo 255 caracteres | Recibe una contraseña ya codificada por un servicio; nunca una contraseña sin codificar. |
| `role` | Texto | Un rol por cuenta: `OWNER`, `VETERINARIAN` o `ADMINISTRATOR`. |
| `active` | Booleano | La cuenta se crea activa y permite activación/desactivación. |

La migración `V1__create_users.sql` crea `users`; V2 crea `owner_profiles`; V3 añade `revoked_access_tokens`; y V4 crea `user_profile_photos`. Las migraciones aplicadas se conservan sin edición. Los datos exclusivos de propietarios no se imponen a cuentas profesionales ni se inventan para cuentas históricas. Se conservan los nombres originales del paquete generado: `edu.uniquindio.grownupsvet.grownupsvet_backend`.

Esteban confirmó un solo rol por cuenta para la primera versión. La [arquitectura de usuarios y permisos](docs/arquitectura-usuarios-y-permisos.md) describe las responsabilidades de propietario, veterinario y administrador. El login emite las autoridades vigentes del rol; cada petición protegida vuelve a consultar cuenta, rol, permisos y revocación en PostgreSQL.

La entidad no implementa autenticación ni sustituye un DTO de respuesta. El hash se excluye de JSON y del texto de diagnóstico. El servicio de login aplica la misma normalización del correo, exige una cuenta activa y verifica el hash Argon2id sin transformar la contraseña recibida. La comprobación de credenciales y la seguridad HTTP corresponden a servicios y a Spring Security.

## Registro de propietarios implementado

`POST /api/v1/auth/registrations` recibe `application/json` y no requiere una sesión. `UserSignupController` valida `UserSignupRequestDTO`; `UserSignupService` crea una cuenta activa con rol `OWNER` y su perfil en una sola transacción. El cliente no puede elegir `role`, `active` ni enviar otras propiedades ajenas al DTO.

Ejemplo con datos ficticios:

```json
{
  "email": "persona@example.com",
  "fullName": "María del Carmen Gómez",
  "dateOfBirth": "1955-05-20",
  "phoneNumber": "+573001234567",
  "password": "mis mascotas caminan por el jardín"
}
```

La respuesta es `201 Created`, con `UserSignupResponseDTO`:

```json
{
  "id": "7d667530-867b-4c78-a458-0904fb82d574",
  "email": "persona@example.com"
}
```

El identificador público es una cadena UUID y el correo se devuelve normalizado en minúsculas. El alta no emite token ni realiza una redirección HTTP. El frontend muestra el éxito y conduce a `/login`; después del primer acceso puede conducir al paso de primera mascota, que sigue pendiente. Las mascotas y la foto no forman parte de esta petición.

| Campo obligatorio | Validación implementada |
|---|---|
| `email` | Texto no vacío, formato de correo y máximo 254 caracteres; unicidad sobre el correo normalizado. |
| `fullName` | Máximo 150 puntos de código Unicode en el texto recibido, incluidos sus espacios; al menos dos componentes con una letra cada uno. Admite letras y marcas internacionales, espacios, apóstrofos, guiones y puntos de iniciales; rechaza dígitos y controles. Retira espacios exteriores al guardar. Comprueba composición mínima, no identidad ni apellido legal. |
| `dateOfBirth` | Fecha sin hora, no futura, con edad de 0 a 130 años cumplidos según un `Clock` en `America/Bogota`. |
| `phoneNumber` | Texto E.164 canónico, `+` y hasta 15 dígitos, sin espacios ni extensiones. Google libphonenumber valida metadatos de números internacionales fijos y móviles; no prueba titularidad ni WhatsApp. |
| `password` | Entre 15 y 128 puntos de código Unicode, preservando espacios y sin mezcla obligatoria de caracteres. Rechaza contenido formado solo por espacios y coincidencias completas con una lista local de contraseñas comunes. |

La contraseña se codifica con Argon2id mediante `PasswordEncoder`, sin recortarla ni transformarla. El prefijo `{argon2id}` identifica el algoritmo en el hash almacenado. La lista local de 10.000 contraseñas de SecLists tiene procedencia, licencia y checksum en [password-blocklist/README.md](src/main/resources/password-blocklist/README.md); es una base limitada de contraseñas comunes, no una colección exhaustiva de filtraciones. Su comparación ignora mayúsculas únicamente para consultar la lista y no modifica el secreto codificado.

La detección de correo duplicado combina una comprobación previa y la restricción única `uk_users_email` de PostgreSQL. Si dos peticiones intentan registrar el mismo correo simultáneamente, el servicio traduce específicamente esa restricción a `EmailAlreadyRegisteredException`. Otras fallas de integridad conservan su naturaleza de error interno y la transacción evita cuentas o perfiles parciales.

Springdoc genera el contrato desde controladores, DTOs y anotaciones en `/v3/api-docs` y `/v3/api-docs.yaml`; Swagger UI está disponible en `/swagger-ui/index.html`. Registro, login y documentación son públicos; las demás rutas exigen un JWT Bearer válido. Este contrato incremental aporta a SCRUM-67 y las operaciones funcionales a SCRUM-23.

## Login y JWT implementados

`POST /api/v1/auth/sessions` recibe `application/json` con `email` y `password`, ambos obligatorios. El correo debe tener formato válido y la contraseña admite como máximo 128 puntos de código Unicode. La contraseña se conserva exactamente como fue recibida, se marca como escritura únicamente y no aparece en la serialización JSON ni en `toString()`.

Ejemplo de petición:

```json
{
  "email": "persona@example.com",
  "password": "mis mascotas caminan por el jardín"
}
```

Una autenticación correcta devuelve `200 OK`, `Cache-Control: no-store` y:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "7d667530-867b-4c78-a458-0904fb82d574",
    "email": "persona@example.com",
    "role": "OWNER",
    "permissions": [
      "PROFILE_READ_SELF",
      "PROFILE_UPDATE_SELF",
      "PROFILE_DEACTIVATE_SELF",
      "PROFILE_PHOTO_READ_SELF",
      "PROFILE_PHOTO_UPDATE_SELF"
    ]
  }
}
```

El token se firma con `RS256`, dura 24 horas y contiene `sub`, `email`, `role`, `permissions`, `iat`, `exp`, `jti`, `iss` y `aud`. El backend valida firma, vencimiento con 30 segundos de tolerancia, emisor `grownupsvet-backend` y audiencia `grownupsvet-clients`. Las rutas protegidas reciben `Authorization: Bearer <accessToken>`. No hay refresh token. Un correo inexistente, contraseña incorrecta o cuenta inactiva produce el mismo `401 INVALID_CREDENTIALS` para no revelar la causa.

La política de creación de contraseñas se aplica al registro, no se vuelve a exigir en cada login. Cada petición protegida comprueba que la cuenta continúa activa y que su rol y permisos siguen vigentes. `DELETE /api/v1/auth/sessions/current` revoca en PostgreSQL el token presentado; un token revocado deja de aceptarse inmediatamente.

## Perfil propio, foto y cierre de sesión implementados

| Operación | Roles | Resultado acordado |
|---|---|---|
| `GET /api/v1/users/me` | `OWNER` | `200 OK` con los datos seguros de la cuenta y el perfil del propietario; nunca devuelve `passwordHash`. |
| `PATCH /api/v1/users/me` | `OWNER` | Modifica únicamente `phoneNumber` y devuelve `200 OK` con el perfil actualizado. |
| `DELETE /api/v1/users/me` | `OWNER` | Desactivación lógica (`active = false`) y `204 No Content`. No elimina físicamente la cuenta. |
| `GET /api/v1/users/me/profile/photo` | Todos los roles | Devuelve los bytes JPEG/PNG de la foto propia o `404 PROFILE_PHOTO_NOT_FOUND`. |
| `PUT /api/v1/users/me/profile/photo` | Todos los roles | Crea o reemplaza la foto propia y devuelve `204 No Content`. |
| `DELETE /api/v1/users/me/profile/photo` | Todos los roles | Elimina la foto propia de forma idempotente y devuelve `204 No Content`. |
| `DELETE /api/v1/auth/sessions/current` | Todos los roles autenticados | Revoca el JWT presentado en PostgreSQL y devuelve `204 No Content`. |

La carga de foto usa el campo multipart `file`. El servidor valida el contenido real, admite JPEG/PNG de hasta 2 MiB, limita dimensiones, corrige orientación EXIF, reduce hasta 512 × 512 y vuelve a codificar sin metadatos. La lectura devuelve `Cache-Control: private, no-store`; `profilePhotoUrl` contiene la ruta relativa protegida o `null`.

En la versión académica, los frontends pueden conservar el JWT en `localStorage`. Deben eliminarlo al cerrar sesión o detectar su vencimiento y enviarlo únicamente mediante `Authorization: Bearer`; nunca en la URL. Esta decisión simplifica los clientes, pero no sustituye la protección frente a XSS. No se implementa limitación de intentos en este incremento.

## Respuesta de error

`shared.dto.error.ApiErrorResponseDTO` extiende `ProblemDetail` de Spring. Documenta los campos estándar de RFC 9457 y añade:

- `errorCode`: código funcional estable para los frontends.
- `fieldErrors`: lista de `FieldValidationErrorResponseDTO`, con `field`, `code` y `message`. Es `[]` cuando no hay errores asociados a campos.

`shared.exception.GlobalExceptionHandler` usa `@RestControllerAdvice` y extiende `ResponseEntityExceptionHandler`: transforma las excepciones MVC y de negocio al formato `application/problem+json`, conservando el estado HTTP y encabezados de protocolo como `Allow`. El controlador se concentra en la petición y la respuesta correcta; las excepciones de negocio se traducen en este punto central.

| Situación | HTTP | `errorCode` |
|---|---|---|
| Campos que incumplen validaciones | `400` | `VALIDATION_FAILED`, con errores por campo. |
| JSON ilegible, tipo incorrecto o propiedad desconocida | `400` | `MALFORMED_JSON`, `TYPE_MISMATCH` o `UNKNOWN_PROPERTY`, según la causa. |
| Correo ya registrado | `409` | `EMAIL_ALREADY_REGISTERED`. |
| Credenciales incorrectas o cuenta inactiva | `401` | `INVALID_CREDENTIALS`. |
| Token inválido, vencido, revocado o con datos de cuenta obsoletos | `401` | `AUTHENTICATION_REQUIRED`. |
| Foto inexistente | `404` | `PROFILE_PHOTO_NOT_FOUND`. |
| Foto demasiado grande | `413` | `PROFILE_PHOTO_TOO_LARGE`. |
| Formato o contenido de foto inválido | `415` / `400` | `UNSUPPORTED_PROFILE_PHOTO_TYPE` / `INVALID_PROFILE_PHOTO`. |
| Método o tipo de contenido no admitido | `405` / `415` | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE`. |
| Fallo inesperado | `500` | `INTERNAL_ERROR`, con un mensaje genérico. |

`shared.security.ApiSecurityErrorHandler` adapta también los rechazos producidos antes del controlador en los filtros de Spring Security: `401 AUTHENTICATION_REQUIRED` para un Bearer ausente o inválido y `403 ACCESS_DENIED`, sin página HTML de login. Ambos manejadores usan `ApiErrorResponseFactory` para conservar el mismo contrato.

La fábrica añade un `instance` aleatorio con formato `urn:uuid:...` para identificar la incidencia sin reflejar la URL ni sus parámetros. El handler no devuelve valores rechazados, SQL, contraseñas, hashes ni mensajes internos de excepciones. Ante un `500`, su diagnóstico registra ese identificador, el tipo de excepción y ubicaciones de código, sin copiar el mensaje de la excepción ni los datos enviados. Los frontends pueden usar `errorCode` y `fieldErrors[].code` para decidir su presentación accesible.

## Ejecución local y perfiles

Instalar un JDK 25 y configurar `JAVA_HOME` con la carpeta raíz de esa instalación. `java -version`, `javac -version` y `.\mvnw.cmd -version` permiten comprobar las versiones utilizadas. En IntelliJ, seleccionar ese JDK como SDK del proyecto, hacer que el módulo lo herede y comprobar el JDK del ejecutor de Maven y de la configuración Run.

El desarrollo utiliza PostgreSQL instalado localmente. Antes de ejecutar el backend o las pruebas, iniciar PostgreSQL y crear desde DBeaver las bases `grownupsvet_dev` y `grownupsvet_test`, inicialmente sin tablas del proyecto. Ambas pueden estar en el mismo servidor, en `localhost:5432`.

Si las bases todavía no existen, conectar DBeaver a una base existente del servidor, como `postgres`, con una cuenta que tenga permiso para crear bases. Activar Auto-commit y ejecutar cada sentencia por separado:

```sql
CREATE DATABASE grownupsvet_dev;
CREATE DATABASE grownupsvet_test;
```

El usuario configurado para la aplicación debe poder conectarse y crear los objetos de las migraciones en esas bases.

La configuración separa las credenciales compartidas del destino de cada perfil:

| Archivo | Configuración |
|---|---|
| `src/main/resources/application.yaml` | Nombre de la aplicación, credenciales mediante `DB_USERNAME` y `DB_PASSWORD`, validación de Hibernate y perfil predeterminado `dev`. |
| `src/main/resources/application-dev.yaml` | Conexión a `jdbc:postgresql://localhost:5432/grownupsvet_dev`. |
| `src/test/resources/application-test.yaml` | Conexión a `jdbc:postgresql://localhost:5432/grownupsvet_test`. Solo está disponible al ejecutar las pruebas y no se empaqueta en la aplicación. |

Configurar `DB_USERNAME` y `DB_PASSWORD` como variables de entorno de Windows con las credenciales del PostgreSQL local. Si se crean o cambian mientras IntelliJ o una terminal están abiertos, cerrarlos y abrirlos nuevamente para que reciban los valores actualizados. No guardar contraseñas en los YAML ni en el repositorio.

El proceso también necesita el almacén PKCS#12 que contiene la clave privada de firma JWT. Debe permanecer fuera del repositorio. Para la clave local creada para este proyecto, configurar en la misma terminal o en la configuración de ejecución de IntelliJ:

```powershell
$env:JWT_KEYSTORE_PATH = "C:\Users\ASUS\.grownupsvet\keys\jwt-signing.p12"
$jwtSecurePassword = Read-Host "Contraseña del almacén JWT" -AsSecureString
$jwtPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($jwtSecurePassword)
try {
    $env:JWT_KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($jwtPasswordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($jwtPasswordPointer)
    Remove-Variable jwtSecurePassword, jwtPasswordPointer
}
$env:JWT_KEY_ALIAS = "grownupsvet-jwt-signing-2026-09"
```

`JWT_KEY_PASSWORD` solo se configura si la entrada privada usa una contraseña distinta; de lo contrario hereda `JWT_KEYSTORE_PASSWORD`. El emisor, audiencia, duración y tolerancia tienen valores predeterminados seguros para este contrato y pueden sobrescribirse con `JWT_ISSUER`, `JWT_AUDIENCE` y propiedades Spring cuando exista una necesidad de despliegue. Si falta la ruta, contraseña, alias o una clave RSA válida de al menos 2048 bits, la aplicación falla al arrancar en vez de operar sin firma.

Desde esta carpeta, iniciar el backend con:

```powershell
.\mvnw.cmd spring-boot:run
```

También se puede ejecutar `GrownupsvetBackendApplication` desde IntelliJ. En ambos casos, si no se activa otro perfil explícitamente, Spring utiliza `dev` y se conecta a `grownupsvet_dev`.

Flyway aplica las migraciones pendientes al cargar el contexto de Spring. V1 crea `users`, V2 `owner_profiles`, V3 `revoked_access_tokens` y V4 `user_profile_photos`; cada versión aplicada se registra en `flyway_schema_history`. Hibernate usa `ddl-auto: validate` para comprobar la correspondencia con las entidades. Los arranques posteriores conservan el esquema y no repiten las migraciones ya aplicadas.

La variable antigua `DB_URL` ya no se utiliza. Una variable `SPRING_DATASOURCE_URL`, un argumento de ejecución u otra sobrescritura explícita en IntelliJ puede tener prioridad sobre la URL del perfil; revisar esas opciones si la aplicación apunta a una base distinta de la prevista.

## Verificación

Para las pruebas de DTO, tipos JSON y protección del hash, sin base de datos:

```powershell
.\mvnw.cmd "-Dtest=UserLoginRequestDTOHttpTests,ApiErrorResponseDTOTests,UserTests" test
```

Para ejecutar toda la suite, comprobar que PostgreSQL esté funcionando, que `grownupsvet_test` exista y que las variables de credenciales estén disponibles. Ejecutar:

```powershell
.\mvnw.cmd test
```

`GrownupsvetBackendApplicationTests` activa automáticamente el perfil `test` mediante `@ActiveProfiles("test")`. No hace falta cambiar las variables de entorno entre el desarrollo y estas pruebas. Flyway prepara `grownupsvet_test` al cargar ese contexto, con su propia tabla `users` y su propio historial `flyway_schema_history`.

El test de aplicación comprueba el arranque, la persistencia real de una cuenta y el rechazo de correos duplicados después de normalizarlos. Los datos de cada prueba de persistencia se revierten al finalizar su transacción. Las tablas y el historial de migraciones permanecen en la base de pruebas; encontrar `users` sin filas después de ejecutar la suite es compatible con una prueba correcta.

`UserSignupHttpIntegrationTests` usa el perfil `test`, con filtros de seguridad reales y sin una transacción envolvente de prueba. Comprueba registro, hash Argon2id de hasta 128 caracteres Unicode, conservación de espacios de la contraseña, datos inválidos, propiedades de privilegios rechazadas, errores MVC y de seguridad, correo duplicado simultáneo y rollback completo cuando falla el perfil. `UserLoginHttpIntegrationTests` comprueba credenciales, estado activo, respuesta, claims, acceso Bearer y rechazo de una firma alterada. Las pruebas generan un par RSA efímero; nunca leen la clave privada local.

Verificación del 10 de septiembre de 2026: `mvnw.cmd clean verify` terminó con **183 pruebas, sin fallos ni omisiones**, validó las cuatro migraciones y generó el JAR. La ejecución usa `grownupsvet_test`; no sustituye una prueba manual de arranque contra `grownupsvet_dev` con el PKCS#12 local configurado.

Las pruebas exportan el contrato desde `/v3/api-docs` a `target/generated-openapi/openapi.json`. El [snapshot y procedimiento de regeneración](docs/openapi/README.md) incluyen una comprobación independiente de OpenAPI 3.1 y siete respuestas HTTP contra sus esquemas. Código y anotaciones son la fuente editable.

No guardar credenciales de la base, contraseñas del almacén ni archivos de clave en el repositorio. Las cuentas ficticias persistentes para desarrollo siguen pendientes; las pruebas crean y eliminan sus propios usuarios.

## Construcción del ejecutable

Con PostgreSQL y las credenciales disponibles, ejecutar desde esta carpeta:

```powershell
.\mvnw.cmd verify
```

Este comando ejecuta las pruebas, incluidas las que usan `grownupsvet_test`, y genera `target/grownupsvet-backend-0.0.1-SNAPSHOT.jar`. El perfil de pruebas no se incluye en ese JAR. Para arrancar el ejecutable con el perfil predeterminado `dev`, detener primero cualquier otra instancia del backend que esté utilizando el puerto 8080 y ejecutar:

```powershell
java -jar .\target\grownupsvet-backend-0.0.1-SNAPSHOT.jar
```

La configuración externa de base de datos y firma JWT también debe estar disponible para este proceso. Comprobar en el registro la URL de `grownupsvet_dev`, el resultado de Flyway y la línea `Started GrownupsvetBackendApplication` para verificar el arranque.

## Organización y convenciones

El proyecto sigue una organización MVC por dominios: controladores para peticiones y respuestas, DTOs para los datos de la API, servicios para reglas de negocio y transacciones, y repositorios para persistencia. El dominio `user` registra propietarios; `authentication` verifica credenciales y emite/valida JWT; `shared` contiene configuración y manejo uniforme de errores.

El apartado «Respuesta de error» describe el manejo implementado. Las [bases del contrato](docs/contrato-api-bases-propuestas.md) delimitan el incremento completo de acceso, perfil y sesión. La autorización se aplica por acción y recurso: un claim o rol no concede acceso a datos ajenos, y el backend contrasta el estado vigente de la cuenta en cada petición protegida.
