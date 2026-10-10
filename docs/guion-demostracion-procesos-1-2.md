# Guion breve de demostración: procesos 1 y 2

**Backend 0.9.0, desde Swagger.** Este documento prepara un recorrido representativo con datos ficticios; los pasos y sus resultados esperados todavía deben ejecutarse durante la demostración. No acredita pantallas frontend, capturas de Mailtrap ni despliegue en Google Cloud. El despliegue está aplazado.

## Preparación antes de exponer

- Abrir `/swagger-ui/index.html` en la instancia local correcta y comprobar `info.version=0.9.0` en `/v3/api-docs`. Si el puerto habitual está ocupado, usar otro puerto configurado; no detener aplicaciones ajenas. El [README](../README.md) y la [guía Mailtrap](verificacion-mailtrap.md) explican arranque, JWT y configuración.
- Tener un **ADMINISTRATOR activo**, un veterinario activo y al menos un turno publicado para mañana o después, dentro de 60 días, alineado a `:00` o `:30` en `America/Bogota`. Preparar esos datos mediante las operaciones de [personal](personal-e-invitaciones.md) y [disponibilidad](disponibilidad-veterinaria.md), sin inventar credenciales predeterminadas.
- Usar dos propietarios ficticios, **A y B**, con correos diferentes. B puede quedar registrado antes de la exposición; A se registra en el primer paso. Cambiar el sufijo del correo en cada ensayo para evitar duplicados.
- Conservar temporalmente los identificadores de mascota, turno y cita y las versiones devueltas. En Authorize pegar solamente `accessToken`; cambiar de token cada vez que cambie el actor. No mostrar tokens, contraseñas ni OTP en capturas.

Frase de apertura: «Vamos a mostrar cómo el backend registra a un propietario y su mascota, protege sus datos y administra una cita con historial y avisos. Las acciones se realizan desde Swagger; la integración de los portales se demuestra por separado».

## Proceso 1: usuarios y mascotas

Registrar A con `POST /api/v1/auth/registrations`, sin autorización. Ejemplo ficticio válido:

```json
{
  "email": "guion-a-01@example.com",
  "fullName": "María Gómez",
  "dateOfBirth": "1960-05-20",
  "phoneNumber": "+573001234567",
  "password": "Mis mascotas pasean tranquilas por el parque"
}
```

| Paso de demostración | Actor / operación | Resultado esperado y explicación |
|---|---|---|
| Registro | Público; petición anterior | `201`, UUID/correo. El servidor asigna `OWNER`; no enviar `role` ni `active` |
| Acceso y perfil | A: `POST /api/v1/auth/sessions` con `email` y `password`; Authorize; `GET /api/v1/users/me` | `200`; mostrar `user.role` en el login y los datos seguros del perfil, sin hash |
| Modificar perfil | A: `PATCH /api/v1/users/me` con `{"phoneNumber":"+573001234568"}` | `200`; únicamente cambia el teléfono. Correo, nombre y nacimiento no se editan por esta operación |
| Registrar mascota | A: `POST /api/v1/pets` con `{"name":"Luna","species":"DOG"}`; guardar `petId`; `GET /api/v1/pets/{petId}` | `201` y luego `200`; la mascota pertenece a A por su identidad autenticada, sin enviar `ownerId` |
| Comprobar propiedad | Iniciar sesión como B y consultar el mismo `GET /api/v1/pets/{petId}` | `404 PET_NOT_FOUND`; no revela los datos de A |
| Archivo reversible | Volver a A; `PATCH /api/v1/pets/{petId}` con `{"active":false}` y después `{"active":true}` | `200`; conserva UUID y datos. Dejarla **activa** para el proceso 2 |
| Personal preparado | Administrador: `GET /api/v1/veterinarians` | `200`; identificar el veterinario activo que dispone del turno. Las cuentas profesionales se gestionan con permisos específicos, no con registro público |

Frase de transición: «La autenticación identifica al propietario y la autorización comprueba qué recurso puede consultar. Archivar conserva la mascota y su historia; para solicitar una cita debe estar activa».

## Proceso 2: solicitud y cambio administrativo de cita

Como A, consultar `GET /api/v1/availability-slots` con `from` y `to` en `YYYY-MM-DD` (rango máximo de 31 días). Elegir un turno libre y guardar su `id` y `version`. Crear la cita mediante `POST /api/v1/appointments` con los cinco campos reales de `CreateAppointmentRequestDTO`:

| Campo | Valor para el ensayo |
|---|---|
| `clientRequestId` | UUID nuevo; puede generarse con `[guid]::NewGuid().ToString()` |
| `petId` | UUID de Luna, propiedad de A |
| `availabilitySlotId` | UUID del turno elegido |
| `expectedAvailabilitySlotVersion` | Versión numérica observada del turno |
| `reason` | Por ejemplo, `Consulta general de prueba` |

