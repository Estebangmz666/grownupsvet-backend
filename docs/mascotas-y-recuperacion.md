# Mascotas y recuperación de acceso — incremento 0.4.0

Decisiones del 10 de septiembre de 2026. Este incremento adelanta el trabajo previsto del 14 al 15 de septiembre en [SCRUM-76](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-76). La implementación de mascotas corresponde a [SCRUM-27](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-27), dentro de SCRUM-7; la recuperación corresponde a [SCRUM-30](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-30), dentro de SCRUM-9.

La secuencia acordada es implementar, probar y generar OpenAPI desde Java. El contrato publicado y la revisión por los tres integrantes siguen siendo criterios propios de SCRUM-76. El código backend no acredita por sí solo integración de los frontends ni cierre de las historias completas.

## Mascotas

Esteban confirmó `OWNER` 1:N `PET`, atención solo de perros y gatos y archivo reversible. Una cuenta puede comenzar sin mascotas. Cada mascota tiene exactamente un propietario y no puede transferirse mediante estas operaciones. `pets.owner_id` es una clave foránea obligatoria hacia `owner_profiles.user_id`, sin restricción única: admite varias mascotas del mismo dueño. La identidad del propietario proviene del JWT validado; nunca de un `ownerId` enviado en el formulario.

| Campo | Tipo JSON | Regla |
|---|---|---|
| `id` | `string` UUID | Generado por el servidor. |
| `name` | `string` | Obligatorio; hasta 100 caracteres Unicode. Se retiran espacios exteriores. No es único. |
| `species` | `string` enum | Obligatorio: `DOG` o `CAT`. |
| `breed` | `string` o `null` | Opcional; hasta 100 caracteres. Puede escribirse «Mestizo»; desconocida se representa con `null`. |
| `sex` | `string` enum o `null` | Opcional: `MALE`, `FEMALE`, `UNKNOWN`. |
| `dateOfBirth` | `string` fecha o `null` | Fecha `YYYY-MM-DD` no futura. Desconocida se representa con `null`, sin inventarla. |
| `dateOfBirthEstimated` | `boolean` | `true` identifica una fecha estimada; exige `dateOfBirth`. Por defecto `false`. |
| `active` | `boolean` | Alta activa por el servidor. Se puede archivar y reactivar mediante actualización. |
| `createdAt`, `updatedAt` | `string` date-time | Instantes asignados por el servidor. |

Ejemplo ficticio de registro:

```json
{
  "name": "Luna",
  "species": "DOG",
  "breed": "Mestizo",
  "sex": "FEMALE",
  "dateOfBirth": "2021-06-01",
  "dateOfBirthEstimated": true
}
```

| Método y ruta | Resultado |
|---|---|
| `POST /api/v1/pets` | `201`, mascota creada y `Location`. |
| `GET /api/v1/pets?page=0&size=20` | `200`, página de mascotas propias; tamaño máximo 100. El filtro opcional `active` permite consultar activas o archivadas. |
| `GET /api/v1/pets/{petId}` | `200`, mascota propia, incluso archivada. |
| `PATCH /api/v1/pets/{petId}` | `200`, actualiza los campos enviados y conserva los omitidos. |

Las operaciones exigen `OWNER` y permisos `PET_CREATE_SELF`, `PET_READ_SELF` o `PET_UPDATE_SELF`, además de comprobar la propiedad del recurso. Un identificador ajeno o inexistente produce la misma respuesta `404 PET_NOT_FOUND`. Los roles profesionales no reciben estos permisos de autogestión; su eventual acceso clínico es otro alcance.

En PATCH, `null` elimina únicamente datos opcionales; nunca sustituye un nombre, especie o booleano obligatorio. La combinación final de fecha e indicador estimado debe ser coherente. Los valores JSON mantienen sus tipos: `false` es booleano, `"false"` no lo es; los enums se envían como texto. No hay eliminación física de mascotas.

