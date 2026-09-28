# Verificación del incremento de citas — 28 de septiembre de 2026

## Resultado ejecutado

| Comprobación | Resultado |
|---|---|
| Base de datos | PostgreSQL 18.2, `grownupsvet_test`; Flyway validó V1–V15 y aplicó las nuevas migraciones en esquemas aislados de pruebas. No se ejecutaron cambios sobre `grownupsvet_dev`. |
| `mvnw.cmd verify` | **431 pruebas**, 0 fallos, 0 errores, 0 omisiones; compilación y JAR completados. Ejecución final: 1 min 21 s. |
| OpenAPI | Versión 3.1.0, contrato 0.7.0, **57 operaciones en 38 rutas**. Snapshot copiado desde el documento servido por Spring después de validar. |
| Validador independiente | **31 respuestas HTTP reales** válidas; rechazó omisiones de campos requeridos, tipos incorrectos, campos adicionales y enums inválidos. Validó 7 esquemas de disponibilidad y 5 de citas, nulabilidad y respuestas de las 7 operaciones de citas. |
| `git diff --check` | Sin errores de whitespace. Git informó avisos de conversión LF/CRLF para archivos editados en Windows. |

Comandos ejecutados desde `grownupsvet-backend`:

```powershell
.\mvnw.cmd verify
& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py
Copy-Item -LiteralPath target/generated-openapi/openapi.json -Destination docs/openapi/openapi.json
git diff --check
```

Las pruebas usan identidades y datos ficticios. La prueba de competencia por un mismo turno utiliza dos peticiones HTTP y transacciones PostgreSQL independientes, bloqueadas y coordinadas por PostgreSQL; solo una crea la cita, asignación e historia. Las pruebas de concurrencia y ciclo de vida de disponibilidad conservan esquemas aislados creados para la ejecución.

La revisión comenzó con una ejecución satisfactoria de las 406 pruebas de la implementación recibida. La inspección encontró defectos no cubiertos por esa suite; se corrigieron y añadieron 25 pruebas. El conjunto de citas contiene ahora 21 pruebas HTTP, 7 transaccionales y 6 de entrega de avisos, además de la carrera HTTP por un turno incluida en disponibilidad.

## Hallazgos corregidos en la revisión

| Problema encontrado | Corrección y evidencia |
|---|---|
| Confirmación bloqueaba turno antes de cita; corte/rechazo invertían ese orden mediante las FK del evento | Se unificó el orden. Una prueba usa conexiones independientes, un bloqueo controlado y `lock_timeout` para ejercer las FK mientras la confirmación espera. Otra enfrenta confirmación y rechazo y verifica un solo cambio efectivo. |
| Se podían trasladar citas normales o confirmadas ya terminadas | La reasignación exige necesidad de reemplazo, veterinario original inactivo, cita vigente y participantes activos. Pruebas de rechazo, conservación del origen y replay tras archivar la mascota. |
| Un rechazo con otro motivo o actor se aceptaba como replay | Se contrasta el evento persistido y la intención normalizada. Los estados humanos permitidos se validan antes de reconocer repetición. |
| Motivos de espacios podían producir 500 o historia vacía; fecha de acuerdo no era estricta | Validación `NotBlank`, comprobación de servicio y fecha textual ISO con offset. Pruebas HTTP de cuerpos inválidos sin cambios persistidos. |
| Un correo antiguo podía anunciar una asignación superada o pedir gestionar una cita ya resuelta | Se verifica asignación y versión de reasignación; se recalculan pendientes administrativos. Pruebas de reintento superado, cancelación, rehabilitación y exclusión de envíos por cita. |
| El correo solo dependía del sondeo y un worker antiguo podía sobrescribir un intento recuperado | Evento `AFTER_COMMIT`, executor acotado y token de adquisición en V15. Pruebas con commits/rollback reales y recuperación concurrente; SMTP simulado. |
| El contrato de historial no exigía todos los campos nulos ni enumeraba todos sus estados/eventos | DTO y personalización OpenAPI desde Java; validador independiente de campos, enums, tipos, nulabilidad y errores documentados. |

`AppointmentTransactionIntegrationTests` también procesa 104 pendientes vencidas, conserva una confirmada, comprueba una segunda ejecución sin efectos y verifica restricciones de historia/snapshots directamente en PostgreSQL. La simulación del tiempo demuestra recuperación de pendientes tras una interrupción; no se afirma haber reiniciado un despliegue real.

