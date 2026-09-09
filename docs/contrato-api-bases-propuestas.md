# GrownupsVet: bases propuestas del contrato API

Fecha inicial: 6 de septiembre de 2026. Actualizado el 9 de septiembre con el incremento de login y JWT. Este documento recoge decisiones y estado de implementación; la especificación OpenAPI se genera desde el código. Los apartados de cierre de sesión, edición de perfil y foto siguen distinguiendo sus propuestas y pendientes.

El objetivo inmediato es que el backend y los dos frontends compartan un acuerdo preciso sobre los datos que intercambian. El orden del backlog puede adaptarse al desarrollo. Para probar el login, Esteban acepta cargar dos o tres cuentas de desarrollo mediante un script o consultas a PostgreSQL.

## Base disponible

- Arquitectura: Spring Boot, PostgreSQL, monolito MVC, JWT, roles y permisos. Emisión y validación JWT ya están implementadas; las reglas funcionales por recurso se añadirán con cada operación protegida.
- Propuesta documentada de organización por dominios, con controladores, DTOs, servicios y repositorios. Los DTOs describen la API; las entidades describen la persistencia.
- Ya existe el proyecto en `grownupsvet-backend`, con Spring Boot 4.1.1, Java 25 y springdoc 3.1.0. La primera operación de negocio implementada es `POST /api/v1/auth/registrations`: crea la cuenta `User` y su `OwnerProfile`, devuelve `201` con `id` y `email`, y documenta sus respuestas mediante anotaciones. Incluye validaciones y errores uniformes, descritos en [el README del backend](../README.md). Todavía no hay un contrato completo de acceso/perfil. Los documentos citan `grownupsvet-backend-model.md`, pero ese archivo no está disponible aquí.
- Esteban confirmó los campos del registro de propietario, el alcance de edición del perfil, correo de hasta 254 caracteres, contraseña de 15–128 sin composición obligatoria, el límite de edad y el acceso con JWT de 24 horas sin renovación. Para el recorrido posterior al registro priorizó simplicidad de implementación; se adopta registro → login → primera mascota. Los detalles técnicos pendientes se distinguen de las decisiones confirmadas en el apartado «Concreciones del 7 de septiembre».
- La primera versión tendrá un solo rol por cuenta, confirmado por Esteban. La [arquitectura de usuarios y permisos](arquitectura-usuarios-y-permisos.md) recoge los tres roles ya presentes en el código y el backlog, sus responsabilidades y los límites todavía pendientes de concretar.

## Acceso confirmado y accesibilidad propuesta

Esteban confirmó que la primera versión puede asumir acceso del propietario a un correo electrónico propio y utilizar **correo y contraseña**. El correo será el identificador de acceso; este mecanismo no necesita un nombre de usuario adicional.

Para el acceso se utilizan `email` como cadena con formato de correo y `password` como cadena de máximo 128 puntos de código; ambos obligatorios. La contraseña es información de entrada, se conserva sin recortar ni normalizar y no se devuelve. El login correcto responde `200 OK` con JWT de 24 horas y no emite token de renovación.

Propuesta para la interfaz de acceso:

- Etiquetas visibles «Correo electrónico» y «Contraseña», botón «Entrar» y opción «Mostrar contraseña» accesible por teclado.
- Permitir pegar y utilizar el autocompletado y los gestores de contraseñas del navegador. Identificar adecuadamente el propósito de ambos campos.
- Mostrar los errores junto al formulario con texto comprensible. Por ejemplo, un código técnico `INVALID_CREDENTIALS` puede corresponder al mensaje «No pudimos entrar. Revisa tu correo y contraseña». No exponer códigos HTTP, tokens ni detalles internos como instrucciones para la persona.
- Hacer visible «Olvidé mi contraseña». Se propone recuperar por correo; aún hay que concretar el flujo, los vencimientos y la entrega del mensaje.
- Mantener etiquetas, contraste, foco y controles fáciles de utilizar; comprobar el recorrido con personas representativas sin asumir sus capacidades únicamente por la edad.

Correo y contraseña pueden formar parte de un acceso accesible: W3C describe el soporte de gestores de contraseñas y de pegar como mecanismos para reducir la carga de recordar y transcribir. Mostrar la contraseña también puede facilitar la entrada. Estas pautas no acreditan por sí solas conformidad completa con WCAG. [W3C: autenticación accesible](https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html).