`active=false` significa «archivada», no «fallecida». La interfaz puede mostrar «Archivar mascota» y «Volver a activar». La fila y su identidad se conservan. Solicitar una cita exige mascota activa y propia; confirmar y reprogramar comprueban también su elegibilidad, según [Citas y agenda](citas-y-agenda.md). Archivar no cancela automáticamente citas anteriores ni modifica información clínica. Administración puede cancelar una cita antes de su inicio o registrar su inasistencia al finalizar, aunque la mascota esté archivada.

## Recuperación

Aplica a las cuentas activas de los cuatro roles, incluido `SUPER_ADMIN`. Las cuentas pendientes de activación o desactivadas no reciben acceso ni se reactivan por recuperar una contraseña. El correo usa el mismo formato y normalización que el acceso existente.

| Paso | Método y ruta | Entrada | Resultado |
|---|---|---|---|
| Solicitar código | `POST /api/v1/auth/password-recoveries` | `email` | `202`, mensaje genérico. |
| Verificar código | `POST /api/v1/auth/password-recoveries/verifications` | `email`, `code` | `200`, `resetToken` y `expiresIn`. |
| Establecer contraseña | `POST /api/v1/auth/password-resets` | `resetToken`, `newPassword`, `confirmNewPassword` | `204`, volver al login habitual. |

Las tres rutas se invocan sin cabecera `Authorization`; un interceptor no debe adjuntar un JWT vencido a este flujo público. El restablecimiento se autoriza exclusivamente con `resetToken`.

El código es texto de seis dígitos, por ejemplo `"004291"`, generado con `SecureRandom`. Vence en diez minutos y admite cinco intentos fallidos. La verificación lo consume y entrega un permiso opaco aleatorio de 32 bytes que dura cinco minutos y solo permite restablecer la contraseña una vez. Cambiar de pantalla no autoriza ninguna operación por sí mismo. El frontend conserva ese permiso temporalmente en memoria, lo transmite en el cuerpo JSON y lo descarta al terminar o vencer; no lo introduce en URL ni lo utiliza como Bearer de la aplicación.

La solicitud ofrece la misma respuesta para correos registrados, inexistentes e inactivos. El correo se procesa fuera de la respuesta HTTP; `202` confirma aceptación del trabajo, no recepción del mensaje. Los códigos y permisos nunca aparecen en logs. PostgreSQL guarda un HMAC del código, vinculado a su desafío, y solo el hash SHA-256 del permiso aleatorio. La clave HMAC se configura fuera del repositorio y es independiente de la clave JWT. El reenvío sustituye el desafío anterior e invalida el permiso de restablecimiento que pudiera existir.

Límites iniciales del proyecto, no valores impuestos por una norma:

| Control | Límite |
|---|---|
| Reenvío por correo | Espera de 60 segundos; máximo 5 solicitudes aceptadas por hora. |
| Verificación por correo | Máximo 10 por hora, acumuladas entre reenvíos. |
| Solicitudes por IP | Máximo 30 por hora. |
| Verificaciones por IP | Máximo 100 por hora. |
| Restablecimientos por IP | Máximo 100 por hora. |

Los contadores se conservan en PostgreSQL, incluso cuando la verificación falla. El bloqueo afecta a los intentos de recuperación, no desactiva la cuenta ni impide usar su contraseña vigente. El backend toma la IP de la conexión y no acepta una cabecera `X-Forwarded-For` arbitraria como identidad confiable. Si se despliega detrás de un proxy, su configuración de confianza debe resolverse antes de usar el sistema en ese entorno.

La contraseña nueva y la confirmación deben coincidir. Se reutiliza la política aprobada: 15–128 puntos de código Unicode, espacios preservados, sin composición obligatoria y rechazo de la lista local SecLists de 10.000 contraseñas comunes. Esta lista es limitada, no una garantía de ausencia en todas las filtraciones.

