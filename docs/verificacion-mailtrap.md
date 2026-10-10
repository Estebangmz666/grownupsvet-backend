# Comprobar recuperación y avisos en Mailtrap Sandbox

Procedimiento manual para el contrato **0.9.0**. Estas instrucciones no acreditan una ejecución SMTP ni una captura ya realizada. [Mailtrap Sandbox](https://docs.mailtrap.io/email-sandbox/overview) captura mensajes de prueba; no los entrega al Gmail u otro buzón real.

## 1. Preparar una instancia actual del backend

Desde `grownupsvet-backend`, con Java 25, PostgreSQL de desarrollo y la configuración JWT descrita en el [README](../README.md):

```powershell
.\scripts\configure-mailtrap.ps1
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:APPOINTMENT_WORKDAY_START_TIME = '06:00'
$env:APPOINTMENT_REMINDER_ADVANCE = 'PT24H'
```

El script pide el usuario y la contraseña de **SMTP Integration del Sandbox**, más el remitente. Guarda la configuración en las variables del usuario de Windows y en esta terminal. Genera las claves HMAC y `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY` si faltan, conservando las existentes; **no envía un correo de prueba**. No configura la clave JWT: conserva su ruta, alias y contraseña en tu configuración local del README. Reinicia IntelliJ si ejecutarás desde allí y selecciona el perfil `dev`, jornada `06:00` y recordatorio `PT24H` en su ejecución. No compartas ni fotografíes las credenciales o claves.

Ejecuta tu instancia del backend con el código actual. Si 8080 está ocupado, usa otra ejecución en un puerto libre, sin cerrar procesos ajenos:

```powershell
$env:BACKEND_BASE_URL = 'http://localhost:8081'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

Abre `/swagger-ui/index.html` en esa misma instancia: por ejemplo, `http://localhost:8081/swagger-ui/index.html`. Comprueba en `/v3/api-docs` que `info.version` sea **0.9.0** y que el servidor de Swagger coincida con la instancia abierta. Una ejecución antigua no incorpora los cambios por tener archivos nuevos en disco.

En Mailtrap abre el **mismo Sandbox** del que tomaste las credenciales. Mantén su bandeja abierta y anota la hora de cada prueba. Configurar SMTP o comprobar conexión/autenticación no demuestra por sí solo que un flujo de la aplicación haya enviado su mensaje.

## 2. Recuperación: una cuenta activa y tres llamadas públicas

Usa una cuenta ficticia **activa**. Si no tienes una, `POST /api/v1/auth/registrations` crea un propietario activo. Este cuerpo contiene exclusivamente datos de ejemplo; cambia el correo si ya está registrado:

```json
{
  "email": "demo-mailtrap@example.com",
  "fullName": "María Gómez",
  "dateOfBirth": "1960-05-20",
  "phoneNumber": "+573001234567",
  "password": "Mis mascotas pasean tranquilas por el parque"
}
```

En **Authorize** de Swagger pulsa **Logout** para este recorrido: sus tres llamadas son públicas. `resetToken` se usa en el cuerpo; no es un JWT ni se pega en Authorize.

| Paso | Operación y cuerpo | Comprobación |
|---|---|---|
| Solicitar | `POST /api/v1/auth/password-recoveries`: `email` de la cuenta activa | HTTP `202`; abrir el nuevo mensaje en Mailtrap y comprobar destinatario y código de seis dígitos |
| Verificar | `POST /api/v1/auth/password-recoveries/verifications`: mismo `email` y `code` **como texto**, conservando ceros iniciales | HTTP `200`; copiar temporalmente `resetToken` de esa respuesta |
| Restablecer | `POST /api/v1/auth/password-resets`: `resetToken`, `newPassword`, `confirmNewPassword`; las dos contraseñas deben coincidir | HTTP `204`; comprobar en Mailtrap el aviso de contraseña restablecida |
| Confirmar acceso | `POST /api/v1/auth/sessions`: `email`, `password` | Contraseña nueva: `200`; anterior: `401`. El código y permiso ya usados no deben volver a aceptarse |

El código vence a los diez minutos y el permiso de restablecimiento a los cinco. El reenvío espera 60 segundos y sustituye el desafío anterior. Un correo inexistente, una cuenta pendiente de activación o una desactivada también reciben `202`, **sin correo**: utiliza una cuenta activa para probar entrega. No publiques OTP, permisos ni JWT en capturas.

## 3. Citas: roles, datos y mensajes inmediatos

Necesitas un propietario activo con mascota propia activa, un **ADMINISTRATOR** activo y un veterinario activo con turnos publicados. Un **SUPER_ADMIN** o un veterinario no pueden confirmar ni cambiar citas. Si falta personal, sigue [Personal e invitaciones](personal-e-invitaciones.md); no hay credenciales administrativas predeterminadas.

Cada vez que cambies de actor, inicia sesión con `POST /api/v1/auth/sessions` y pega **solo `accessToken`** en Authorize; Swagger añade Bearer. Usa una cita nueva para escenarios terminales distintos y espera la captura de cada aviso antes de aplicar el siguiente cambio: un cambio posterior puede superar mensajes todavía pendientes.

| Preparación / acción | Actor y operación | Datos reales que debes utilizar |
|---|---|---|
| Crear mascota, si falta | Propietario: `POST /api/v1/pets` | Por ejemplo `name="Luna"`, `species="DOG"`; conservar su `id` |
| Publicar turno, si falta | Administrador: `POST /api/v1/veterinarians/{veterinarianId}/availability-slots` | `startsAt` textual ISO con offset; mañana o después, dentro de 60 días, minutos `:00` o `:30`; duración automática de 30 minutos |
| Elegir disponibilidad | Propietario: `GET /api/v1/availability-slots` | `from` y `to` en `YYYY-MM-DD`, rango de hasta 31 días; conservar `items[].id` y `version` del turno libre |
| Solicitar cita | Propietario: `POST /api/v1/appointments` | `clientRequestId` UUID nuevo, `petId`, `availabilitySlotId`, `expectedAvailabilitySlotVersion` observado y `reason`; guardar `id` y `version` de respuesta |
| Confirmar | Administrador: `PATCH /api/v1/appointments/{appointmentId}/status` | `status="CONFIRMED"`, `expectedVersion` actual; antes del inicio y del corte de la pendiente |
| Rechazar una pendiente | Administrador: mismo PATCH, sobre otra cita `REQUESTED` | `status="REJECTED"`, `expectedVersion`, `reason` no vacío |
| Cancelar | Administrador: mismo PATCH, sobre una pendiente/confirmada todavía futura | `status="CANCELLED"`, `expectedVersion`, `reason` no vacío |
| Reprogramar | Administrador: `POST /api/v1/appointments/{appointmentId}/reschedulings` | Los ocho campos de `RescheduleAppointmentRequestDTO`: UUID `clientRequestId`, turno destino, versiones de turno/cita, `reason`, `agreementChannel` (`PHONE`, `WHATSAPP`, `OTHER`), `agreementContactedAt` con offset y `agreementNote`; registrar el acuerdo de prueba. Destino libre desde mañana; participantes activos |

Para obtener un UUID puedes ejecutar `[guid]::NewGuid().ToString()`. Para cada operación nueva usa una clave nueva; para comprobar un replay conserva clave, cuerpo y actor. Consulta `GET /api/v1/appointments/{appointmentId}` antes de editar para utilizar la versión actual. Las versiones son números JSON, no cadenas.

Después de confirmar, rechazar, cancelar o reprogramar, consulta como propietario de esa cita o administrador:

`GET /api/v1/appointments/{appointmentId}/notifications?page=0&size=20`

Busca el tipo correspondiente: `OWNER_CONFIRMED`, `OWNER_REJECTED`, `OWNER_CANCELLED` u `OWNER_RESCHEDULED`. La creación de una solicitud por sí sola no promete un correo P12. En Mailtrap comprueba destinatario ficticio, mascota, profesional y fecha/hora coherentes con la cita.

| Estado de aviso | Interpretación |
|---|---|
| `PENDING` | Espera su momento o próximo intento; un recordatorio futuro aún no debe aparecer en la bandeja |
| `SENDING` | Un trabajador tiene una adquisición vigente |
| `SENT` | SMTP aceptó el envío; **todavía debes comprobar la captura en Mailtrap**. No significa lectura ni entrega a un buzón real |
| `FAILED` | Se agotó la entrega; revisar `errorCode` y configuración, sin publicar diagnósticos privados |
| `SUPERSEDED` | Otro cambio o pérdida de elegibilidad invalidó ese mensaje pendiente |

El sondeo de citas tiene intervalo de 15 segundos; el primer despacho también se intenta después del commit. Vuelve a consultar si el mensaje está pendiente. No registres una captura como satisfactoria hasta verla en el Sandbox.

## 4. Recordatorio real y casos adicionales

- **24 horas reales:** reserva y confirma una cita cuyo inicio esté a más de 24 horas. Conserva esa programación y el backend ejecutándose. Consulta `OWNER_REMINDER`: `scheduledAt` debe ser inicio menos 24 horas, comparando instantes, con las horas presentadas en `America/Bogota`. Al llegar ese momento comprueba `SENT` y el mensaje nuevo en Mailtrap. No cambiar `PT24H`, el reloj del sistema ni la hora de producción para adelantar la prueba.
- **Confirmación tardía:** confirma otra cita a menos de 24 horas de su inicio, todavía antes de su corte/inicio. Debe generar confirmación y omitir el recordatorio atrasado.
- **Corte:** deja una solicitud `REQUESTED` para el día siguiente sin confirmar. Con jornada `06:00`, comprueba la cancelación automática de esa fecha y `OWNER_CANCELLED` cuando alcance el corte; no manipules el reloj público.
- **Propietario desactivado:** opcionalmente, usa una cita ya confirmada, desactiva al propietario con `DELETE /api/v1/users/me` usando su sesión y después cancela esa cita futura como administrador. El aviso del cambio sigue dirigido a su correo; consulta notifications como administrador porque la sesión del propietario ya no es válida. Recuperación no envía códigos a esa cuenta desactivada.
- **Inasistencia:** `NO_SHOW` solo se registra por administración desde el fin de una confirmada y no genera correo P12.

## 5. Qué conservar como evidencia

Anota fecha/hora, revisión usada, actor/rol, UUID ficticio de cita, acción, HTTP, tipo/estado/fechas del aviso y captura censurada del mensaje en el Sandbox. Para recuperación, registra los tres HTTP y la comprobación de contraseña nueva/anterior sin guardar secretos.

Si no aparece un correo, confirma primero instancia **0.9.0**, cuenta/rol, Sandbox y credenciales seleccionados. Un `401` pide revisar sesión/Authorize; `403`, el rol; `409`, versión, estado, corte o capacidad del turno; `503` en recuperación, disponibilidad/configuración o capacidad de su cola. Un `PENDING` con fecha futura es normal; `SMTP_DELIVERY_FAILED` o `EMAIL_CONFIGURATION_MISSING` requieren revisar la configuración local y el siguiente intento. Las pruebas automatizadas con SMTP simulado no sustituyen estas capturas manuales.