| Paso de demostración | Actor / operación | Resultado esperado y evidencia |
|---|---|---|
| Solicitud | A: POST anterior; guardar `appointmentId` y `version` | `201`, estado `REQUESTED`; ocupa el turno. Repetir **el mismo cuerpo y clave** devuelve `200` con el mismo UUID, sin otra solicitud |
| Restricción por rol | A: `PATCH /api/v1/appointments/{appointmentId}/status` con `status="CONFIRMED"` y la versión actual | `403`; ser propietario no permite confirmar |
| Confirmación | Administrador: mismo PATCH con `status="CONFIRMED"` y `expectedVersion` actual | `200`, estado `CONFIRMED` y versión nueva; hacerlo antes del corte y del inicio original |
| Estado de avisos | A o administrador: `GET /api/v1/appointments/{appointmentId}/notifications?page=0&size=20` | Mostrar `OWNER_CONFIRMED`, estado y fechas reales. Si se confirmó con al menos 24 horas de anticipación, mostrar también el recordatorio `OWNER_REMINDER`, programado para inicio menos 24 horas |
| Conflicto de versión | Administrador: intentar cancelar con `status="CANCELLED"`, motivo y **versión anterior a la confirmación** | `409 CONCURRENT_UPDATE`; la cita conserva su estado. Explicar que una pantalla con datos antiguos no debe sobrescribir el cambio |
| Cancelación válida | Administrador: leer `GET /api/v1/appointments/{appointmentId}` y repetir cancelación con su versión actual y motivo `Cancelación administrativa de prueba` | `200`, `CANCELLED`, versión nueva; hacerlo antes del inicio |
| Historia conservada | A o administrador: `GET /api/v1/appointments/{appointmentId}/events?page=0&size=20`; volver a consultar notifications | Mostrar solicitud, confirmación y evento humano `CANCELLED`, actor, estados y motivo; mostrar `OWNER_CANCELLED`. El UUID se conserva y el turno vuelve a estar libre si continúa siendo solicitable |
| Propiedad de cita | B: `GET /api/v1/appointments/{appointmentId}` | `404`; B no consulta la cita de A |

Las versiones son números JSON. `SUPER_ADMIN` y `VETERINARIAN` no heredan la gestión administrativa de citas. No ejecutar una transición con datos de ejemplo sin sustituir UUID y versiones por las respuestas del ensayo.

Si se pretende capturar ambos correos, esperar la captura de confirmación antes de cancelar. Un cambio puede dejar un aviso anterior `SUPERSEDED` si todavía no se había enviado. **`SENT` acredita aceptación SMTP, no lectura ni recepción en buzón**: enseñar la captura del Sandbox es una evidencia aparte.

## Recuperación y Mailtrap: bloque condicionado a la captura manual

El Sandbox `grownupsvet` está accesible con sesión autenticada. La [verificación inicial](verificacion-mailtrap-2026-10-10.md) registra seis avisos de citas en lista/encabezados; después se comprobaron el [recordatorio con reloj real](verificacion-recordatorio-real-2026-10-10.md) y el [transporte de recuperación V18](verificacion-recuperacion-smtp-2026-10-10.md). La lectura visual del contenido y el restablecimiento completo siguen pendientes. Para completar estos pasos, seguir [verificacion-mailtrap.md](verificacion-mailtrap.md):

1. Quitar Authorize y usar la cuenta activa A: `POST /api/v1/auth/password-recoveries` con `email`; esperar `202` y abrir el mensaje en el Sandbox.
2. `POST /api/v1/auth/password-recoveries/verifications` con `email` y `code` textual de seis dígitos; esperar `200` y conservar temporalmente `resetToken`.
3. `POST /api/v1/auth/password-resets` con `resetToken`, `newPassword`, `confirmNewPassword`; esperar `204`. Mostrar que la contraseña nueva funciona y la anterior/JWT anteriores dejan de aceptarse, sin exponer secretos.

Si no existe captura, mostrar contrato y evidencia automatizada y decir explícitamente «captura manual pendiente». No sustituirla por una pantalla inventada. El recordatorio real debe comprobarse en su momento **inicio menos 24 horas**, conservando `PT24H` y el reloj real; no forma parte de una demostración inmediata de pocos minutos.

Al finalizar, iniciar sesión otra vez como A (con la contraseña nueva si se realizó recuperación), poner ese token en Authorize y mostrar `DELETE /api/v1/auth/sessions/current`: `204`; el mismo JWT deja de permitir el perfil (`401`). Después retirar el token de Authorize.

## Resultados ya comprobados y registro del ensayo

El [informe 0.9.0](verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md) acredita **508 pruebas automatizadas satisfactorias**, contrato **59 operaciones/40 rutas** y **41 respuestas HTTP validadas**. Son resultados de aquellas ejecuciones; el correo de sus pruebas está controlado/simulado y no acredita capturas remotas, pantallas de compañeros o despliegue cloud.

Registrar al ejecutar este guion: fecha/hora, revisión usada, pasos realizados, rol, HTTP, UUID ficticios, versiones antes/después, estados/eventos de cita y capturas censuradas. Marcar cada paso como **realizado**, **fallido** o **pendiente**; no completar anticipadamente una columna de resultados obtenidos.

Frase de cierre: «La muestra comprueba el recorrido backend, los permisos, la propiedad y la trazabilidad de cambios. La captura en Sandbox, la integración de ambos portales y el despliegue tienen verificaciones y estados propios. La atención clínica está fuera de esta entrega».