Argon2id no descifra ni modifica el hash viejo: `PasswordEncoder.encode(newPassword)` genera un hash con una sal nueva y reemplaza el valor almacenado. El login posterior sigue usando `matches`. El cambio de hash, el consumo del permiso y el incremento de `authenticationVersion` se realizan en una sola transacción. Cada petición protegida compara la versión del JWT con la cuenta actual, de modo que todos los tokens anteriores dejan de ser válidos. La revocación individual existente se mantiene para el cierre de una sola sesión.

**Compatibilidad:** los JWT anteriores a este incremento no contienen `authenticationVersion` y deben sustituirse mediante un nuevo login. El campo de bloqueo optimista de `User` impide que una actualización basada en datos antiguos sobrescriba el cambio de contraseña; una colisión devuelve `409 CONCURRENT_UPDATE`.

Los errores mantienen `application/problem+json`, `errorCode` y `fieldErrors`. El código incorrecto, vencido o consumido produce `400 INVALID_PASSWORD_RECOVERY_CODE`; un permiso inválido produce `400 INVALID_PASSWORD_RESET_TOKEN`; el exceso de intentos devuelve `429 PASSWORD_RECOVERY_RATE_LIMITED` con `Retry-After`. Si la recuperación está deshabilitada o no puede aceptar el trabajo, devuelve `503 PASSWORD_RECOVERY_UNAVAILABLE`, sin fingir envío.

PATCH es válido para modificaciones parciales y no es intrínsecamente inseguro. Aquí se utilizan POST para crear una solicitud, una verificación y un restablecimiento, dentro del módulo de autenticación. La protección procede del permiso restringido, el consumo atómico, los límites y HTTPS en despliegue.

## Interfaz accesible y límites operativos

El formulario de código debe permitir pegar los seis dígitos completos, conservar ceros iniciales y ofrecer etiquetas visibles, teclado y reenvío con espera comprensible. Se recomienda un único campo de texto con teclado numérico, no un campo numérico que elimine ceros. La nueva contraseña y su confirmación deben permitir mostrar/ocultar, pegar y usar gestores de contraseñas.

Desde el incremento 0.9.0, V18 guarda el correo en una bandeja de salida PostgreSQL dentro de la transacción de desafío/restablecimiento. El OTP está cifrado con AES-GCM y una clave independiente; la validación del código sigue usando su HMAC. Después del commit, trabajadores acotados adquieren tareas con un token y envían SMTP fuera de la transacción; el sondeo recupera trabajo tras reinicio. La primera falla SMTP conserva el desafío para reintentar. El agotamiento o vencimiento termina solamente la intención correspondiente, sin borrar un desafío posterior; al terminar se purgan ciphertext y nonce.

La admisión de capacidad se comprueba antes de consultar si existe la cuenta, manteniendo las respuestas públicas indistinguibles. Un aviso posterior al restablecimiento no revierte la contraseña: si no puede admitirse por capacidad, se registra `FAILED/DELIVERY_CAPACITY_EXHAUSTED`; los fallos SMTP normales sí se reintentan. La cola durable y sus leases no garantizan entrega exactamente una vez ante respuestas SMTP inciertas.

Las respuestas públicas no distinguen la existencia de la cuenta y SMTP queda fuera de su recorrido. Esto reduce diferencias evidentes de tiempo, pero no demuestra respuestas de tiempo constante ni elimina todos los canales estadísticos de enumeración.

## Conectar Mailtrap en desarrollo

Mailtrap Email Sandbox captura los mensajes en una bandeja de pruebas; no los entrega al Gmail u otro buzón real del destinatario. La entrega real de producción requiere configurar un servicio de envío para ese entorno.

