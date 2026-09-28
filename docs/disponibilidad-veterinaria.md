# Disponibilidad veterinaria — contrato 0.7.0

Este incremento agrega la publicación, consulta, edición y bloqueo de turnos concretos de 30 minutos y su integración con solicitudes y citas activas. La agenda disponible ya excluye ocupaciones reales.

## Permisos

| Actor activo | Permiso y alcance |
|---|---|
| `ADMINISTRATOR` | Crear y consultar turnos de cualquier veterinario; cambiar su hora; bloquear o volver a publicar; consultar auditoría. |
| `VETERINARIAN` | Consultar solo la agenda propia. No puede crear ni modificar turnos ni consultar auditoría administrativa. |
| `OWNER` | Consultar turnos publicados que cumplen la ventana, solo para veterinarios activos. No recibe bloqueos ni datos de auditoría. |
| `SUPER_ADMIN` | No recibe permisos de disponibilidad. |

Los endpoints requieren JWT Bearer. Los tokens existentes llevan una lista exacta de permisos; después del despliegue 0.7.0, los usuarios deben volver a iniciar sesión para obtener sus permisos vigentes.

## Reglas de tiempo y publicación

- El horario de negocio es `America/Bogota`; la sede considerada es Armenia.
- La anticipación mínima para nuevas solicitudes es desde el inicio del día local siguiente; el horizonte máximo continúa en 60 días. El inicio de jornada se configura con `APPOINTMENT_WORKDAY_START_TIME` (`HH:mm`) y no se asume un valor predeterminado de negocio.
- Los turnos son intervalos `[startsAt, endsAt)`, duran exactamente 30 minutos y comienzan en `:00` o `:30`, sin segundos ni fracciones. Turnos contiguos son válidos.
- Los instantes de API reciben texto ISO 8601 con offset; no admiten timestamps numéricos ni fechas sin offset. El servidor valida la cuadrícula en `America/Bogota`, persiste un instante y responde con UTC (`Z`) junto con `timeZone`.
- Publicar o mover exige que el turno sea futuro al validar la operación. Un turno iniciado no puede rescatarse cambiándolo de hora.
- Un administrador puede generar fechas concretas, incluidas fechas repetidas semanalmente dentro de un lote. El rango tiene de 1 a 31 fechas inclusivas, los días usan ISO 1=lunes a 7=domingo y cada lote puede crear hasta 1000 turnos. `dailyEndTime` es exclusivo; `24:00` únicamente permite terminar el día con un turno que empieza a las 23:30.
- El lote completo es atómico. Una colisión, incluso con un turno bloqueado, rechaza el lote completo. Una restricción única de PostgreSQL protege `(veterinarian_id, starts_at)`.
- Bloquear conserva el UUID y la fila. Volver a publicar conserva el mismo turno y aumenta su versión. El historial registra cada cambio efectivo; una solicitud sin cambios no crea evento ni nueva versión.
- Deshabilitar un veterinario oculta automáticamente sus turnos publicados a propietarios, sin borrarlos ni bloquearlos. Al rehabilitarlo, esos turnos vuelven a aparecer si siguen publicados y están dentro de la ventana. Los bloqueos administrativos se conservan.
- La consulta de propietarios incluye turnos desde el inicio del día local siguiente hasta 60 días desde la consulta. No se reciben nuevas solicitudes para la fecha local de hoy, aunque falte tiempo para el turno. Mañana puede solicitarse sin esperar 24 horas completas. La agenda administrativa y la agenda propia del veterinario no usan este filtro de alta.
- Las opciones excluyen turnos ya ocupados por citas `REQUESTED` o `CONFIRMED` y devuelven la versión del turno para detectar cambios después de que el propietario lo consultó. Bloquear un turno ocupado y cambiar la hora de cualquier turno con historial de citas producen un conflicto controlado.
- El filtro de fechas `from`/`to` de las consultas es local e inclusivo y admite hasta 31 fechas. Un resultado vacío responde `200` con `items: []`.

## Rutas nuevas

Todas las rutas parten de `/api/v1`. Los listados usan `page` desde 0 y `size` de 1 a 100; el valor predeterminado es `page=0`, `size=20`. Las respuestas autenticadas llevan `Cache-Control: no-store, private`.

| Método y ruta | Actor | Uso |
|---|---|---|
| `POST /veterinarians/{veterinarianId}/availability-slots` | Administrador | Publicar un turno (`201`, `Location`). Cuerpo: `startsAt`. |
| `POST /veterinarians/{veterinarianId}/availability-slot-batches` | Administrador | Generar turnos concretos (`201`), con `startDate`, `endDate`, `daysOfWeek`, `dailyStartTime`, `dailyEndTime`. |
| `GET /veterinarians/{veterinarianId}/availability-slots?from&to&status&page&size` | Administrador; veterinario propio | Listar turnos publicados, bloqueados o pasados en el rango. `status` es opcional. |
| `GET /veterinarians/{veterinarianId}/availability-slots/{slotId}` | Administrador; veterinario propio | Leer un turno de la agenda indicada. Un UUID de otra agenda devuelve 404. |
| `PUT /veterinarians/{veterinarianId}/availability-slots/{slotId}` | Administrador | Cambiar `startsAt`; requiere `expectedVersion` y `reason`. No cambia el veterinario ni el UUID. |
| `PATCH /veterinarians/{veterinarianId}/availability-slots/{slotId}/status` | Administrador | Bloquear o publicar; requiere `status`, `expectedVersion` y `reason`. |
| `GET /veterinarians/{veterinarianId}/availability-slots/{slotId}/events?page&size` | Administrador | Historial cronológico, con actor, motivo y estados anteriores/nuevos. |
| `GET /availability-slots?from&to&veterinarianId&page&size` | Propietario | Consultar únicamente opciones solicitables dentro de la ventana y sin datos privados o administrativos. |

