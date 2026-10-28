# Plan previo de rendimiento de citas — 28/10/2026

## Estado y evidencia de partida

**Plan previo conservado; subincremento C1 de citas autorizado e implementado, pendiente de pruebas coordinadas.** C2 y autenticación permanecen como propuestas separadas. No cambian reglas, contratos, locks, transacciones, recursos cloud ni el colector ya verificado.

La muestra real correlacionó 28/28 intentos con request logs de `grownupsvet-backend-admission-960c8898`. Fuente conservada fuera de Git: `output/backend-cierre-2026-10-28/cloud-run-latencies-after-finalization.json` del workspace padre. Sus incumplimientos permanecen como resultados del corte:

| Operación observada | Latencia de solicitud en Cloud Run | Frente a 500 ms |
| --- | ---: | --- |
| Crear cita | 549.604 ms | No cumple en esa muestra |
| Confirmar administrativamente | 749.171 ms | No cumple en esa muestra |
| Consultar disponibilidad | Aproximadamente 366.7 ms | Cumple en esa muestra |
| Publicar disponibilidad | Aproximadamente 450 ms | Cumple en esa muestra |

Las operaciones clínicas M9 tienen su análisis separado: borrador 583.7, guardado 608.8, tratamiento 793.1 y finalización 881.6 ms en el corte comunicado. No se modifican desde este plan.

No se estima un nuevo P95 a partir de contar consultas. Cloud Run en `us-central1` y PostgreSQL Render Ohio hacen plausible que los viajes secuenciales contribuyan, pero no hay un perfil por sentencia que atribuya causalidad. También pueden intervenir espera del pool, CPU, calentamiento y trabajo asíncrono. Este análisis no ejecutó SQL, pruebas, HTTP, gcloud ni nuevas mediciones.

## Inspección del código y unidad del conteo

Fuentes principales:

- `appointment/service/AppointmentService.java`: `create`, `updateStatus`, `get`, `replay`, `insertAssignment`, `insertEvent` y helpers de locks.
- `appointment/service/AppointmentNotificationService.java`: `recordEvent` y `enqueue`.
- `authentication/security/CurrentAccountJwtValidator.java`, `UserRepository` y `RevokedAccessTokenRepository`.
- `AppointmentEmailDispatcher`, `AppointmentEmailConfiguration` y migraciones de citas V12/V14/V15/V16/V17/V21.

Los números siguientes cuentan **sentencias emitidas desde llamadas JDBC/JPA de la aplicación**, por un camino válido concreto. No incluyen BEGIN/COMMIT/ROLLBACK, configuración de conexión/pool, SQL interno de triggers/FK, ni trabajo posterior del worker. Son conteos estáticos del código, no tiempos de red medidos.

El JWT válido hace hoy dos lecturas: `UserRepository.findById` y `RevokedAccessTokenRepository.existsById(issuer,jti)`. `User` contiene campos escalares: no hay asociaciones eager adicionales en ese `findById`. Los tokens estructuralmente inválidos o con snapshot de cuenta inválido pueden salir antes; no se atribuyen dos consultas a todos los rechazos.

### Creación nueva sin conflicto

| Secuencia | Llamadas |
| --- | ---: |
| Lock de propietario + rol/estado; búsqueda de intención previa | 2 |
| Lock mascota; lectura inicial del slot; lock de veterinario; lock del slot; validación de veterinario/perfil posterior | 5 |
| INSERT cita RETURNING id; INSERT asignación; INSERT evento REQUESTED | 3 |
| `get`: releer cuenta del propietario y SELECT de respuesta con joins | 2 |
| **Servicio de cita** | **12** |
| JWT válido, separado del servicio | **2** |
| **Total de aplicación de ese camino** | **14** |

`REQUESTED` no encola un aviso en `AppointmentNotificationService.recordEvent`: retorna antes de SQL. Si el INSERT no crea una fila, el código hace comprobaciones adicionales de replay/conflicto; no se mezclan con el camino exitoso anterior.

