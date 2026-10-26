# Verificación de Gmail SMTP local y Cloud Run — 16/10/2026

El usuario autorizó una cuenta Gmail existente para enviar mensajes reales desde el backend local y Cloud Run. Se verificaron autenticación SMTP local, arranque local en el puerto 8081, recepción de una prueba SMTP directa y recepción del correo de recuperación generado mediante la API en nube. La revisión `grownupsvet-backend-00004-mzr` está lista y recibe el 100 % del tráfico. La confirmación del usuario de que ambos mensajes llegaron a **Recibidos** constituye la evidencia de recepción; el estado `SENT` acredita por separado aceptación SMTP.

## Cambios implementados

- El validador productivo admite `sandbox.smtp.mailtrap.io` o `smtp.gmail.com`. Para Gmail exige puerto 587, autenticación, STARTTLS activado y obligatorio, y remitente igual a la cuenta autenticada. Conserva las validaciones de base de datos, URLs y proxies.
- `scripts/deploy-google-cloud.ps1` acepta `MailProvider = 'Gmail'`. El valor predeterminado continúa siendo `Mailtrap`. Los bindings SMTP siguen usando referencias de Secret Manager con versiones numéricas.
- `scripts/configure-gmail.ps1` captura la cuenta y contraseña mediante terminal o formulario `-ShowWindow`. Guarda una `PSCredential` fuera del repositorio en `%USERPROFILE%\.grownupsvet\gmail\smtp-credential.clixml`. DPAPI cifra la contraseña para el usuario de Windows; el nombre de usuario queda legible en el XML.
- `. .\scripts\configure-gmail.ps1 -UseSavedCredential` carga SMTP en el proceso de PowerShell actual. No guarda la contraseña en el registro de variables de entorno de Windows ni altera claves de base de datos, JWT, HMAC o cifrado de correos.
- El helper fija la importación del módulo de seguridad de PowerShell a la instalación de `$PSHOME`, para evitar resolución incorrecta desde otras rutas. Esta corrección se comprobó con Windows PowerShell 5 y PowerShell 7.
- La descripción OpenAPI se actualizó en Java; el snapshot se regeneró y validó. También incorpora el endpoint público `hello` que ya existía en el árbol local antes de este cambio.

## Verificación ejecutada

| Comprobación | Resultado |
|---|---|
| `ProductionConfigurationValidatorTests` | 20 pruebas, sin fallos, errores ni omisiones |
| Generación OpenAPI desde prueba HTTP | 1 prueba, sin fallos, errores ni omisiones |
| Validador independiente OpenAPI | OpenAPI 3.1.0 válido; 41 ejemplos HTTP disponibles compatibles con sus esquemas |
| Alcance de los ejemplos | Se reutilizaron los ejemplos generados disponibles; no se repitieron sus 41 recorridos HTTP |
| Despliegue offline | Cuatro combinaciones Cloud SQL/PostgreSQL externo y Mailtrap/Gmail válidas; default Mailtrap conservado; proveedor desconocido y referencia `:latest` rechazados |
| Helper local | Parser válido, normalización de dirección/espacios y rechazo de dirección inválida comprobados con valores ficticios |
| Persistencia DPAPI | Exportación/importación de una credencial ficticia conserva su valor; contraseña ausente en texto plano del XML |
| Helper y módulo de seguridad | Importación desde `$PSHOME` comprobada en hosts PowerShell 5 y 7 |
| Empaquetado | `mvnw.cmd -DskipTests package` correcto; no sustituye las pruebas anteriores |
| Diferencias | `git diff --check` sin errores; avisos de normalización LF/CRLF |

Logs locales ignorados: `target/gmail-configuration-tests.log`, `target/gmail-openapi-generation.log` y `target/gmail-package.log`. Los cambios de despliegue PostgreSQL externo preexistentes se conservaron. No se realizó commit ni push.

## Colas antes de la transición

Auditoría agregada, sin destinatarios, payloads, códigos ni tokens. Conexiones y transacciones de solo lectura, terminadas mediante rollback; conexión Render con TLS y validación `verify-full`.

| Cola | Local demo | Cloud Render |
|---|---|---|
| Citas | Vacía | Vacía |
| Recuperación | Vacía | 3 `SENT` |
| Invitaciones | 1 `SENT` | Vacía |
| `PENDING` / `SENDING` | 0 / 0 | 0 / 0 |

La auditoría refleja ese momento. Se repitió inmediatamente antes de la transición y ambas bases conservaron `PENDING=0` y `SENDING=0` en las colas auditadas. Los mensajes `SENT` de Sandbox no se reenviaron automáticamente.

## Autenticación y recepción local