## Registro y perfil del propietario: decisiones confirmadas

Esteban definió los siguientes datos para crear la cuenta. Los nombres JSON de la tabla son los utilizados por `UserSignupRequestDTO` en el registro implementado:

| Dato solicitado | Propiedad JSON | Representación | Finalidad indicada |
|---|---|---|---|
| Correo electrónico | `email` | Cadena con formato de correo. | Identificador del login conforme al acuerdo anterior y medio alternativo de comunicación. |
| Fecha de nacimiento | `dateOfBirth` | Fecha sin hora, `YYYY-MM-DD`; `LocalDate` en Java y `DATE` en PostgreSQL. | Dato previsto para una posible adaptación de accesibilidad según edad; esa funcionalidad todavía no está confirmada. |
| Nombre completo | `fullName` | Cadena. | Nombre del propietario en su perfil; se recoge en un único campo. |
| Teléfono | `phoneNumber` | Cadena con código de país. | Medio principal de contacto mediante WhatsApp y llamadas. Se aceptarán números de otros países además de Colombia. |
| Contraseña | `password` | Cadena de entrada; se persiste únicamente su hash. | Credencial de autenticación. |

El teléfono se representa como texto para conservar el prefijo internacional y los dígitos. Su uso como contacto no cambia el identificador de login acordado. No se ha acordado que el teléfono sea único por cuenta ni que un número declarado implique que ya fue verificado o que disponga de WhatsApp.

El recorrido confirmado es crear la cuenta primero y registrar las mascotas después, con el propietario ya identificado. La petición de registro de cuenta no incorpora mascotas.

La adaptación automática de accesibilidad según la edad sigue siendo una idea en discusión. No hay reglas aprobadas de cambio de interfaz ni un mínimo de 60 años para registrarse. Esteban fijó un máximo de 130 años; su concreción se describe más adelante. El requisito de fecha de nacimiento y la decisión de implementar una adaptación de interfaz se documentan por separado.

Esteban confirmó que la foto de perfil sí se incluirá en la primera versión y será opcional para crear o usar la cuenta. Su preferencia es almacenarla directamente en PostgreSQL para el proyecto académico. La propuesta técnica es usar una tabla separada con contenido `bytea` y metadatos, consultada cuando se solicite la imagen; el detalle está en la [arquitectura de usuarios y permisos](arquitectura-usuarios-y-permisos.md). Los límites de carga y el contrato de la operación siguen como propuestas, todavía sin implementar. La foto del propietario es independiente de las imágenes de mascotas.

WhatsApp y llamadas describen los canales de contacto previstos por Esteban. Esta decisión no especifica ni implementa envíos automáticos, proveedores externos o verificación del teléfono. El mecanismo de recuperación de acceso continúa pendiente.

### Edición del perfil: alcance inicial confirmado

El propietario podrá modificar únicamente su teléfono y su foto de perfil. El resto de los datos personales solicitados en el registro no tendrá edición por parte del propietario en la primera versión:

| Dato | Edición por el propietario |
|---|---|
| Teléfono | Permitida; conserva el requisito de código de país y aceptación de números internacionales. |
| Foto de perfil | Permitida; la funcionalidad se incluye y aportar una foto es opcional. |
| Correo electrónico | No disponible en el alcance inicial. |
| Nombre completo | No disponible en el alcance inicial. |
| Fecha de nacimiento | No disponible en el alcance inicial. |

Esta restricción debe respetarse en la API, además de en el formulario: las peticiones de edición de perfil solo podrán modificar los campos autorizados. No se expondrá una actualización genérica de la entidad `User`.

«No editable» describe lo que el propietario puede hacer en la aplicación; no exige impedir toda corrección administrativa futura en la base de datos. El cambio de correo, las correcciones de nombre o fecha de nacimiento y un posible flujo de soporte o PQR quedan fuera del desarrollo inicial. Esta decisión no incorpora una operación administrativa ni un procedimiento de actualización directa en la base de datos.

La contraseña se trata como una credencial, separada de la edición general del perfil. El cambio o la recuperación de contraseña corresponde al alcance de autenticación y no se resuelve mediante una actualización del perfil; su flujo todavía está pendiente. Esteban aceptó 15–128 caracteres, espacios permitidos, sin mezcla obligatoria de tipos de caracteres y con rechazo de contraseñas comunes. Esa política ya se valida en registro y deberá aplicarse a los futuros flujos que establezcan una nueva contraseña; el DTO de login conserva la contraseña recibida sin transformarla.