Un replay de creación existente consume hoy cinco llamadas de servicio: lock propietario, SELECT id por intención, SELECT fingerprint, SELECT cuenta desde `get` y SELECT de respuesta. Con JWT válido son siete. Su estado terminal y las reglas de fingerprint deben mantenerse.

### Confirmación nueva, sin replay

| Secuencia | Llamadas |
| --- | ---: |
| Lock administrador; snapshot cita; lock propietario; lock mascota; lock veterinario; lock cita; lock slot | 7 |
| Revalidación de participantes/slot después de adquirir locks | 1 |
| UPDATE cita; INSERT evento CONFIRMED | 2 |
| Superseder tareas anteriores; leer fuente de aviso; INSERT aviso inmediato | 3 |
| INSERT recordatorio si `startsAt - 24 h >= now` | 0–1 |
| `get`: cuenta del administrador y SELECT de respuesta | 2 |
| **Servicio de cita** | **15–16** |
| JWT válido | **2** |
| **Total de aplicación de ese camino** | **17–18** |

El horizonte de recordatorio está fijado en 24 horas y se valida en configuración. Una confirmación tardía no crea recordatorio retroactivo; por eso no se presupone una segunda tarea en la muestra de mañana a las 06:00. El replay de confirmación tiene otro camino: siete locks/snapshot, una comprobación del evento previo y dos lecturas de `get`; no vuelve a escribir evento ni outbox.

SMTP no se ejecuta dentro de `recordEvent`. El listener `AFTER_COMMIT` entrega el trabajo a un `ThreadPoolTaskExecutor` de un hilo y cola acotada; si la cola se rechaza, se conserva el trabajo durable para el poll. No se propone saltar leases, revalidación de destinatarios o comprobaciones inmediatamente anteriores a SMTP. El worker puede competir por CPU/pool, pero eso no es un envío síncrono dentro de la petición.

## Garantías que no se pueden debilitar

1. Conservar el orden vigente de locks. En creación: propietario → mascota → veterinario → slot. En confirmación: administrador, snapshot para identificar recursos, propietario → mascota → veterinario → cita → slot. No convertir el snapshot sin lock en autorización ni adelantar locks del slot respecto de la cita.
2. Conservar `NO KEY UPDATE` de cuentas y los `FOR UPDATE` existentes; no agrupar locks con un `IN` sin orden ni sustituirlos por un JOIN cuyo orden de adquisición dependa del planner.
3. Revalidar estado/rol/propiedad y elegibilidad después de las esperas pertinentes. No usar el snapshot JWT previo como sustituto de la cuenta bloqueada por negocio. Mantener la lectura posterior que comprueba el perfil veterinario hasta demostrar otra forma equivalente bajo los mismos locks.
4. Evaluar reloj/corte después de adquirir los locks como hoy. No cambiar mínimo de un día, horizonte de sesenta días, hora de corte, transiciones, horario ni igualdad del recordatorio de 24 horas.
5. Conservar fingerprint, claves de intención, replay exacto, códigos de conflicto, unicidad de turno/mascota, auditoría inmutable y relaciones con slot/asignación.
6. Cita, versión, asignación, evento, supersesión y nuevas tareas deben confirmar o revertir juntos. No introducir `REQUIRES_NEW`, `commit` intermedio, aviso antes del commit o aceptación de tareas faltantes.
7. Mantener revocación y estado de cuenta consultados en cada petición protegida. No cachear cuentas, permisos o denylist; no sustituirlos por claims sin consulta vigente. El limitador de login V23 permanece intacto.
8. No cambiar DTOs, rutas, OpenAPI, formato de payload durable, metadatos de error, configuración de nube ni región. No registrar SQL con binds, correos, tokens o contenido clínico.

## Incremento R5-C1: lecturas redundantes, con alcance pequeño

### Respuesta dentro de una mutación