La credencial se capturó fuera de Git y la autenticación SMTP con Gmail fue satisfactoria. El backend local arrancó en `127.0.0.1:8081` con readiness `UP`. Se envió una prueba SMTP directa a la cuenta Gmail autorizada y el usuario confirmó que llegó a Recibidos. Esta prueba acredita transporte/recepción directa; el recorrido de recuperación de la aplicación se comprobó posteriormente en nube.

## Imagen y revisión Cloud Run

| Elemento | Evidencia del 16/10/2026 |
|---|---|
| Servicio y región | `grownupsvet-backend`, `us-central1` |
| Cloud Build | `3aab8781-5125-453f-8999-0e2c9d7d249e`, `SUCCESS` |
| Imagen | `us-central1-docker.pkg.dev/project-4f1c67ce-5621-40e0-96c/grownupsvet/backend:gmail-20261016-cbd1abb1e81a` |
| Digest desplegado | `sha256:1f7d51ef60246bdb80c2c8b8c1da07929c238a04e81d5401262f56b797017932` |
| Fuente construida | Allowlist de 236 archivos; SHA256 del manifiesto canónico de fuentes `cbd1abb1e81aa529d1cb35c2b95d60caa19377d2dcc81526806f257b2cb9f0fe` |
| Revisión | `grownupsvet-backend-00004-mzr`, `Ready=True`, 100 % del tráfico |
| SMTP | `smtp.gmail.com:587`, STARTTLS obligatorio, cuenta Gmail autorizada como usuario/remitente |
| Secretos SMTP | Versiones numéricas `2` de los secretos existentes `grownupsvet-mail-username` y `grownupsvet-mail-password`; valores fuera del repositorio |
| Configuración conservada | Otras variables preservadas; sin nuevos permisos IAM; 1 vCPU, 2 GiB, mínimo 1, máximo 1, concurrencia 8 |
| HTTP público | Liveness y readiness `200 UP`; OpenAPI `200`, contrato `0.9.0`, 41 rutas y descripción compatible con Gmail; mascotas sin JWT `401` |

La contraseña se transfirió cifrada y se descifró únicamente en memoria para crear la versión de Secret Manager mediante stdin. Credenciales SMTP locales protegidas con DPAPI y credenciales/contraseña del propietario de prueba se conservan fuera de Git. No se incluyen direcciones reales de cuentas Google, contraseñas, códigos de recuperación ni tokens.

Evidencia visual del estado de Cloud Run: [captura de revisión lista](../../output/gmail-2026-10-16/cloud-run-gmail-ready.jpg). El archivo se generó durante la verificación fuera del repositorio backend; su disponibilidad en un clon depende de que se entregue junto con estos documentos.

## Recuperación enviada desde la aplicación en nube

Se registró mediante la API un propietario de prueba autorizado, con nombre `Prueba Correo GrownupsVet` y demás datos ficticios; la respuesta fue `201`. Su dirección corresponde a la cuenta Gmail autorizada y no se publica. La solicitud de recuperación respondió `202` el **16/10/2026 a las 12:12:08 de Bogotá** (`2026-10-16T17:12:08.2948192Z`). El usuario confirmó que este segundo mensaje, enviado por la aplicación en nube, llegó a Recibidos.

| Metadato de la tarea de recuperación | Resultado |
|---|---|
| Estado | `SENT` |
| Intentos | `1` |
| Creación UTC | `2026-10-16T17:12:08.048003Z` |
| Aceptación SMTP UTC | `2026-10-16T17:12:10.851737Z` |
| Horas en Bogotá | `12:12:08.048` → `12:12:10.851` |
| Último código de error | `null` |
| Recepción | Usuario confirmó llegada a Recibidos |

La consulta posterior leyó únicamente metadatos, mediante conexión TLS `verify-full` y transacción de solo lectura terminada con rollback. No se leyeron OTP, payloads cifrados ni tokens. No se verificó el código ni se restableció la contraseña: esas acciones no fueron solicitadas.

## Alcance y límites

La autenticación, la revisión lista, la aceptación SMTP y la recepción confirmada acreditan esta configuración y este recorrido de recuperación. No prueban todos los tipos de avisos, recordatorios con reloj real, integración de portales, carga, lectura del mensaje o finalización del restablecimiento. Los 41 ejemplos HTTP existentes fueron validados contra el esquema; no equivalen a 41 recorridos nuevos ejecutados. El empaquetado omitió tests y se informa separado de las 20 pruebas del validador y la prueba de generación OpenAPI.

La evidencia histórica de [Cloud Run/Render del 15/10/2026](verificacion-despliegue-cloud-run-render-2026-10-15.md) continúa correspondiendo a Sandbox. No se modificó Jira ni se realizó commit/push durante esta verificación.

Referencias oficiales: [SMTP Gmail](https://developers.google.com/workspace/gmail/imap/imap-smtp), [contraseñas de aplicación](https://support.google.com/accounts/answer/185833?hl=es).
