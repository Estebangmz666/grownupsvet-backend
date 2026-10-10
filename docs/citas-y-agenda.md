# Citas, cambios administrativos y avisos

Incremento backend **0.9.0**: añade avisos generales y recordatorios P12 sobre las citas 0.7.0 y los cambios administrativos P07 de 0.8.0. La zona de negocio es `America/Bogota`; cada turno dura 30 minutos y representa un intervalo `[inicio, fin)`. Los turnos contiguos son válidos.

## Estados, ocupación e historia

| Estado de cita | Ocupa el turno | Transiciones permitidas |
|---|---:|---|
| `REQUESTED` | Sí | `CONFIRMED`, `REJECTED`, `CANCELLED` |
| `CONFIRMED` | Sí | `CANCELLED` antes del inicio; `NO_SHOW` al terminar el turno; reprogramación/reasignación conservan estado |
| `REJECTED` | No | Terminal |
| `CANCELLED` | No | Terminal; cancelación administrativa o corte diario, con eventos/actores distinguibles |
| `NO_SHOW` | No | Terminal; únicamente registro administrativo sobre una confirmada cuyo turno terminó |

La marca de asignación (`ASSIGNED` o `NEEDS_REASSIGNMENT`) es independiente del estado funcional. Deshabilitar al veterinario mantiene la cita y la ocupación hasta una reasignación atómica; el caso queda visible para administración. Cada cambio efectivo agrega un evento inmutable y conserva las asignaciones anteriores. El vencimiento del corte usa actor `SYSTEM` y no un usuario ficticio.

La ocupación se protege en PostgreSQL para citas activas por turno y por mascota e intervalo. No se aplica exclusividad al propietario. Confirmar conserva la cita; rechazar, cancelar y marcar inasistencia liberan ocupación en la misma transacción. El turno pasado no vuelve a ofrecerse para nuevas solicitudes. Una cita terminal no vuelve a estado activo por reintento y no tiene reapertura/corrección clínica en este alcance.

## Permisos

| Rol | Citas | Correo del propietario |
|---|---|---|
| `OWNER` | Crear y consultar únicamente propias; consultar avisos de su cita destinados a él | Sin acceso |
| `VETERINARIAN` | Consultar únicamente citas actualmente asignadas | Revelación puntual para cita confirmada asignada, antes de terminar y con motivo auditado |
| `ADMINISTRATOR` | Consultar todas y sus estados de aviso; confirmar, rechazar, cancelar, reprogramar, reasignar y registrar inasistencia | Sin endpoint de revelación en este incremento |
| `SUPER_ADMIN` | Sin permisos de agenda por herencia | Sin acceso |

El propietario y la mascota deben seguir activos para confirmar y reprogramar. Archivar o desactivar no cancela citas existentes. Cancelar o registrar inasistencia no requiere que esos participantes ni el veterinario sigan activos; el administrador sí debe estar activo. Crear una solicitud requiere mascota propia y activa; el propietario se deriva de la identidad autenticada.

## Tiempo y corte

Las solicitudes nuevas deben ser para una fecha local posterior a hoy y a no más de 60 días, dentro de turnos publicados y disponibles. La fecha de hoy nunca se ofrece para alta, incluso antes del corte. Mañana puede solicitarse aunque falten menos de 24 horas.

Esteban confirmó **06:00** como inicio de jornada en `America/Bogota`. `APPOINTMENT_WORKDAY_START_TIME` permite suministrar ese `HH:mm` por entorno; el perfil `prod` incorpora el valor acordado. Al alcanzar el corte de la fecha local, el proceso cancela todas las solicitudes `REQUESTED` de esa fecha, incluidas las que esperan reasignación, preserva las confirmadas y puede recuperar trabajo tras reinicio. La confirmación requiere `ahora < corte` y que el turno todavía no haya comenzado.

El vencimiento aplicado queda guardado al crear o reprogramar una solicitud pendiente. Un cambio posterior de configuración no altera solicitudes existentes.

## API

Las nueve operaciones de citas se documentan en OpenAPI desde Java: creación idempotente, consulta paginada, detalle, historial, consulta de avisos, cambios de estado, reprogramación administrativa, reasignación excepcional y revelación justificada del correo. Los turnos disponibles continúan en el recurso de disponibilidad y exponen su versión para proteger la selección frente a cambios concurrentes.