Antes de cerrar el contrato, concretar las validaciones de registro y de teléfono. Las funcionalidades aplazadas de soporte, corrección de datos y adaptación por edad no bloquean la construcción de esta base.

### Estado de implementación

La entidad `User` y la migración `V1__create_users.sql` representan la cuenta compartida entre propietarios, veterinarios y administradores. `OwnerProfile` y la nueva migración `V2__create_owner_profiles.sql` incorporan los datos exclusivos del propietario en `owner_profiles`, conservando V1. La tabla usa el mismo UUID de la cuenta como clave primaria y referencia; no obliga a inventar datos para cuentas profesionales o cuentas históricas.

`UserSignupController` expone `POST /api/v1/auth/registrations` y valida `UserSignupRequestDTO`. `UserSignupService` crea cuenta activa con rol `OWNER` y perfil de forma transaccional, codifica la contraseña con Argon2id y devuelve `UserSignupResponseDTO` con `id` (cadena UUID) y `email` normalizado. No acepta rol ni estado enviados por el cliente; las propiedades desconocidas se rechazan con `400`. El alta no genera sesión ni devuelve mascotas o foto.

`GlobalExceptionHandler` y `ApiSecurityErrorHandler` comparten `ApiErrorResponseFactory` para emitir `application/problem+json`. Springdoc genera el alcance disponible desde código y anotaciones. Esta descripción distingue código incorporado de resultados de verificación: los resultados concretos y la copia generada del contrato se registran con su evidencia correspondiente.

## Concreciones del 7 de septiembre

Este apartado registra la respuesta de Esteban y las recomendaciones para avanzar SCRUM-67. «Confirmado» describe una decisión del usuario; «propuesto» todavía no implica aceptación del equipo ni implementación. C4 queda a cargo de los otros dos compañeros según el reparto comunicado por Esteban; esta actualización se limita al contrato de acceso/perfil.

### Validaciones del registro

| Dato | Decisión o propuesta | Estado |
|---|---|---|
| Correo | Esteban aceptó conservar 254 caracteres para la dirección completa. Texto obligatorio con formato de correo y unicidad sobre el valor normalizado. | Implementado en registro. |
| Nombre completo | Esteban confirmó máximo 150 caracteres y al menos un nombre y un apellido en un único campo. Se validan dos o más componentes con alguna letra cada uno; admite letras y marcas Unicode, espacios, apóstrofos, guiones y puntos de iniciales, sin dígitos ni controles. El límite cuenta puntos Unicode sobre el texto recibido, incluidos los espacios; los espacios exteriores se retiran al guardar. | Implementado en registro. La composición mínima no verifica identidad ni permite reconocer jurídicamente un apellido. |
| Teléfono | Números internacionales E.164, sin limitar a Colombia ni deducir el país por residencia; validación de formato y metadatos mundiales. | Implementado en registro con Google libphonenumber. |
| Nacimiento | Fecha válida y no futura; edad en años cumplidos de 0 a 130 inclusive, calculada por el servidor con fecha de referencia en `America/Bogota` y `Clock` inyectable. | Implementado en registro. |
| Contraseña | 15–128 puntos de código Unicode, espacios preservados, sin composición obligatoria y rechazo local de contraseñas comunes. No se admite contenido compuesto solo por espacios. | Implementado en registro. Limitar intentos sigue pendiente. |