## Trazabilidad local propuesta

| Criterio y alcance | Implementación | Evidencia de prueba |
|---|---|---|
| SCRUM-36 — integrar la disponibilidad con ocupaciones reales | `AppointmentService`, consulta de turnos disponibles, guardas de bloqueo/cambio de hora y restricciones de V12–V14 | Ocupación excluida del listado; conflicto de turno; mascota sin solapamiento entre veterinarios; competencia simultánea; restricciones e historia ejercidas directamente en PostgreSQL. |
| SCRUM-39 — solicitud, consulta, confirmación y rechazo | Controlador y servicio de citas, permisos por rol, eventos inmutables e idempotencia de creación | Creación/reintento; conflicto por turno ocupado; rechazo libera turno; filtros/páginas e historial; cuenta y mascota; corte diario conserva confirmadas. |
| SCRUM-79 — contrato de API de citas y disponibilidad | Anotaciones Java, contrato generado 0.7.0, ejemplos reales y validador independiente | `GET /v3/api-docs`; 57 operaciones/38 rutas; 31 ejemplos de respuesta y verificaciones negativas de campos/tipos. |
| Ampliación acordada — reasignación coordinada | Cambio de asignación y horario atómico, registro de acuerdo previo, tareas durables de correo | Sin constancia no cambia la cita ni crea tarea; con constancia guarda acuerdo, conserva UUID y crea aviso para propietario; guardas de vigencia, elegibilidad y replay. |
| Ampliación acordada — veterinario deshabilitado | `StaffManagementService` marca citas futuras con necesidad de reasignación y encola aviso a administradores activos | Cita permanece `REQUESTED`, cambia asignación a `NEEDS_REASSIGNMENT`, evento único y tarea pendiente durable. |
| P3 — consulta de correo | Endpoint limitado al veterinario asignado, cita confirmada no terminada, motivo y auditoría en transacción | Auditoría antes de respuesta; veterinario anterior pierde acceso tras reasignación; respuesta de acceso incluida en ejemplos OpenAPI. |

Esta tabla acredita el alcance backend comprobado para las tareas. La publicación y las transiciones se registran por separado mediante el commit y los comentarios de Jira. El snapshot generado no acredita la revisión de contrato por los tres integrantes. SCRUM-43, SCRUM-47 y SCRUM-81 solo reciben avance parcial: no se implementaron cancelación manual de confirmadas, inasistencia ni avisos generales.

## Cambios de contrato para los consumidores

- `AvailableVeterinarianSlotResponseDTO` ahora incluye `version`; la solicitud debe enviar `expectedAvailabilitySlotVersion` obtenido al consultar la disponibilidad.
- Se añaden rutas de creación, agenda, detalle, historial, estado, reasignación y consulta justificada del correo bajo `/api/v1/appointments`.
- Cambió el conjunto exacto de permisos JWT. Los usuarios deben iniciar sesión nuevamente para recibir permisos actuales.
- La configuración del entorno debe definir `APPOINTMENT_WORKDAY_START_TIME` con la hora local `HH:mm` acordada para operación, en `America/Bogota`. `00:00` se usa solo en el perfil de pruebas; no se define una hora de negocio por defecto.
- El envío de notificaciones necesita `MAIL_FROM` y SMTP. Las seis pruebas nuevas de entrega usan PostgreSQL real y `JavaMailSender` simulado: verifican despacho tras commit, ausencia de despacho tras rollback, errores, obsolescencia y recuperación de adquisición. No demuestran SMTP remoto ni recepción en buzón.

## Límites de esta evidencia

- No se desplegó la aplicación ni se contactó a los integrantes. La hora laboral de producción sigue siendo una configuración externa obligatoria; la usada en pruebas no constituye una decisión comercial.
- No se probó integración con los dos frontends, accesibilidad visual, correo remoto, entrega exactamente una vez, revisiones compartidas ni aceptación del contrato.
- La matriz de criterios de Jira aún no cubre por sí sola todas las ampliaciones de reasignación, correo a administradores y consulta del correo; se conservan como alcance aprobado con evidencia de backend separada.
- Un correo en tránsito SMTP no puede retirarse si la asignación cambia después de la última revalidación; la agenda conserva el estado vigente. La respuesta incierta del proveedor puede ocasionar un reenvío.
- Los resultados describen el código verificado el 28/09/2026 sobre PostgreSQL de pruebas; no constituyen evidencia de un despliegue.