La creación recibe una clave `clientRequestId` UUID y el contenido original normalizado. Una repetición de la misma intención devuelve la cita existente; reutilizar la clave con otro contenido produce conflicto. La autorización y el estado de cuenta se comprueban en cada petición, incluso al resolver una repetición.

`PATCH /api/v1/appointments/{appointmentId}/status` permite confirmar, rechazar, cancelar o registrar `NO_SHOW`, exclusivamente con `APPOINTMENT_MANAGE`. Requiere la versión observada. Todas salvo confirmación exigen motivo no vacío, máximo 1000 caracteres. Una repetición inmediata solo se reconoce si coincide actor, transición y motivo normalizado con el evento humano persistido. Una cancelación `SYSTEM` al corte no es replay de una cancelación administrativa.

Las consultas por agenda usan fechas locales inclusivas, rango máximo de 31 días, páginas desde cero, tamaño predeterminado 20 y máximo 100. Las respuestas autenticadas son privadas y no almacenables. Los errores siguen `application/problem+json` y no exponen detalles SQL ni datos privados.

## Cambios administrativos P07

Esteban confirmó estas reglas el 08/10/2026. Cancelar `REQUESTED` o `CONFIRMED` exige `ahora < inicio original`; en el instante exacto del inicio se rechaza con conflicto. Registrar inasistencia exige `CONFIRMED` y `ahora >= fin`; no existe marcado automático ni tolerancia adicional. Ambos conservan identidad, snapshots y motivo, y agregan eventos humanos inmutables (`CANCELLED` o `NO_SHOW`).

`POST /api/v1/appointments/{appointmentId}/reschedulings` reprograma una cita normalmente `ASSIGNED`, antes de su inicio original. Si es `REQUESTED`, su corte original también debe seguir vigente, aunque el sondeo no la haya cancelado todavía. Propietario, mascota y veterinarios deben estar activos. Una cita `NEEDS_REASSIGNMENT` utiliza la operación excepcional del apartado siguiente.

`RescheduleAppointmentRequestDTO` exige `clientRequestId`, `availabilitySlotId`, `expectedAvailabilitySlotVersion`, `expectedVersion`, `reason`, `agreementChannel`, `agreementContactedAt` y `agreementNote`. El acuerdo previo siempre es obligatorio: canal `PHONE`, `WHATSAPP` u `OTHER`, fecha textual ISO con offset no posterior a la operación y nota no vacía de hasta 500 caracteres. Solo se registra la constancia; no se verifica el contacto externo.

El destino debe ser otro turno publicado y libre, del mismo veterinario u otro activo, con fecha local desde mañana hasta el día 60 inclusive. La reprogramación conserva UUID, propietario, mascota y estado; una pendiente recibe el corte del nuevo día, calculado con la configuración vigente. El servicio vuelve a comprobar los límites temporales después de adquirir los bloqueos. Si falla el destino, permanecen intactas la reserva original, versión e historia.

La intención se persiste en `appointment_operation_idempotency` con tipo `RESCHEDULING`, fingerprint del actor y contenido normalizado, y unicidad por cita/tipo/clave. Mismo actor/clave/intención devuelve `200` con el estado actual, incluso si ocurrió después otro cambio; otro actor o contenido produce `409 APPOINTMENT_IDEMPOTENCY_CONFLICT`. Cada replay mantiene la autorización vigente. Versiones obsoletas, plazos, estados o capacidad incompatibles producen `409`, sin éxito aparente.

El incremento histórico P07/0.8.0 no creó correos generales ni recordatorios; conservó los avisos excepcionales. El incremento actual P12/0.9.0 añade los avisos acordados en el apartado siguiente. La agenda y los eventos siguen siendo la fuente del resultado de un cambio; la inasistencia no genera correo.

## Avisos y recordatorios P12 — incremento 0.9.0

Esteban confirmó el canal **Mailtrap Sandbox**, con mensajes capturados allí como evidencia suficiente para la entrega académica de los procesos 1 y 2. Una captura de Sandbox no demuestra recepción en un buzón real. Las pruebas automatizadas de despacho usan SMTP simulado; la captura remota y la integración de ambos portales son comprobaciones separadas.

