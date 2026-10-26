# Admisión persistente de inicio de sesión

Incremento de auditoría del 26/10/2026. Contrato **0.11.0**: conserva `POST /api/v1/auth/sessions` y su respuesta de acceso; añade la respuesta documentada `429 LOGIN_RATE_LIMITED`. La implementación y sus pruebas locales no acreditan por sí solas la activación o publicación en Cloud Run.

**Corte: 26 de octubre de 2026, America/Bogota. Alcance parcial:** P1/P2 web y P3 API, según la evidencia publicada de cada componente. La auditoría final del proyecto completo corresponde a su finalización; este documento registra únicamente el incremento de admisión de acceso.

## Política y alcance

Con la función habilitada, cada petición JSON válida admite hasta **10 intentos por correo normalizado y peer de transporte en una ventana de 15 minutos**, y **60 intentos por peer en una ventana de un minuto**. Son ventanas fijas desde su primera admisión, compartidas entre instancias mediante PostgreSQL. Se cuentan accesos correctos y fallidos antes de buscar la cuenta o ejecutar Argon2. Las peticiones rechazadas no prolongan una ventana ni incrementan sus contadores. Un acceso correcto tampoco reinicia el cupo.

La combinación correo/peer limita el abuso sin desactivar la cuenta ni bloquearla globalmente por correo. Otra combinación de origen conserva su propio cupo. Al vencer una ventana se puede volver a intentar; no se requiere intervención administrativa. La política es igual para cuentas conocidas, desconocidas, pendientes de activación e inactivas. Las validaciones de JSON/campos continúan devolviendo sus errores habituales antes de la admisión.

Un rechazo devuelve `application/problem+json`, `errorCode=LOGIN_RATE_LIMITED`, mensaje genérico en español, `Retry-After` con **segundos enteros positivos redondeados hacia arriba** y `Cache-Control: private, no-store`. No incluye el correo, dirección, contraseña, cupos de una cuenta ni su existencia/estado. El cliente conserva los campos, informa la espera y deja que el usuario reintente; no repite automáticamente la contraseña.

## PEER y encabezados de proxy

El único modo admitido es `PEER`: `HttpServletRequest.getRemoteAddr()`, normalizado como dirección literal. El código no lee `X-Forwarded-For`, `Forwarded` ni `CF-Connecting-IP`. **PEER representa el extremo de transporte observado por el servidor; no se afirma que sea la dirección de la persona.** Cloudflare, Cloud Run, un NAT o un proxy pueden agrupar usuarios bajo el mismo peer y compartir el límite de 60/minuto.

La API actual también admite acceso directo: un encabezado Cloudflare por sí solo no acredita que una petición atravesó ese servicio. No se añade un encabezado secreto ni se inventa una cadena de confianza. Al activar la función, el arranque rechaza `server.forward-headers-strategy=framework`. Admite `none`; con `native` exige `server.tomcat.remoteip.internal-proxies=^$` y `trusted-proxies` vacío o `^$`. Así Tomcat no sustituye el peer por un encabezado recibido. No habilitar confianza generalizada para hacer que el contador parezca ser por IP humana.

Antes de activar en un entorno compartido, comprobar su manifiesto/configuración, medir un recorrido acotado por gateway y API directa y aceptar el impacto de un peer agrupado. La comprobación HTTP local de encabezados falsificados acredita que la aplicación no los selecciona; no sustituye esa inspección del entorno real. La defensa no garantiza impedir ataques distribuidos ni sustituye límites volumétricos de infraestructura.

## Configuración

La función está **deshabilitada por omisión** para conservar la operación de despliegues existentes mientras se verifica su origen. Activarla requiere configuración deliberada y una clave HMAC estable y privada, independiente de contraseñas, JWT y otros secretos. No publicar su valor ni pasarlo por argumentos o URLs.

| Variable | Valor inicial / condición |
| --- | --- |
| `LOGIN_RATE_LIMIT_ENABLED` | `false`; usar `true` después de validar el entorno |
| `LOGIN_RATE_LIMIT_HMAC_SECRET` | Base64 de al menos 32 bytes aleatorios; obligatoria al habilitar |
| `LOGIN_IDENTIFIER_ORIGIN_MAXIMUM` | `10` |
| `LOGIN_IDENTIFIER_ORIGIN_WINDOW` | `PT15M` |
| `LOGIN_ORIGIN_MAXIMUM` | `60` |
| `LOGIN_ORIGIN_WINDOW` | `PT1M` |

El modo está fijado a `PEER`. La configuración rechaza máximos no positivos y ventanas fuera de un segundo a 24 horas. Los máximos tienen límites de protección de 10 000 por combinación y 100 000 por peer; estos límites de configuración no cambian la política inicial aprobada.

Todas las instancias que compartan el servicio deben recibir la misma clave y política. Rotar la clave cambia los identificadores de los contadores y reinicia efectivamente el cupo; es una operación deliberada, no un mecanismo de reintento. Una clave ausente o inválida al habilitar impide arrancar; no se genera una clave distinta en cada instancia.

## Persistencia, concurrencia y limpieza

V23 añade `login_attempt_rate_limits` sin modificar V1–V22. Guarda claves HMAC-SHA256 con prefijos de propósito distintos, inicio/fin de ventana y cantidad admitida; no conserva correo ni peer en claro. Tiene restricciones de forma/integridad e índice de expiración. No contiene contraseñas, JWT ni contenido clínico y no crea una relación con una cuenta.

Cada admisión usa una transacción corta `REQUIRES_NEW` con timeout de cinco segundos: bloquea primero el contador de peer y luego su combinación, comprueba cupo y confirma ambos incrementos antes de volver al controlador. El hash Argon2 y la consulta de cuenta suceden después. Los fallos de credenciales no revierten los contadores; el servicio no retiene locks mientras verifica la contraseña. El cupo compartido se comprueba antes de crear combinaciones nuevas cuando el peer está agotado.

La expiración se comprueba durante cada admisión, aunque la limpieza no haya corrido. Un trabajo cada minuto retira hasta 1000 contadores vencidos usando `SKIP LOCKED`, sin afectar ventanas activas. Con cero instancias no corre la limpieza; los registros pendientes se retiran al reanudar. La expiración funcional no depende de ella. Un fallo de PostgreSQL no permite el acceso por un camino alterno sin limitación.

## Verificación reproducible

Con JDK 25 y PostgreSQL 18 exclusivo de pruebas, ejecutar desde el backend:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=LoginAttemptRateLimitConfigurationTests,LoginAttemptRateLimitHttpIntegrationTests,UserLoginHttpIntegrationTests' test
```

La suite nueva usa un esquema UUID propio, reloj inyectable, cuentas ficticias y conexiones PostgreSQL reales. Comprueba límites exactos, normalización, `429`/header/caché, privacidad, cuenta inactiva/desconocida, expiración y redondeo, admisiones concurrentes desde dos servicios con el mismo almacén, rollback externo, ausencia de Argon2 en el rechazo, encabezados falsificados, modo deshabilitado y limpieza. Las pruebas de configuración rechazan claves/políticas inválidas y proxies que reescribirían el peer. No utilizan Render ni correo externo.

El ejemplo `login-rate-limited.json` y el OpenAPI se generan desde las pruebas/Java. La validación completa y la copia del snapshot siguen el [procedimiento del contrato](openapi/README.md); la ejecución focalizada no sustituye la regresión completa, CI remoto ni la aceptación del despliegue.