Añadir un helper privado de lectura de `AppointmentResponseDTO` que reciba la cuenta ya bloqueada/validada por esa misma transacción y aplique el mismo predicado de visibilidad. Usarlo al terminar creación, confirmación y sus replays. El `get(actorId,id)` público conserva su consulta y validación para solicitudes de lectura independientes.

Esto quita una lectura repetida de cuenta por mutación, sin eliminar el lock inicial ni reutilizar autenticación anterior al lock. No construir la respuesta a partir del body del cliente: conservar el SELECT de proyección vigente y sus joins/campos.

### Replay de creación

Leer `id` y `request_fingerprint` en una sola proyección, conservando `MessageDigest.isEqual` y el mismo error por intención alterada. Devolver la respuesta con el helper de cuenta bloqueada. El camino nuevo sigue haciendo una sola búsqueda vacía de intención.

### Snapshot de autenticación, como cambio separado revisable

Sustituir las dos lecturas del JWT válido por una proyección SQL de cuenta y `EXISTS` de revocación por `(issuer,jwt_id)`, en el mismo statement. Devolver exclusivamente id/email/rol/estado/authenticationVersion y revocado; no cargar el hash de contraseña. Conservar validación previa de claims, comparación exacta de versión, email, rol y conjunto de permisos, y el mismo `INVALID_TOKEN`.

La consulta valida un snapshot consistente de ambos estados por petición. No comparte ese snapshot con los locks de negocio ni amplía la vida de una sesión. Debe seguir rechazando una revocación/cambio ya confirmado antes de una validación posterior. No se mezcla este cambio con `UserLoginService`, Argon2, rate limit, emisor de JWT o keystores.

| Camino | Actual, incluida autenticación válida | Objetivo C1 | Ahorro de statements |
| --- | ---: | ---: | ---: |
| Crear nueva | 14 | 12 | 2 |
| Replay creación | 7 | 4 | 3 |
| Confirmar sin recordatorio | 17 | 15 | 2 |
| Confirmar con recordatorio | 18 | 16 | 2 |

Son objetivos del diseño para esos caminos, no resultados verificados ni milisegundos prometidos.

## Incremento R5-C2: agrupar escrituras posteriores a los locks

Debe ser una revisión separada después de C1 o un bloque explícitamente autorizado por el agente principal. La opción recomendada para evaluar es un **statement PostgreSQL con CTEs de escritura y RETURNING**, no concatenar SQL interpolado ni usar APIs internas del driver. Mantener `NamedParameterJdbcTemplate` y parámetros tipados.

### Creación

Conservar las siete lecturas/locks previas. Agrupar INSERT cita → INSERT asignación → INSERT evento REQUESTED y proyección final. Usar los datos `RETURNING` de la cita insertada para los hijos y la respuesta; no releer la tabla base esperando ver efectos de otro CTE dentro del mismo snapshot.

`ON CONFLICT DO NOTHING` se aplica solo donde existe hoy, al INSERT de cita. Una inserción vacía no puede producir hijos, evento, outbox ni contador. Su salida debe distinguirla y ejecutar las mismas comprobaciones de replay/ocupación ya existentes. Si la cita se insertó pero la proyección de respuesta no existe, fallar y revertir, como hoy; no confundir ese fallo con un conflicto de reserva. Cualquier fallo de asignación/evento debe abortar toda la transacción.

Objetivo: siete lecturas/locks + un statement de escritura/proyección = **8 llamadas de servicio**; con el snapshot JWT de C1, **9** de aplicación, frente a 14 actuales. Ahorro objetivo total: cinco statements. Si la revisión SQL no demuestra equivalencia clara, comenzar solo agrupando asignación+evento con el padre ya insertado y conservar una lectura de respuesta; el ahorro será menor y se registrará como tal.

### Confirmación

