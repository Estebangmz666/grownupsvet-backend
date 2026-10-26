# Preparar datos ficticios para los procesos 1 y 2

`scripts/create-demo-data.ps1` es un cliente de la **API local 0.9.0/0.10.0**. Crea datos nuevos y conserva una cita confirmada para el [guion de demostración](guion-demostracion-procesos-1-2.md). No ejecuta SQL, bootstrap, creación/activación de personal, cambios de permisos o despliegue; usa cuentas profesionales existentes.

El script no se ha ejecutado sobre tu base `grownupsvet_dev` para preparar esta documentación. La prueba HTTP automatizada usa PostgreSQL aislado y correo ausente/simulado. **El helper no llama SMTP directamente, pero confirmar, cambiar y cancelar por la API puede activar los trabajadores del backend:** si tu instancia tiene Mailtrap configurado, puede enviar mensajes a su Sandbox.

**Evidencia local del 10 de octubre de 2026:** `DemoDataScriptHttpIntegrationTests` pasó 2 pruebas, sin fallos, errores ni omisiones, ejecutando el script real contra HTTP y PostgreSQL aislados. Comprobó los rechazos `401`/`403`/`404`/`409`, A `CANCELLED` versión 3 con cuatro eventos, B `CONFIRMED` versión 1, avisos consultables sin intentos SMTP y ausencia de contraseñas/tokens en salida y reporte. También rechazó un token de veterinario antes de crear datos. El esquema de prueba se eliminó; esto no acredita datos creados en tu instancia ni captura Mailtrap.

## Prerrequisitos y alcance

- Backend actual con Swagger/OpenAPI 0.9.0 o 0.10.0, configuración de PostgreSQL/JWT válida y zona de negocio `America/Bogota`. Comprobar instancia/puerto mediante la [guía Mailtrap](verificacion-mailtrap.md).
- Cuenta `ADMINISTRATOR` activa: obtener su `accessToken` mediante `POST /api/v1/auth/sessions`. `SUPER_ADMIN`, `VETERINARIAN` y `OWNER` no sustituyen ese permiso. No hay credenciales predeterminadas en este helper.
- UUID de un veterinario ya **activo**, consultable por el administrador en `GET /api/v1/veterinarians/{id}`. Su perfil y activación deben estar preparados con los flujos de [personal](personal-e-invitaciones.md).
- Dos horarios libres consecutivos de ese veterinario. Indicar el inicio del primero con offset explícito; el segundo se publica 30 minutos después. Ambos deben caer desde mañana en Colombia y no superar 60 días desde la ejecución, con minutos `:00`/`:30`, sin segundos/fracciones. Conviene elegir dos días después para evitar el cambio de fecha durante el recorrido.
- Contraseña **de demostración**, que cumpla la política de registro (15–128 puntos de código Unicode y rechazo de contraseñas comunes). Se usa para los dos propietarios ficticios nuevos; no reutilizar contraseñas reales.

La URL admite exclusivamente un origen `http`/`https` de loopback: `localhost`, IPv4 de loopback o `::1`, con puerto válido. No acepta credenciales, query, fragmento ni prefijo de ruta; por ejemplo, usar `http://localhost:8081`, sin añadir `/api/v1`. No sigue redirecciones ni utiliza un proxy; HTTPS conserva la validación normal del certificado.

## Ejecución manual autorizada

Desde `grownupsvet-backend`, con una instancia **de demostración** identificada y turnos libres elegidos:

```powershell
$demoAdministratorToken = Read-Host 'Solo accessToken del administrador, sin Bearer' -AsSecureString
$demoOwnerPassword = Read-Host 'Contrasena para los propietarios ficticios' -AsSecureString
$demoVeterinarianId = [guid](Read-Host 'UUID del veterinario activo')
$demoSlotStart = [DateTimeOffset]::Parse((Read-Host 'Inicio futuro ISO con offset, por ejemplo YYYY-MM-DDT10:00:00-05:00'))

.\scripts\create-demo-data.ps1 -ApiBaseUrl 'http://localhost:8081' `
    -AdministratorAccessToken $demoAdministratorToken `
    -VeterinarianId $demoVeterinarianId `
    -FirstSlotStartsAt $demoSlotStart `
    -OwnerPassword $demoOwnerPassword
```