| Cambio efectivo | Aviso al propietario |
|---|---|
| Confirmación | `OWNER_CONFIRMED` |
| Rechazo | `OWNER_REJECTED` |
| Cancelación manual o corte diario | `OWNER_CANCELLED` |
| Reprogramación general | `OWNER_RESCHEDULED` |
| Reasignación excepcional | `OWNER_REASSIGNED`, conservado |
| Inasistencia | Ningún correo nuevo |

La tarea se guarda junto con el evento en la misma transacción de negocio. Su identidad es evento/destinatario/tipo; una intención ya persistida no crea otra tarea al reintentar. El replay de la operación no agrega otro evento ni otro aviso. El corte utiliza el evento `DAILY_CUTOFF`, distinto de la cancelación humana, pero ambos informan la cancelación. Los avisos de rechazo/cancelación siguen siendo elegibles en su estado terminal; no se filtran como si necesitaran una cita activa.

Una cita `CONFIRMED` programa `OWNER_REMINDER` **24 horas antes** de su inicio, calculadas sobre instantes. En la frontera exacta se crea el recordatorio; una confirmación con menos de 24 horas solo crea confirmación, sin recordatorio retroactivo. Reprogramar/reasignar supera los pendientes del horario anterior y crea el recordatorio de la nueva programación únicamente cuando su momento todavía no pasó. Cada programación tiene su propia intención persistida.

Una tarea programada puede recuperarse tras interrupción si la cita continúa confirmada, normalmente asignada, con veterinario activo y sin haber comenzado. Al comenzar o quedar superada ya no se envía. La auditoría de acceso al correo no altera la programación: se compara la revisión relevante del evento y no la versión global de la cita. Las tareas nuevas se originan en cambios efectivos; no hay una carga retroactiva automática de citas anteriores al incremento.

El propietario desactivado **recibe los cambios y conserva el recordatorio** de sus citas confirmadas vigentes. Desactivar acceso no cancela la cita ni elimina el destinatario. Esta regla de entrega no habilita acceso a la API ni modifica las guardas de cuentas inactivas.

`scheduled_at` conserva la fecha prevista y no se sustituye por el próximo intento ni por el vencimiento del lease. Los reintentos son acotados por `maximum-email-attempts`; si falta remitente o bean SMTP, el dispatcher deja el trabajo pendiente sin consumir intentos. El perfil de nube valida la configuración del canal antes de arrancar. `APPOINTMENT_REMINDER_ADVANCE` se liga a la duración y debe conservar `PT24H`, conforme al acuerdo.

El texto está en español e incluye datos mínimos de mascota, acción, profesional y fecha/hora. No incluye motivo interno, constancia del acuerdo, claves ni tokens. Si `FRONTEND_BASE_URL` está vacío no aparece enlace; si está configurado se utiliza la base validada, sin inventar rutas del portal.

### Consulta privada del estado de avisos

`GET /api/v1/appointments/{appointmentId}/notifications` devuelve `AppointmentNotificationPageResponseDTO`, con páginas desde cero, tamaño predeterminado 20 y máximo 100; orden estable por creación e identificador.

- Propietario: únicamente avisos de su cita destinados a él; los avisos a administración no afectan sus totales ni páginas. Una cita ajena responde `404`.
- Administrador: seguimiento de todos los avisos de la cita, sin correo, destinatario, payload, lease ni detalles SMTP privados.
- Veterinario y superadministrador: `403`; no se les añade permiso. Una sesión ausente o inválida recibe `401`.

Cada elemento contiene identificadores de aviso/cita, `notificationType`, `status`, `scheduledAt`, `nextAttemptAt`, `sentAt`, `attemptCount` y un código funcional público. Los campos anulables permanecen presentes. `nextAttemptAt` solo tiene valor cuando el estado es `PENDING`; la fecha prevista se conserva independientemente. Las respuestas son privadas y no almacenables.

| Estado | Significado para el consumidor |
|---|---|
| `PENDING` | Programado o esperando otro intento; puede tener fecha futura |
| `SENDING` | Intento adquirido por un worker |
| `SENT` | Aceptación SMTP registrada; no demuestra lectura ni entrega a buzón |
| `FAILED` | Fallo definitivo o intentos agotados |
| `SUPERSEDED` | El aviso ya no corresponde a la programación/estado vigente |