Mantener los siete locks/snapshot y la octava consulta de elegibilidad. Extender su proyección únicamente con los datos internos requeridos del aviso que ya pertenecen a filas protegidas, conservando también las condiciones/joins de existencia que hoy verifica la fuente de aviso. No eliminar una validación solo porque su columna no se serialice.

Tras revalidar versión, reloj y participantes, agrupar UPDATE cita → INSERT evento → supersesión de tareas antiguas → INSERT de una o dos tareas → proyección de respuesta. La respuesta debe leer el estado/versión `RETURNING` de la actualización. El evento y las tareas nuevas deben depender explícitamente de las salidas de los CTEs anteriores; no asumir orden textual de ejecución.

Conservar exactamente el UUID de evento compartido, columnas de auditoría y payload JSON que hoy construye Java. Las tareas se crean con un INSERT de múltiples filas, con `ON CONFLICT(event_key,recipient_id,task_type) DO NOTHING` igual al actual; la fila de recordatorio solo existe bajo la condición de 24 horas original. El UPDATE de supersesión conserva sus estados, excepción `ADMIN_REASSIGNMENT`, lease y código actuales.

El writer agrupado puede devolver proyección de respuesta y recuentos de control. Debe comprobar que existe exactamente la cita/fuente esperada; cero filas no equivale a un éxito. Cero inserts de tareas por replay de un evento ya encolado sigue siendo admisible donde hoy es idempotente. El camino de replay de confirmación se conserva antes de cualquier escritura; no se fuerza a recorrer el nuevo writer.

Objetivo: ocho lecturas/locks + un statement de escritura/proyección = **9 llamadas de servicio**; con C1 JWT, **10** de aplicación, frente a 17–18 actuales. Ahorro objetivo total: siete u ocho statements, independiente de que haya una o dos tareas. La visibilidad entre CTEs, los triggers/FK y el orden de adquisición de locks de tareas deben probarse con PostgreSQL antes de aceptar esta forma. Si falla esa prueba o la consulta pierde claridad, dividir el writer en pasos explícitos y declarar el conteo alcanzado.

### Alternativa conservadora para avisos

Si no se autoriza agrupar toda la escritura, reutilizar un contexto de aviso obtenido bajo los mismos locks posteriores a la espera y sustituir los dos INSERT de tareas por un único INSERT multivalor. Ahorra una lectura de fuente y, solo cuando corresponde recordatorio, un INSERT. El resto de writers de cancelación/rechazo/reprogramación/corte mantiene su comportamiento hasta ser revisado. Esta alternativa por sí sola no se presenta como garantía de lograr 500 ms.

## Pruebas necesarias después de autorizar implementación

No se ejecutan en este análisis. Reutilizar suites reales con PostgreSQL y esquemas UUID; el agente principal coordina Maven para evitar ejecuciones paralelas.