1. Crear una cuenta y un **Email Sandbox** en Mailtrap; abrir su pestaña **Integration** y seleccionar SMTP.
2. Tener a mano usuario y contraseña SMTP del Sandbox. No enviarlos por chat ni escribirlos en archivos del repositorio.
3. Desde el backend, ejecutar `./scripts/configure-mailtrap.ps1`. Solicita las credenciales localmente, prepara el remitente y genera claves independientes HMAC/cifrado si faltan, conservando las existentes. Guarda variables del usuario de Windows; no envía mensajes ni imprime claves.
4. Reiniciar IntelliJ o abrir otra terminal y arrancar el backend con Java 25 y PostgreSQL habituales.
5. Usar una cuenta ficticia activa, solicitar recuperación, abrir el mensaje capturado en Mailtrap, verificar el código y establecer una contraseña válida. Confirmar que la contraseña antigua y los tokens anteriores ya no funcionan, y que el código y permiso usados se rechazan.

Variables: `PASSWORD_RECOVERY_ENABLED`, `PASSWORD_RECOVERY_HMAC_SECRET`, **`PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY`**, `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` y `MAIL_PASSWORD`. La clave de correo es Base64 de exactamente 32 bytes y distinta del HMAC; se requiere al habilitar recuperación. El perfil dev usa SMTP con STARTTLS obligatorio y tiempos de espera de cinco segundos. Sin configuración, la recuperación permanece deshabilitada. Mantener ambas claves estables entre reinicios/revisiones; cambiar HMAC invalida desafíos/cuotas y cambiar cifrado sin terminar pendientes impide descifrarlos.

El Sandbox de Mailtrap se configuró posteriormente en desarrollo y Esteban confirmó las pruebas manuales de recuperación, incluida la contraseña nueva y la anterior. La integración de pantallas y la revisión con los otros dos integrantes siguen pendientes por disponibilidad del equipo. Las plantillas Thymeleaf se aplazaron hasta disponer del diseño visual del frontend.

## Fuentes técnicas

- [OWASP: recuperación de contraseña](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html): códigos aleatorios, uso único, vencimiento, respuestas consistentes, sesión restringida y controles de abuso.
- [OWASP: almacenamiento de contraseñas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): Argon2id y sal.
- [Spring Security: PasswordEncoder](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
- [Spring Boot: envío de correo](https://docs.spring.io/spring-boot/reference/io/email.html).
- [Mailtrap: Email Sandbox](https://docs.mailtrap.io/email-sandbox/overview).
- [RFC 5789, sección 5](https://www.rfc-editor.org/rfc/rfc5789#section-5): seguridad de PATCH.

## Verificación del incremento 0.4.0

El 10 de septiembre de 2026, `mvnw.cmd verify` completó **264 pruebas**, sin fallos, errores ni omisiones. Incluye 53 casos HTTP de mascotas, 17 de recuperación, la recuperación deshabilitada, configuración/secretos y cinco casos de correo SMTP real exclusivamente en loopback. Las pruebas ejercitan PostgreSQL local, aislamiento entre propietarios, PATCH parcial/concurrente, cuotas, vencimiento, uso único y rechazo de JWT anteriores al restablecimiento.

El OpenAPI 3.1.0 generado, versión 0.4.0, contiene **16 operaciones** y pasó `openapi-spec-validator`; **13 respuestas HTTP** pasaron la comprobación independiente de JSON Schema. Se comprobó además que no contiene rutas exclusivas de pruebas y que sus booleanos, enteros y enums anulables coinciden con el contrato. El snapshot se copió al repositorio solo después de estas verificaciones.

Las pruebas automatizadas no incluyen Mailtrap remoto, pantallas frontend, despliegue ni revisión de los otros integrantes. Las migraciones V5/V6 se verificaron automáticamente en `grownupsvet_test`.

Posteriormente, el 10 de septiembre de 2026, se comprobó conexión STARTTLS y autenticación SMTP con el Sandbox de Mailtrap. El desarrollador reportó el arranque local y Swagger funcionando, un `204` de `POST /api/v1/auth/password-resets` (captura compartida) y `authentication_version = 1` mediante consulta SQL. En la conversación posterior Esteban confirmó que los casos de contraseña nueva y anterior pasaron. Esto documenta una verificación manual reportada por el desarrollador; no sustituye la revisión del contrato por los tres integrantes. No se incorporan credenciales, códigos ni tokens reales a esta evidencia.