La tarea superada no promete que un mensaje ya en tránsito pueda retirarse. SMTP sigue siendo una entrega al menos una vez: una respuesta incierta puede originar reenvío. La no duplicación acreditada corresponde a intención, evento y tarea persistida.

## Reasignación, aviso y contacto

Deshabilitar un veterinario marca sus citas futuras para reasignación y crea trabajo durable para avisar a cada administrador activo. Una reasignación conserva UUID, propietario, mascota y estado de cita. Reserva el destino y libera el origen atómicamente. Cambiar fecha u hora requiere que el administrador registre canal, fecha y constancia breve del acuerdo previo con el propietario. Sin capacidad, la cita sigue pendiente de reasignación; no se cambia unilateralmente.

La operación exige `NEEDS_REASSIGNMENT`, veterinario original inactivo, propietario y mascota propios y activos, cita aún no terminada y destino futuro de otro veterinario activo. No sirve para reprogramar libremente citas asignadas ni para trasladar citas históricas. Una confirmada vigente puede resolverse a otro turno futuro del mismo día; una pendiente debe conservar un plazo de confirmación válido y no puede escapar del corte trasladándose a otro día.

Los motivos no pueden estar vacíos ni contener solo espacios. La fecha del contacto se envía como texto ISO 8601 con offset explícito, por ejemplo `2026-10-05T10:00:00-05:00`; no admite un número epoch ni una fecha sin zona. La constancia es obligatoria cuando cambia fecha u hora.

Tras el cambio efectivo se genera una notificación durable al propietario con veterinario y fecha/hora definitivos. El envío sucede después del commit y los errores reintentables quedan persistidos; SMTP no revierte la cita. La entrega exactamente una vez no se puede prometer.

Un evento después del commit activa un executor acotado; el sondeo periódico recupera trabajo pendiente. La adquisición de una tarea y el registro del resultado usan transacciones breves, sin mantenerlas durante SMTP. Cada intento tiene un token de adquisición: si vence y otro worker lo recupera, el anterior no puede sobrescribir su resultado. Solo se admite un intento vigente por cita.

Los avisos excepcionales antiguos siguen comprobando el último evento `REASSIGNED`/`RESCHEDULED`; los nuevos avisos usan su evento de negocio y los recordatorios la revisión relevante de programación. Los avisos superados quedan `SUPERSEDED`, incluso si se volvió al mismo horario. Una consulta auditada del correo aumenta versión, pero no supera un aviso vigente. Los estados terminales tampoco envían un aviso antiguo, aunque sí el nuevo aviso terminal correspondiente. Los avisos administrativos recalculan las citas que aún requieren gestión y comprueban que el administrador continúe activo. Un rechazo del executor deja el trabajo durable para el siguiente sondeo.

La comprobación de vigencia se realiza inmediatamente antes de llamar a SMTP. Un correo que ya está en tránsito no puede retirarse si la cita cambia durante su envío; el correo documenta la actualización y la agenda conserva el estado vigente. Una respuesta SMTP incierta puede producir un reenvío al recuperar el trabajo.

El correo del propietario no aparece en listados, historial ordinario, URL ni logs. Solo el veterinario actualmente asignado puede solicitarlo para una cita confirmada cuyo intervalo no terminó. Cada consulta requiere motivo y deja auditoría persistida antes de revelar el correo. Una reasignación retira el acceso del veterinario anterior.

## Límites

No se añaden frontend, urgencias, autoservicio de cambios, clínica, reapertura de terminales, múltiples sedes, grupos de mascotas ni integración WhatsApp. V17 añade programación y tipos P12 sin modificar V1–V16 ni sustituir índices de ocupación y triggers de historia/snapshots. La cola acepta avisos legacy; volver a un worker 0.8.0 que desconozca los tipos nuevos requiere un tratamiento explícito, no basta revertir la imagen.

La suite definitiva del incremento 0.9.0 contiene **508 pruebas**, sin fallos, errores ni omisiones, y la validación independiente utiliza **41 respuestas HTTP reales**: incluye cuatro páginas/errores adicionales de consulta de avisos. El [informe del incremento](verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md) registra la corrección de concurrencia de recuperación y el contenedor final. Es evidencia local automatizada; no acredita Google Cloud desplegado, entrega remota en Sandbox, revisión compartida ni integración/aceptación de los dos portales.