1. **Equivalencia HTTP/contrato:** creación nueva/replay, misma intención alterada, confirmación/replay por actor/razón, estados terminales y matrices de permisos conservan DTO/status/errorCode. El snapshot OpenAPI debe seguir igual.
2. **Rollback tardío de creación:** un trigger de prueba rechaza el INSERT de asignación o evento después de intentar crear la cita. Verificar ausencia de cita/hijos/auditoría, contador cero y posibilidad de reintentar la misma intención. Justifica la nueva frontera de escritura agrupada.
3. **Rollback tardío de confirmación:** rechazar la fila de recordatorio o el aviso inmediato después del evento; comprobar estado/versión/eventos/tareas anteriores y lease exactamente restaurados, ninguna tarea nueva y ningún dispatch antes del commit. Incluir caso de dos filas para detectar un primer insert parcial.
4. **Idempotencia y carreras existentes:** mantener `concurrentReplaysCommitOneAppointmentAndOneInitialEvent`, confirmación vs rechazo/cancelación, reprogramación concurrente y unicidad de slot/mascota. Dos replays de confirmación deben dejar un solo evento y un solo aviso por tipo.
5. **Locks y relojes:** conservar pruebas de confirmación vs corte y FK de destinatario, cita antes de slot y participante desactivado mientras espera. Si el writer incorpora nuevas dependencias SQL, bloquear filas desde otra conexión y demostrar que no cambia el orden ni aparece deadlock; comprobar corte real después de esperar.
6. **Avisos:** reutilizar las verificaciones de 24 horas exactas, confirmación tardía sin recordatorio, supersesión, misma tarea reencolada, lease activo/expirado y rollback sin envío. Preservar payload/recipient/version y dispatcher durable; no efectuar SMTP externo en tests.
7. **JWT:** revocación y logout inmediato, cuenta desactivada, cambio de rol/email/authenticationVersion, permisos incompletos/sobrantes y usuario ausente. La nueva proyección debe emitir un único statement por JWT válido sin caché. Una sesión revocada antes de la siguiente solicitud nunca puede autorizar negocio.
8. **Presupuesto de statements:** un contador de ejecuciones JDBC de test, sin binds ni valores personales, debe medir el camino completo después de preparar fixtures. Comprobar límites máximos acordados, no cada nombre de helper. Esto acredita menos ejecuciones síncronas de aplicación, no P95. No contar cada SELECT de trigger como un viaje extra ni ocultar BEGIN/COMMIT como si no existieran en el transporte.

## Archivos previstos y control de alcance

- C1: `AppointmentService`, proyección/repositorio interno de autenticación si se aprueba y `CurrentAccountJwtValidator`; pruebas focalizadas de citas y revocación.
- C2: `AppointmentService` y `AppointmentNotificationService`; pruebas transaccionales y de outbox existentes, con pocos casos nuevos para los fallos introducidos por la agrupación.
- No editar migraciones ya aplicadas, DTOs, controlador REST, `AppointmentEmailDispatcher`, limitador de login V23, colector, scripts de aceptación ni configuración cloud para obtener una mejora artificial. Si una implementación requiere ampliar ese alcance, volver a la revisión del plan.
- Mantener Java 25 y convenciones de records/proyecciones/mappers existentes. No introducir ORM adicional, dependencia, caché, procedimiento almacenado o driver privado.

## Verificación posterior y aceptación del incremento

Después de los focales y regresión coordinados, comprobar reportes sin omisiones, OpenAPI, snapshot y gates. Solo entonces el agente principal decide publicar/desplegar el nuevo commit/digest. No hay autorización implícita de despliegue en este documento.

La comparación posterior usará revisión exacta nueva, mismo colector/traces y operaciones elegibles, con cantidad de casos, ventana, carga declarada y errores/timeouts conservados. Mantener separado cliente extremo a extremo, Cloud Run y Prometheus. No repetir hasta conseguir un subconjunto favorable, eliminar el corte previo ni acreditar una población con una muestra funcional pequeña. Si el resultado sigue por encima de la meta, se informa y se revisa el siguiente cuello de botella sin relajar seguridad o reglas.

La primera decisión solicitada al agente principal fue qué combinación C1/C2 implementar y quién poseería la modificación de autenticación, para no solaparla con la optimización clínica. La sección siguiente recoge su autorización limitada. Durante el análisis previo no se modificó Java ni se ejecutaron pruebas o accesos externos.

## Alcance autorizado para implementar R5-C1 de citas

El agente principal autorizó únicamente reutilizar la cuenta bloqueada en respuestas de `create`/`updateStatus` y sus replays, y unir id/fingerprint de intención de creación en una proyección. `get` público conserva su lectura por solicitud. Reprogramación/reasignación, autenticación, clínica, avisos, C2 y la infraestructura quedan fuera de esta implementación.

Los presupuestos de este subincremento **sin modificar autenticación** son: creación nueva 12→11 sentencias de servicio; replay de creación 5→3; confirmación 15–16→14–15; replay de confirmación 10→9. El JWT válido conserva sus dos consultas actuales y se suma por separado. Los objetivos C1 de la tabla anterior incluían también la optimización de autenticación propuesta, que no está autorizada en este subincremento.