El límite de correo se refiere a la dirección completa. OWASP recoge 254 como máximo total para una validación inicial razonable. Esto no demuestra que el buzón exista o pertenezca a quien se registra. [OWASP: validación de correo](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html#email-address-validation).

La política aceptada se apoya en NIST: para autenticación de un solo factor establece un mínimo de 15 caracteres y evita reglas de composición. El máximo de 128 es una elección del proyecto aceptada por Esteban. Se conservarán pegado, gestores y visualización accesible de contraseña. El registro cuenta puntos de código Unicode y conserva la contraseña original al codificarla con Argon2id, sin truncamiento ni normalización silenciosa. El login deberá comprobarla con el mismo tratamiento. [NIST: contraseñas](https://pages.nist.gov/800-63-4/sp800-63b/authenticators/#passwords).

La lista local de SecLists tiene 10.000 entradas y se compara con la contraseña completa ignorando mayúsculas únicamente para esta consulta. No se elimina el espacio exterior ni se bloquea por palabras contenidas en una frase, y ninguna contraseña se envía a un servicio externo. Es una base limitada de contraseñas comunes; no equivale a una colección exhaustiva de credenciales filtradas. El [recurso versionado](../src/main/resources/password-blocklist/README.md) documenta origen, licencia MIT, commit y checksum.

### Teléfono internacional

Propuesta de interfaz: selector de país/prefijo y campo de teléfono que permita pegar un número internacional completo. Un número ya escrito con `+` no debe recibir otra vez el prefijo seleccionado.

Contrato implementado en el registro: `phoneNumber` es una cadena en formato internacional canónico E.164, con `+` seguido del código de país y los dígitos correspondientes, hasta 15 dígitos (16 caracteres con `+`). La interfaz puede mostrar espacios; normaliza el valor antes de enviarlo. La futura consulta del perfil conservará ese formato, pero todavía no existe esa operación.

El backend comprueba tanto la forma canónica como la validez con metadatos de Google libphonenumber. No basta contar dígitos o aplicar un regex colombiano. Evitar el uso de `parse` como validador único: puede extraer números de texto y transformar letras; rechazar entradas ajenas al contrato antes de interpretarlas. No exigir que el número pertenezca al país de residencia ni limitarlo a móviles sin un requisito adicional.

Esta política cubre números internacionales del plan E.164 que reconozcan los metadatos utilizados; no promete validar todos los códigos cortos, extensiones ni excepciones de numeración del mundo. Mantener la biblioteca actualizada. La validación no prueba titularidad, disponibilidad, SMS ni WhatsApp y no incorpora verificación telefónica.

Fuentes: [ITU-T E.164, edición de febrero de 2026](https://www.itu.int/rec/T-REC-E.164-202602-I/en) y [Google libphonenumber: validación y límites](https://github.com/google/libphonenumber/blob/master/FAQ.md).

### Correo duplicado y resultado del registro

Esteban quiere una respuesta que permita mostrar que el correo ya está registrado y ofrecer recuperación de acceso. El registro implementa `409 Conflict` con `errorCode: EMAIL_ALREADY_REGISTERED`, usando `ApiErrorResponseDTO`; `400` corresponde a cuerpos o campos inválidos. El mensaje es «Este correo ya está registrado. Puedes iniciar sesión o recuperar tu contraseña». No incluye datos del titular. Login ya está disponible; recuperación continúa pendiente. [RFC 9110: 409](https://www.rfc-editor.org/rfc/rfc9110.html#name-409-conflict).

Esta respuesta revela que la dirección está registrada. Es la experiencia solicitada por Esteban; el riesgo se documenta y el control de abuso queda pendiente, sin afirmar que ya esté mitigado. Para login y recuperación se prevén respuestas genéricas sobre la existencia de la cuenta. [OWASP: mensajes de autenticación](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#authentication-and-error-messages).

El control de duplicados combina una comprobación previa del correo normalizado y la restricción única `uk_users_email` de PostgreSQL. El servicio traduce esa violación concreta a `EmailAlreadyRegisteredException`, cubriendo también una colisión posterior a la comprobación inicial. Otras fallas de integridad se propagan para devolver un error interno genérico; cuenta y perfil participan de la misma transacción.

El objetivo confirmado es conducir a registrar la primera mascota. Se adopta **registro → login → primera mascota**. `POST /api/v1/auth/registrations` devuelve `201 Created` con `id` y `email`, sin token ni redirección HTTP; el frontend muestra el éxito y conduce a `/login`. `POST /api/v1/auth/sessions` autentica y emite el JWT. La creación de una mascota será una operación posterior de P04; el éxito del alta no depende de ella.

Después del primer login del propietario, el frontend conduce al registro de la primera mascota. No se fija una propiedad como `hasPets` en el JWT ni una redirección permanente por ausencia de mascotas. Debe concretarse cómo representar el paso pendiente de forma persistente y si puede posponerse, para que una cuenta recién creada no pierda ese estado al volver a entrar o desde otro navegador. Esta señal dinámica deberá provenir de la API correspondiente, no de un claim que queda fijo durante 24 horas. Los nombres de rutas de pantalla, salvo `/login` planteada por Esteban, los definirán los frontends.

### Login y sesión de 24 horas

Implementado: `POST /api/v1/auth/sessions` devuelve `200 OK`, JWT con identificación, correo, rol y permisos, duración de 24 horas (`exp = iat + 86400`) y ausencia de tokens de renovación. Al vencer es necesario volver a autenticarse.

`UserLoginResponseDTO` contiene `accessToken`, `tokenType` con valor `Bearer`, `expiresIn` con valor `86400` y `user` con `id`, `email`, `role` y `permissions`. Esta forma está publicada en OpenAPI. La respuesta incluye `Cache-Control: no-store`.

El JWT RS256 contiene `sub` con el UUID de la cuenta, `email`, `role`, `permissions`, `iat`, `exp`, `jti`, `iss=grownupsvet-backend` y `aud=grownupsvet-clients`. `iat`/`exp` son instantes Unix y `jti` identifica el token. No duplica `id` ni incluye contraseña, hash, teléfono, nacimiento o foto. [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html).

El catálogo inicial contiene `PROFILE_READ_SELF`, `PROFILE_UPDATE_SELF`, `PROFILE_PHOTO_READ_SELF` y `PROFILE_PHOTO_UPDATE_SELF`. Spring Security convierte el claim `permissions` en autoridades sin prefijo. Un permiso para editar perfil no concede acceso al perfil de otra persona: esa propiedad se comprobará en el servicio al implementar la operación. La consulta de `active` y rol vigente en cada petición queda pendiente; hoy los claims son una fotografía válida hasta el vencimiento. [OWASP: autorización en cada petición](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html#validate-the-permissions-on-every-request).

Propuesta de cierre de sesión: registrar en PostgreSQL la revocación de `(issuer, jwtId)` junto con su vencimiento, validar firma y claims antes de registrar/consultar esa identidad y comprobar la revocación en cada petición protegida. Limpiar registros cuando el token ya no pueda aceptarse. Después del cierre confirmado, el cliente elimina su copia. Esto conserva el JWT sin refresh y permite invalidar copias del token; borrar solo la copia del navegador no lo consigue. La tabla aún no existe. [OWASP: revocación JWT](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html#jwt-denylist).

Spring Security acepta únicamente RS256 y comprueba firma, `exp`, emisor y audiencia, con 30 segundos de tolerancia de reloj. Las pruebas cubren token vencido, emisor incorrecto, audiencia incorrecta y firma alterada. [Spring Security: validación JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-validation).

Credenciales incorrectas, correo inexistente o cuenta inactiva producen la misma respuesta `401 INVALID_CREDENTIALS`. Un token ausente, inválido o vencido produce `401 AUTHENTICATION_REQUIRED`; una autoridad insuficiente produce `403 ACCESS_DENIED`. La API devuelve Problem Details JSON, no HTML ni una redirección. La revocación aún no existe. [OWASP: estados HTTP para API](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html#http-return-code).

Antes de implementar el almacenamiento/transporte del token en los dos clientes, concretar sus tecnologías y si se servirán bajo el mismo origen o distintos. No asumir `localStorage` ni fijar cookies sin revisar ese contexto. La carga de fotos protegidas debe usar el mismo acuerdo de autenticación; un elemento `img` no añade por sí solo un encabezado Bearer.

### Foto del perfil

Esteban mantiene la preferencia de almacenar directamente en PostgreSQL y solicita ayuda para el diseño. La propuesta detallada de tabla, carga separada, validación, normalización y lectura protegida está en [arquitectura de usuarios y permisos](arquitectura-usuarios-y-permisos.md#foto-opcional-almacenada-en-postgresql). Los formatos y límites siguen siendo propuestas de aplicación, no decisiones ya aceptadas por el equipo.

### Trabajo siguiente y evidencia

Avanzar por operaciones: concretar su petición, respuesta y reglas pendientes; implementar DTOs, comportamiento y anotaciones; probar la operación y generar su OpenAPI con springdoc. El contrato cubrirá progresivamente registro, login, sesión, perfil y foto. No se exige escribir previamente un YAML manual ni implementar todas las operaciones para publicar un primer contrato parcial.

El nombre quedó definido e implementado con el límite de 150 y dos o más componentes. El login ya tiene un catálogo inicial de permisos de perfil propio. Permanecen por definir el seguimiento del paso de primera mascota, los permisos de módulos posteriores y el almacenamiento del token en los frontends; los límites de foto y detalles de revocación se mantienen como propuestas técnicas.

Registro, login y JWT aportan implementación a SCRUM-23 y contrato generado a SCRUM-67; la evidencia debe especificar este alcance incremental. El vínculo SCRUM-67 → SCRUM-6 debe conservar la trazabilidad. La revisión compartida y las operaciones de perfil/foto siguen siendo requisitos antes de declarar completo el alcance mayor. La implementación conserva V1 y V2 sin modificarlas.

## Acuerdo sobre los datos

Propuesta: propiedades JSON en inglés y `camelCase`; nombres completos y claros para los esquemas y DTOs de petición y respuesta. Cada propiedad declara tipo, obligatoriedad, aceptación de `null`, restricciones y ejemplo. Ser opcional y aceptar `null` son decisiones distintas.

| Dato | Representación propuesta en JSON | Detalle que debe quedar explícito |
|---|---|---|
| Texto | `"123"` | Longitud, formato y si se permiten espacios o cadena vacía. El número `123` no sustituye al texto. |
| Entero | `123` | Límites y unidad cuando aplique. La cadena `"123"` no sustituye al número. |
| Decimal | `12.5` | Unidad, límites y precisión requerida; acordar otra representación si el dominio exige exactitud que el cliente no puede conservar. |
| Booleano | `true` o `false` | Rechazar `"true"`, `"false"`, `0` y `1` cuando el contrato exige booleano. |
| Fecha sin hora | `"2026-09-06"` | `type: string` y `format: date`; validar que la fecha exista. |
| Instante | `"2026-09-06T15:30:00Z"` | RFC 3339; propuesta de emitir en UTC. Una futura hora de agenda necesita sus propias reglas de zona horaria. |
| Enumeración | Cadena entre los valores publicados | Mayúsculas/minúsculas y catálogo explícitos; evitar posiciones numéricas de enumeraciones Java. |
| Lista | Arreglo JSON, incluido `[]` | Tipo de sus elementos, límites y forma de paginación cuando corresponda. |

El identificador de la cuenta queda publicado en `UserSignupResponseDTO` como una cadena con formato UUID. Las operaciones posteriores que representen la misma cuenta deben conservar esa forma. Otros recursos definirán su identificador de manera explícita, sin deducir su formato público únicamente del tipo de columna SQL.

La exigencia de tipos estrictos de esta tabla corresponde a cuerpos JSON. Los parámetros de ruta y consulta se transportan como texto en la URL; su interpretación y serialización se documentan por separado.

## Un contrato compartido

Se adopta **OpenAPI generado desde código y anotaciones**, siguiendo el enfoque que Esteban utilizó en StayHub. El backend ya incluye `springdoc-openapi-starter-webmvc-ui` 3.1.0. Diseñar el acuerdo de una operación antes de programar su comportamiento no obliga a mantener una especificación manual independiente.

1. Mantener controladores, DTOs, anotaciones y configuración de springdoc como única fuente editable del contrato técnico. Usar `@Operation`, `@ApiResponse`, `@Schema` y ejemplos donde la inferencia no describa suficientemente el acuerdo.
2. Generar la especificación mediante springdoc. Sus salidas configuradas son `/v3/api-docs` (JSON) y `/v3/api-docs.yaml` (YAML); Swagger UI permite visualizarlas en `/swagger-ui/index.html`. La seguridad permite esas rutas, registro y login sin Bearer; las demás rutas requieren autenticación.
3. Exportar y versionar una copia generada, identificada con su revisión de código, para los dos frontends. No mantener ni corregir a mano otro YAML de las mismas operaciones. La exportación puede automatizarse durante la verificación de Maven; esa automatización aún no está configurada.
4. Verificar la estructura OpenAPI, referencias y ejemplos, y contrastar las respuestas reales mediante pruebas HTTP. Declarar un error o un esquema de seguridad en anotaciones no implementa su manejo ni protege el endpoint. Publicar temprano el alcance disponible y distinguir lo implementado de cualquier contrato preparatorio.
5. Si los frontends usan TypeScript, generar sus tipos y cliente desde la misma copia publicada, comprobando la compatibilidad del generador. Ante cambios, actualizar código/anotaciones, regenerar y revisar la diferencia del contrato, y comunicarla a ambos consumidores. Los archivos generados no se corrigen a mano.

Fuentes: [springdoc: generación desde código](https://springdoc.org/#introduction) y [springdoc: exportación con Maven](https://springdoc.org/plugins.html).

Los tipos de TypeScript ayudan durante el desarrollo, pero no validan por sí solos los datos recibidos en ejecución. Los formularios convierten sus valores al tipo acordado antes de enviarlos; el servidor valida lo que realmente recibe.

## Comportamiento del backend

El primer incremento implementa `GlobalExceptionHandler`, un `@RestControllerAdvice` que extiende `ResponseEntityExceptionHandler`. Los cuerpos JSON mal formados, tipos incorrectos, propiedades desconocidas y validaciones fallidas devuelven `400` con `ApiErrorResponseDTO` en `application/problem+json`. La configuración rechaza conversiones de números o booleanos a cadenas y propiedades adicionales. Los errores por campo se incluyen cuando el campo puede identificarse sin reflejar datos arbitrarios del cliente.

Los códigos globales distinguen `VALIDATION_FAILED`, `MALFORMED_JSON`, `TYPE_MISMATCH` y `UNKNOWN_PROPERTY`. `fieldErrors` utiliza códigos como `REQUIRED`, `INVALID_EMAIL`, `INVALID_FULL_NAME`, `INVALID_DATE_OF_BIRTH`, `INVALID_PHONE_NUMBER`, `INVALID_PASSWORD_LENGTH` y `COMMON_PASSWORD`. El correo duplicado devuelve `409 EMAIL_ALREADY_REGISTERED`. Se preservan los estados y encabezados propios de MVC para método o formato no admitido; una falla inesperada devuelve `500 INTERNAL_ERROR` con mensaje genérico.

`ApiSecurityErrorHandler` adapta también `401` y `403` desde los filtros JWT, que pueden fallar antes del controlador. Ambos manejadores comparten una fábrica de errores y un `instance` aleatorio `urn:uuid:...`. No se incluyen contraseñas, hashes, valores rechazados, SQL ni mensajes internos de excepciones. El log de un `500` usa identificador, clase y ubicaciones de código, sin copiar el mensaje potencialmente sensible.

La verificación HTTP debe comprobar la deserialización real y sus conversiones automáticas: declarar `String` o añadir `@Valid` no sustituye esa evidencia. En este incremento, enviar `role`, `active` u otra propiedad desconocida debe fallar, en lugar de ignorarse o modificar la cuenta.

La aceptación del incremento requiere pruebas HTTP con datos válidos y casos que cubran el problema real: número o booleano por texto, campo obligatorio ausente, `null` no permitido, fecha inválida y propiedades adicionales. También deben comprobarse el correo duplicado, las transacciones y la concordancia entre respuestas y esquemas. No basta con probar directamente el método del servicio.

## Cuentas para desarrollo

Cargar las cuentas ficticias mediante un script repetible cuando exista el esquema de usuarios. El script debe utilizar el formato de contraseña codificada que espere el login y poder ejecutarse sin duplicar cuentas. Documentar cómo usarlas en el entorno de desarrollo. Esto permite probar el acceso sin implementar previamente la administración de personal.

## Alcance del OpenAPI incremental

La especificación generada publica `POST /api/v1/auth/registrations` y `POST /api/v1/auth/sessions`, incluido el esquema Bearer JWT para rutas protegidas. El paso de primera mascota, las comprobaciones funcionales de permisos, las operaciones de foto, la revocación y el almacenamiento del token se concretan antes de sus respectivos incrementos. Cada operación ampliará la misma especificación desde el código; no hace falta escribir un YAML manual completo para avanzar.

El proyecto usa Maven, Spring Boot 4.1.1 y Java 25. Los perfiles `dev` y `test` seleccionan sus respectivas bases PostgreSQL; las credenciales se configuran mediante variables de entorno. El README explica la ejecución y la verificación con la base de pruebas. Las reglas de módulos posteriores se resuelven cuando se incorpore su contrato.

## Referencias técnicas

- [OpenAPI 3.1.1, tipos de datos](https://spec.openapis.org/oas/v3.1.1.html#data-types): los tipos se restringen mediante `type`; declarar un `format` no garantiza que todas las herramientas lo validen. Esta referencia no fija todavía la versión que usará el proyecto.
- [Spring MVC, RequestBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): conversión del cuerpo HTTP y validación de objetos de entrada.
- [OpenAPI Generator, cliente TypeScript Fetch](https://openapi-generator.tech/docs/generators/typescript-fetch/): alternativa para generar el cliente si se confirma TypeScript en los frontends.