No hay `DELETE`. Para retirar un turno, se bloquea y se conserva el historial.

### Ejemplos ficticios

Publicar un turno:

```http
POST /api/v1/veterinarians/8c34e580-8d8c-4b88-99d4-4d55d1c13d33/availability-slots
Authorization: Bearer <JWT>
Content-Type: application/json

{"startsAt":"2026-10-05T09:00:00-05:00"}
```

Generar el turno de 09:00 y el de 09:30 el lunes indicado; el fin `10:00` es exclusivo:

```json
{
  "startDate": "2026-10-05",
  "endDate": "2026-10-05",
  "daysOfWeek": [1],
  "dailyStartTime": "09:00",
  "dailyEndTime": "10:00"
}
```

Bloquear un turno con control de versión:

```json
{"status":"BLOCKED","expectedVersion":0,"reason":"Ajuste de agenda"}
```

Si el cliente conserva una versión anterior, recibe `409 CONCURRENT_UPDATE`. Una hora ocupada por otro turno recibe `409 AVAILABILITY_SLOT_CONFLICT`; un turno pasado recibe `409 AVAILABILITY_SLOT_NOT_EDITABLE`; un veterinario pendiente o deshabilitado no puede publicar ni cambiar la hora (`409 VETERINARIAN_NOT_ACTIVE`). Los detalles usan el formato `application/problem+json` común, sin exponer SQL ni restricciones internas.

`expectedVersion` y los elementos de `daysOfWeek` deben enviarse como enteros JSON. Las cadenas, fracciones y notación decimal o exponencial no se convierten silenciosamente a enteros: responden `400 TYPE_MISMATCH`, igual que un tipo inválido de `startsAt`, sin cambiar turnos ni historial. Los segundos o fracciones de segundo fuera de la cuadrícula responden `400 INVALID_AVAILABILITY_REQUEST`.

Los siete esquemas de respuesta de disponibilidad declaran todos sus campos como obligatorios y rechazan propiedades adicionales. En eventos de creación, `previousStartsAt`, `previousEndsAt`, `previousStatus` y `reason` están presentes con valor `null`; no se omiten. Las páginas vacías conservan sus metadatos e `items: []`.

## Persistencia y concurrencia

V10 crea `veterinarian_availability_slots` y `veterinarian_availability_events`; V11 refuerza las transiciones e intervalos válidos del historial. V12 añade citas, asignaciones históricas inmutables, eventos, tareas de correo e índices únicos parciales de ocupación activa; V13 registra la reactivación del veterinario y V14 valida en PostgreSQL que cada asignación conserve el turno, veterinario e intervalo originales. V12 añade además las guardas para impedir que un turno referenciado cambie de hora o que se bloquee con una cita activa. No se editaron migraciones aplicadas.

PostgreSQL comprueba la duración, cuadrícula, estado, versión y unicidad por profesional e inicio. Los eventos de disponibilidad, eventos de citas y asignaciones históricas no se pueden actualizar ni borrar. La consulta del propietario filtra en base de datos el estado del turno y de la cuenta, las fechas solicitables, las ocupaciones reales y los límites temporales.

La seguridad de la unicidad evita duplicados también ante solicitudes concurrentes. No se convierte cualquier error de base de datos en conflicto de horario. El control de versión de la petición detecta datos desactualizados además de la versión optimista de JPA.

## Integración con solicitudes y citas

La consulta del propietario excluye turnos ocupados por citas `REQUESTED` y `CONFIRMED`. PostgreSQL protege la ocupación activa por turno y por mascota/intervalo; el servicio vuelve a validar la versión del turno después de bloquearlo. La respuesta de disponibilidad incluye `version` para que la solicitud no reserve silenciosamente un turno que cambió desde su consulta.

Bloquear un turno ocupado falla y cambiar la hora de cualquier turno con historial de asignaciones falla, aunque la cita anterior ya se haya rechazado o cancelado. Rechazar o vencer una solicitud libera la ocupación dentro de la transacción que registra el evento terminal. Las reglas funcionales completas están en [Citas y agenda](citas-y-agenda.md).

## Verificación

La evidencia automatizada se registra en [OpenAPI y verificaciones](openapi/README.md) y en el [informe del incremento de citas](verificacion-citas-2026-09-28.md). Los ejemplos HTTP se generan desde pruebas con identidades ficticias sobre PostgreSQL. Esta API no acredita revisión de frontend, despliegue, accesibilidad visual ni revisión compartida del equipo.