Antes de añadir las pruebas, se fija este mecanismo: reutilizar la suite transaccional de citas y su conexión PostgreSQL ya asociada a la transacción, con un proxy de test que cuente ejecuciones de `PreparedStatement`. Los servicios de cita/avisos usados para esa medición reciben el mismo template medido y se ejecutan dentro del `TransactionTemplate` existente; se conserva publicación de eventos y outbox real, con dispatcher ya sustituido por el mock de la suite. El proxy no almacena SQL, binds ni valores personales y no cierra la conexión que posee el transaction manager. El conteo excluye autenticación, apertura/commit y SQL interno de triggers.

Casos focalizados: respuesta nueva idéntica a GET; replay de creación después de cancelación devuelve el estado actual y no duplica escrituras; intención alterada conserva conflicto y no escribe; confirmación/replay con y sin recordatorio mantienen DTO, versión, eventos y tareas; GET vuelve a leer la cuenta y aplica su rol vigente. Los presupuestos son máximos por camino, para no impedir una reducción futura equivalente. Las pruebas existentes siguen acreditando concurrencia y rollback; no se duplican esas matrices. No se ejecutará Maven hasta coordinación del agente principal.

Implementación entregada en `AppointmentService` y cuatro métodos nuevos en `AppointmentTransactionIntegrationTests` (cinco ejecuciones por el caso parametrizado de 23/24 horas). Revisión propia del diff y `git diff --check` correctos. No se ejecutaron ni compilaron las pruebas; los presupuestos anteriores siguen siendo expectativas hasta la ejecución coordinada. No se modificaron autenticación, clínica, correo, REST, migraciones ni configuración.

## Plan previo autorizado R5-C2: creación y confirmación acotadas

Este apartado se escribe antes de editar el incremento C2. El agente principal confirmó C1 con un focal de 149 pruebas en ocho suites y autorizó la opción conservadora siguiente. La estimación no acredita una latencia desplegada.

### Visibilidad y estructura SQL verificadas