Cambiar puerto/fecha/UUID por los valores elegidos. El script no escribe variables del usuario de Windows y no almacena contraseñas ni tokens. No introducir secretos como texto en la línea de comandos o compartirlos por chat. Escribe un único JSON en salida y guarda el mismo reporte en `target/demo-data/demo-<RunId>.json`.

## Recorrido y resultado esperado

| Fase | Acción mediante API | Resultado que comprueba el cliente |
|---|---|---|
| Precondiciones | OpenAPI, perfil sin token y lectura administrativa del veterinario | Contrato 0.9.0/0.10.0, rechazo sin autenticación (`401`), autorización vigente y veterinario activo antes de crear propietarios |
| Proceso 1 | Registro/login de dos propietarios nuevos `demo-a-<RunId>@example.test` y `demo-b-<RunId>@example.test`; mascota propia para cada uno | UUID reales, rol `OWNER` asignado por servidor; B no lee la mascota de A (`404`) |
| Disponibilidad | Administrador publica dos turnos; A consulta opciones | Rutas/horas/versiones devueltas por la API, turnos publicados y solicitables |
| Solicitud A | Crear solicitud y repetir idéntica clave/cuerpo | `201`, luego `200` con mismo UUID/versión; B no consulta cita A (`404`) |
| Permisos y versiones | A intenta confirmar; administrador confirma; intenta cancelar con versión anterior | `403`, confirmación válida y `409` de datos obsoletos sin alterar la cita |
| Cambios A | Reprogramar al segundo turno y cancelar con las versiones actuales | Conserva UUID/propietario; estado final `CANCELLED`; historia de solicitud/confirmación/reprogramación/cancelación |
| Agenda B | B solicita el primer turno liberado y administrador confirma | Estado final `CONFIRMED`, visible en la agenda propia de B |
| Avisos | Consultar historia y `GET /api/v1/appointments/{id}/notifications` | Intenciones de cancelación A y confirmación B consultables; no exige que SMTP haya terminado |

La constancia de reprogramación usa `OTHER`, fecha actual y una nota claramente **ficticia de simulación**; no atribuye una conversación real a nadie. El reporte no demuestra concurrencia simultánea, pantallas frontend, aceptación del equipo o captura en Mailtrap.

## Reporte y fallos parciales

El JSON incluye `RunId`, origen local, fechas, `Success`, `Owners` (id/email/petId), `Slots`, `Appointments` (id/ownerId/status/version), `Checks`, `Error`, `ReportPath` y **`MailtrapCaptureVerified=false`**. Los datos proceden de respuestas verificadas; no contiene cuerpos HTTP, contraseñas, tokens, OTP ni diagnósticos privados del proveedor. Los códigos de API del reporte se limitan a una lista de códigos funcionales conocidos.

Cada recurso con UUID confirmado se guarda enseguida. Si falla un paso, la salida/proceso indica fallo y conserva el reporte parcial; **no borra automáticamente cuentas, mascotas, turnos ni citas**. Una pérdida de conexión puede ocurrir después de que el servidor confirme una operación: revisar el reporte y la API antes de repetir. No convertir un reporte parcial en una demostración completa.

Cada ejecución usa otro `RunId` y nuevos correos; repetir el helper puede crear más datos. Además, elegir de nuevo los mismos turnos producirá conflicto. La reversión manual se decide con las operaciones existentes y sus reglas; no borrar historia ni modificar la base por SQL para limpiar el ensayo.

La verificación visual de mensajes y del recordatorio real de 24 horas continúa en [verificacion-mailtrap.md](verificacion-mailtrap.md). `SENT` acredita aceptación SMTP; `MailtrapCaptureVerified=false` tampoco significa que el backend no haya enviado: significa que **este cliente no inspecciona la bandeja del Sandbox**.