Se consultaron las fuentes oficiales de PostgreSQL18: [DML en WITH](https://www.postgresql.org/docs/18/queries-with.html#QUERIES-WITH-MODIFYING), [RETURNING](https://www.postgresql.org/docs/18/dml-returning.html) e [INSERT](https://www.postgresql.org/docs/18/sql-insert.html). Las ramas DML comparten snapshot; los datos modificados deben pasar por RETURNING. No se presupone orden textual ni se actualiza la misma fila dos veces dentro de una sentencia. Cada rama DML se ejecuta íntegramente; el resultado exterior no convierte una escritura parcial en éxito.

1. **Crear:** conservar las siete lecturas/locks y todas las reglas C1. Un writer privado dentro de `AppointmentService` insertará cita RETURNING → asignación RETURNING → evento REQUESTED RETURNING, con dependencias explícitas por id. La proyección final leerá exclusivamente el padre retornado y los joins vigentes. Un LEFT JOIN lateral permitirá distinguir padre no insertado (cero filas, mismas comprobaciones de conflicto/replay) de padre creado cuya proyección esté incompleta (error y rollback). Exigir una asignación y un evento en el camino creado. No llamar a notificaciones para REQUESTED, igual que hoy. Emitir la métrica únicamente tras obtener una creación completa. Objetivo: **8 sentencias de servicio**.
2. **Confirmar:** conservar las ocho lecturas/locks/elegibilidad, reloj, corte, versión y replay sin escribir. Un writer privado hará únicamente UPDATE cita RETURNING → INSERT evento CONFIRMED RETURNING → respuesta desde el padre retornado. La versión retornada identifica el evento persistido mediante la lectura de fuente existente; esta obtiene `appointment_events.id` y lo usa como `event_key` del outbox, conservando su vínculo previo. El UUID independiente de cada tarea sigue identificando esa fila del outbox. Después llamar al servicio de avisos existente: supersesión y lectura de fuente se mantienen en statements separados, posteriores al writer y con la misma transacción. Así la lectura de fuente observa cita/evento ya persistidos y no depende de visibilidad entre CTEs.
3. **Avisos de confirmación:** añadir un camino acotado para CONFIRMED que conserva supersesión/fuente/payload y ejecuta un INSERT multivalor con una fila inmediata y una fila de recordatorio solo cuando corresponde. Sus UUIDs siguen siendo independientes; ambas filas comparten evento, destinatario, payload y la unicidad vigente. Mantener igualdad del límite de24h, ON CONFLICT por evento/destinatario/tipo y publicación de `AppointmentNotificationsQueuedEvent` después de todas las filas. Las demás transiciones usan el camino actual. Objetivo de confirmación: **12 sentencias de servicio** con una o dos tareas; replay conserva el presupuesto C1.
4. **Proyección compartida:** separar únicamente columnas y joins de la proyección privada para reutilizarlos con `appointments` o con el CTE retornado, sin duplicar campos DTO. No introducir repositorio público, nuevo bean, API, constructor adicional o migración. Esto conserva también los fixtures directos del contador C1.

### Pruebas y finalización coordinadas

Conservar los máximos C1 y añadir pruebas focalizadas de los nuevos máximos C2. Usar triggers temporales, limitados al propietario/cita del fixture, que fallen en INSERT de asignación, evento REQUESTED, evento CONFIRMED y segunda fila OWNER_REMINDER. Cada fallo debe dejar intactos padre/versión/asignación/eventos/tareas previas y permitir repetir la misma solicitud una vez retirado el trigger. La prueba del recordatorio incluirá una tarea anterior con lease para comprobar rollback exacto de la supersesión, y comprobará que no hubo dispatch antes del commit fallido.

Verificar asimismo conflicto ON CONFLICT de turno con otro propietario sin crear asignación/evento extra, DTOs nuevos/replay/GET, evento y payload compartidos, y las suites existentes de carreras/rollback/24horas. Revisar el diff y congelar los archivos antes del único Maven coordinado por el agente principal. No tocar autenticación, clínica, dispatcher SMTP, fuentes de aceptación, CI, nube ni contratos. La alternativa de contexto de fuente que ahorraría otra lectura queda expresamente fuera.

### Entrega C2 pendiente de ejecución

Implementados writers privados de creación/confirmación en `AppointmentService` y rama multivalor exclusivamente CONFIRMED en `AppointmentNotificationService`. Se mantiene la lectura de fuente después del statement de cita/evento. Si falta la proyección de respuesta en confirmación, su error se difiere hasta después del servicio de avisos para conservar la precedencia previa de los errores de fuente; cualquier fallo aún revierte toda la transacción. Los demás writers conservan su camino.

Añadidos cinco métodos de prueba, ocho ejecuciones parametrizadas: presupuesto8/12 con y sin recordatorio y equivalencia del payload/evento; fallo de asignación; fallo de evento REQUESTED; fallo de evento CONFIRMED; fallo de primera/segunda tarea con restauración exacta del lease anterior y sin dispatch; conflicto de turno entre propietarios sin hijos/métrica extra. Los máximos C1 permanecen sin cambios. Revisión propia y whitespace/diff-check correctos; no se ejecutó compilación, Maven, SQL ni HTTP. Los resultados de PostgreSQL y los presupuestos siguen pendientes del focal coordinado.

### Resultado de la ejecución coordinada posterior

La [regresión completa](verificacion-rendimiento-regresion-2026-10-28.md) aprobó 762 pruebas en 65 suites, incluidos los presupuestos máximos C2 de 8/12 y las pruebas reales de rollback/outbox. Contrato, snapshot y gates negativos también pasaron. La expectativa de latencia desplegada permanece pendiente de medición de la imagen nueva; los apartados anteriores conservan el estado previo a ejecutar cada fase.
