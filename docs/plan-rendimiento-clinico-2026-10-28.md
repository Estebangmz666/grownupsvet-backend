# Plan previo R5: reducir latencia de escritura clínica

Estado posterior: la [regresión coordinada](verificacion-rendimiento-regresion-2026-10-28.md) aprobó 762 pruebas en 65 suites, sin fallos, errores u omisiones, después de implementar R5a/R5c/B1 y la guarda de replay DRAFT documentada más adelante. Los apartados de análisis conservan su estado previo a implementación; R5d no se implementó y la latencia desplegada del parche aún no se acredita.

28/10/2026. **Solo análisis y plan; no se implementó Java, SQL, tests o configuración.** No se ejecutaron Maven, BD, SMTP o cambios de nube. Se mantiene R3 encendido durante la dosis/control de I8. Cada fase siguiente requiere coordinación del agente principal antes de editar sus archivos o ejecutar verificaciones.

## Evidencia y diagnóstico

`output/backend-cierre-2026-10-28/cloud-run-latencies-after-finalization.json` correlaciona 28/28 intentos elegibles con la revisión `grownupsvet-backend-admission-960c8898`. Son latencias de solicitud Cloud Run, no tiempo CPU exclusivo de Java. En la muestra M9:

| Operación | N | P95 de la muestra, ms | Meta <500 ms |
| --- | ---: | ---: | --- |
| Crear borrador | 1 | 583,705 | No cumple |
| Guardar textos | 1 | 608,833 | No cumple |
| Guardar tratamiento | 1 | 793,140 | No cumple |
| Finalizar | 1 | 881,638 | No cumple |
| Replay de finalización | 1 | 464,917 | Cumple |
| Detalle | 5 | 349,856 | Cumple |
| Historia | 2 | 396,147 | Cumple |

El código contiene varias consultas cliente secuenciales por escritura. La distancia Cloud Run Iowa–Render Ohio es una **hipótesis de contribución** al costo por roundtrip; el colector no demuestra tiempos SQL individuales ni causalidad exclusiva. No se propone cambiar región, proveedor o presupuesto.

### Conteo estático del camino nuevo exitoso

Se cuentan llamadas JDBC explícitas, excluyendo BEGIN/COMMIT, autenticación y SQL interno de triggers. No equivale todavía al número exacto de paquetes/roundtrips de protocolo. `p` y `c` son cantidades de recetas y controles.

| Camino | Descomposición | Llamadas |
| --- | --- | ---: |
| Create | validación actor1 + snapshot/locks5 + operación1 + existencia1 + insert padre/evento/operación3 + reread1 | 12 |
| Update textos | validación actor exterior1 + lockForMutation8 + operación1 + update1 + auditoría2 + reread1 | 14 |
| Treatment | lockForMutation8 + operación1 + deletes2 + inserts p+c + update1 + auditoría2 + lectura de hijos2 | 16+p+c; 18 con1+1 |
| Finalize | lockForMutation8 + operación1 + actualizaciones/auditoría/supersede6 + reread1 + scheduling3+p+c | 19+p+c; 21 con1+1 |

`lockForMutation` hace validación actor1, lookup encuentro→cita1, snapshot cita1 y cinco locks en total: owner, pet, veterinarian, appointment, encounter. `update` repite la validación de actor antes de llamar a ese helper. El validador JWT agrega normalmente dos lecturas: usuario actual y revocación; `UserPermissionResolver` resuelve permisos en memoria. Esas dos comprobaciones de seguridad deben seguir ocurriendo en cada solicitud, sin caché entre solicitudes.

Fuentes principales: `clinical/service/ClinicalEncounterService.java` (create, update, lockForMutation, lockClinicalParticipants, readMutationResult), `ClinicalTreatmentService.java` (update/readTreatment/inserciones), `ClinicalFinalizationService.java` (transacción y evento) y `clinical/reminder/service/ClinicalReminderSchedulingService.java` (listener BEFORE_COMMIT). No hay advisory lock clínico en esos caminos: la fila del veterinario serializa las claves por actor. Los advisory locks de bootstrap/recuperación no deben modificarse para esta mejora.

## Invariantes obligatorias

1. Mantener adquisición **owner → pet → veterinarian → appointment → encounter**, sin iniciar por el encuentro o por un conjunto de filas con orden de ejecución implícito. Revalidar identidad de owner/pet después del lock de cita y conservar validación fresca de actor/participantes.
2. Mantener estados/roles, acceso404/403, versiones esperadas y su precedencia de error. El replay sigue comprobando clave durable, actor, tipo, encuentro y fingerprint; payload distinto produce conflicto y no una respuesta cacheada.
3. Mantener una transacción para padre, hijos, eventos, operaciones, COMPLETED, supersede de correos y schedules. Preservar triggers, constraints únicas, finalización inmutable y métricas AFTER_COMMIT. Un fallo tardío debe revertir todo.
4. El listener de schedules sigue BEFORE_COMMIT con transacción MANDATORY. SMTP, materialización y dispatch continúan fuera de esa transacción. No forzar workers ni cambiar reloj o ventanas para mejorar mediciones.
5. No quitar la comprobación JWT de cuenta/revocación, no cachear datos privados entre peticiones y no cambiar contratos, límites50+50, normalización, orden de listas o fingerprints históricos.

## Fase R5a: instrumentación acotada y respuestas sin reread

Antes de optimizar, añadir en tests un contador de ejecuciones JDBC por operación sobre fixtures aislados: SQL parametrizado clasificado, sin valores, correos, tokens o textos clínicos. Distinguir execute/executeBatch, sentencia y commit; separar la autenticación del servicio. No habilitar logging SQL de producción. Fijar una referencia con0,1 y50 hijos y medir el camino de replay por separado.

Para create, update y finalize, usar **INSERT/UPDATE … RETURNING** con el RowMapper actual para obtener la fila persistida en la misma llamada de escritura. Suprimir únicamente el SELECT final de respuesta. No construir timestamps/versiones a mano ni cambiar la respuesta del replay: este devuelve el estado actual bloqueado según el contrato existente. Mantener `readMutationResult` o un equivalente interno que no reaplique la política de lectura humana al resultado de una mutación ya autorizada.

Ahorro esperado: una llamada por escritura de padre. RETURNING conserva los valores efectivamente almacenados y los cambios de triggers; esto evita discrepancias de precisión temporal/numérica.

## Fase R5b: snapshot inicial y comprobaciones sin duplicación

Conservar una comprobación temprana de actor cuando sea necesaria para mantener403 antes de revelar existencia. Reemplazar lecturas repetidas por **una consulta inicial simple de identidad**, sin locks, que traiga actor role/status y la ruta encuentro→cita→owner/pet/slot. Usar LEFT JOIN con comprobaciones Java explícitas para distinguir actor inválido y recurso inexistente en el mismo orden actual. Para create, la misma idea parte de appointmentId.

Ese snapshot solo localiza filas. Después se adquieren los cinco locks mediante sentencias separadas en el orden anterior y se revalida la cita. No se considera el snapshot autorización suficiente ni se reutiliza el usuario leído por JWT como reemplazo del lock fresco. Pasar una identidad tipada interna al helper de locks evita reconsultar rol desde update y evita flags ambiguos de autorización.

En create, después de locks, una consulta puede devolver conjuntamente la operación previa y `exists(encounter por appointment)`, respetando primero el conflicto de fingerprint/replay y después el conflicto de atención existente. No depender de una excepción SQL genérica para reinterpretar todas las violaciones de constraints como el mismo409.

Objetivo estático orientativo al combinar esta fase con RETURNING: create12→9 y textos14→10 llamadas de servicio. La comprobación final se obtiene del contador real; no se declarará SLO cumplido por esta estimación.

## Fase R5c: hijos y schedules de tamaño acotado

### Tratamiento

- Mantener normalización/fingerprint antes de escribir y los dos DELETE acotados al encounter bloqueado.
- Sustituir `jdbc.update` por hijo por un INSERT multirrow parametrizado por tabla, con RETURNING de las columnas almacenadas y posición. Máximo50 filas por tabla; construir únicamente nombres de parámetros/tuplas, nunca interpolar valores clínicos.
- Usar los hijos retornados, ordenados por posición, como respuesta del camino nuevo; evita dos SELECT finales y conserva la escala NUMERIC/precisión de PostgreSQL. En listas vacías omitir el INSERT y devolver lista vacía.
- El replay conserva lectura del estado actual; una consulta conjunta de hijos por discriminador/posición o agregación JSON solo se introduce si mantiene el snapshot y simplifica la implementación. No cambiar REPEATABLE_READ del GET.
- `batchUpdate` es una alternativa simple para escrituras sin resultado, especialmente listas grandes. No asumir que su API garantiza un solo roundtrip ni activar globalmente rewrite del driver sin medir efectos. Para el caso1+1 no aporta por sí solo el ahorro principal.

### Programación de recordatorios

Conservar la consulta que exige FINALIZED/COMPLETED, incluida cuando no hay hijos. Reemplazar los dos SELECT de hijos y los inserts individuales por un **INSERT … SELECT con UNION ALL** de recetas/controles, `ON CONFLICT DO NOTHING` y un único parámetro `now` obtenido del Clock actual. Es una escritura por conjunto sin CTE de locks.

Reproducir exactamente: receta next_due=starts_at si now<ends_at; control start=scheduledAt−24h, acotado a now únicamente si está dentro de la ventana; FINISHED cuando ya terminó. Mantener UUIDs por fila, fuente y FK/unique por encounter/source. No borrar o reescribir schedules existentes para resolver conflictos.

Scheduling baja de3+p+c a2 llamadas, incluyendo validación. Combinado con las fases anteriores, el caso finalización1+1 puede bajar21→aproximadamente15 llamadas de servicio. Tratamiento1+1 puede bajar18→aproximadamente14; con50+50 evita crecimiento lineal de ejecuciones. Esto puede seguir siendo insuficiente para500ms: requiere nueva medición.

## Fase R5d condicionada al resultado: comprimir el tramo de locks

Si las fases simples conservan demasiados viajes para alcanzar la meta, presentar al agente principal una segunda decisión antes de implementarla: **función PL/pgSQL pequeña de adquisición secuencial de contexto**, mediante migración nueva, con sentencias owner/pet/vet/appointment/encounter claramente ordenadas y snapshot final devuelto. Solo mover adquisición/lectura, manteniendo las políticas y errores de negocio en Java. No trasladar toda la atención clínica a una función monolítica.

La función reduciría varios viajes de red a una llamada cliente sin eliminar locks. Requiere revisar interacción con las mutaciones de citas del otro análisis, privilegios de ejecución, ausencia de nuevos locks/advisory globales y pruebas reales de carreras/deadlock. No implementar una CTE de locks suponiendo que el planner respetará un orden textual. Esta fase tiene mayor costo de mantenimiento y queda condicionada a evidencia; no se propone como primer cambio ni se introduce una migración ahora.

La autenticación podría pasar de dos SELECT a una proyección de usuario+EXISTS revocación en otra mejora coordinada, preservando todas las verificaciones actuales. No se mezcla ese refactor compartido con la primera entrega clínica ni se atribuye su ahorro a este plan sin medición.

## Verificación necesaria antes de aprobar implementación/despliegue

- Reutilizar suites HTTP, transaccionales, history y reminder existentes. Ampliar solo escenarios que distingan regresiones reales, no assertions que copien el SQL.
- Fallo del segundo grupo de hijos tras DELETE: texto, listas previas, versión, eventos, operaciones y métricas permanecen iguales.
- Fallo tardío de schedule CONTROL tras insertar PRESCRIPTION: rollback integral de FINALIZED/COMPLETED, auditoría, supersede y schedules. Existe cobertura base `lateSchedulingFailureRollsBackEncounterAppointmentEventsAndAllSchedules`; debe seguir verde con la escritura por conjunto.
- Mismo actor+tipo+clientRequestId/intención en concurrencia: una mutación, un evento, una operación y una métrica de commit. Misma clave/payload diferente o encuentro diferente:409 sin modificación. Probar también reutilización de clave entre citas del mismo veterinario.
- Carreras con inactivar owner/pet/vet, reasignar/cancelar/confirmar cita y dos ediciones de encounter; conservar bloqueos y evitar snapshot de autorización obsoleto. No validar esto solo con mocks.
- Equivalencia de respuesta nueva/replay/GET para escala de dosis, timestamps microsegundos, orden0..49, campos nulos y listas vacías. Mantener FINALIZED inmutable, acceso OWNER/assignedVET/finalizador y GET paginado coherente.
- Contar ejecuciones sin parámetros para comprobar reducción y ausencia de crecimiento por hijo. No confundir la reducción de llamadas con una garantía de latencia.
- Coordinar un único Maven focal, seguido por los gates exigidos si hay cambios. Desplegar solo con autorización y después de la ventana de recordatorios actual. Comparar muestras nuevas con trace/revisión/operación y Cloud Run request latency; conservar fallos y muestras anteriores. No mezclar cliente extremo-a-extremo con servidor ni recrear correos reales para inflar N.

## Fuentes primarias

- [PostgreSQL18 INSERT, múltiples filas y RETURNING](https://www.postgresql.org/docs/18/sql-insert.html).
- [PostgreSQL18 UPDATE RETURNING](https://www.postgresql.org/docs/18/sql-update.html).
- [Datos devueltos por DML y valores de triggers](https://www.postgresql.org/docs/18/dml-returning.html).
- [Spring JDBC batch operations](https://docs.spring.io/spring-framework/reference/data-access/jdbc/advanced.html).
- [NamedParameterJdbcTemplate.batchUpdate](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.html).

No se ejecutaron mutaciones, profiling de producción o benchmarks nuevos al redactar este plan. El incumplimiento observado de las cuatro escrituras M9 permanece abierto hasta una implementación validada y nueva evidencia.

## Delta previo propuesto R5b1: identidad de mutación, sujeto a revisión de root

Este delta todavía no está autorizado para implementar. No combina los cinco locks ni sustituye la autenticación:

1. Conservar `lockForMutation(actorId, encounterId)` para tratamiento/finalización: verifica el veterinario una vez y delega a un helper privado claramente denominado `lockForValidatedVeterinarian`.
2. `update` mantiene su validación temprana de veterinario antes de validar el request, conservando la precedencia403. Después delega directamente a ese helper privado; así evita repetir el mismo SELECT sin lock. El helper no acepta un flag de autorización.
3. El helper consulta encuentro→cita→slot y owner/pet en un único SELECT sin locks, filtrando el veterinario asignado como hoy. Sustituye únicamente el lookup de appointmentId seguido del snapshot de identidad. Recurso inexistente/asignación ajena conserva404; no se divulga contenido clínico.
4. Una identidad interna tipada contendrá appointmentId/ownerId/petId. El camino create conserva su SELECT de identidad por appointmentId y ambos caminos llaman al mismo tramo de locks owner→pet→vet→appointment. Después se vuelven a comparar owner/pet bajo el lock de cita y se toma encounterFORUPDATE. El rol/estado del veterinario se valida otra vez al bloquearlo; no se autoriza desde el snapshot.
5. Verificar carreras de reasignación/owner/pet, actor inválido, claves concurrentes y error403/404. Ahorro esperado: dos SELECT en update y uno en tratamiento/finalización, además de R5a/c; no supone alcanzar500ms. No cambia auth, operaciones de citas, fingerprints, migraciones o privilegios.

## Delta previo de privacidad: replay de un DRAFT que perdió elegibilidad

La revisión independiente detectó un comportamiento anterior a R5: CREATE/UPDATE/TREATMENT devolvían el replay válido antes de comprobar el estado actual de la cita/participantes. Una cita cancelada podía ocultar su DRAFT en GET y devolverlo al repetir la misma intención. Root autorizó corregirlo según las reglas clínicas ya aprobadas, antes de implementar este delta:

1. Conservar primero la comprobación durable de clave/actor/tipo/encounter/fingerprint y su conflicto. Si coincide y el encounter actual sigue DRAFT, exigir la misma `requireEligibleAppointment` usando el contexto bloqueado actual; responder409 existente sin DTO para cita inelegible o participantes inactivos.
2. Compartir un helper explícito de elegibilidad del replay. No validar expectedVersion actual ni exigir DRAFT a un replay de recurso FINALIZED: los replays legítimos de CREATE/UPDATE/TREATMENT y finalización deben seguir disponibles.
3. CREATE reutilizará su SELECT del encounter anterior con FOR UPDATE después del lock de cita, completando el mismo orden de locks; no añadirá una consulta. UPDATE/TREATMENT/FINALIZE ya tienen el encounter bloqueado. El lookup de un veterinario reasignado conserva404 antes del replay.
4. Probar misma intención/clave tras cancelar o inactivar owner/pet, con snapshot de estado/hijos/eventos/operaciones/métricas sin cambios y ningún DTO devuelto. Probar old-vet reasignado404 y los tres replays después de FINALIZED con versiones originales. Rechazar después de CONFIRMED no es una transición legal de este dominio; no se fabricará ese estado mediante SQL para aparentar cobertura.

Este delta no modifica datos existentes, claves, payloads, fingerprints o el contrato. Cierra una vía de lectura preexistente al reutilizar la elegibilidad actual del borrador.

## Corte de implementación para el focal coordinado

Root autorizó R5a, R5c y el delta acotado R5b1, además del cierre de replayDRAFT. Se implementaron en las cuatro clases clínicas indicadas, sin migraciones, funciones almacenadas o cambios de auth/citas. Las dos revisiones de código independientes no encontraron regresiones nuevas de RETURNING, orden/precisión o ventanas de schedules; la vía preexistente del replay se corrigió como delta separado.

Conteo estático posterior del camino nuevo: create11, textos11, tratamiento1+1=15 y finalización1+1=16 llamadas de servicio. Tratamiento con50+50 mantiene15; con0+0 usa13. Este conteo excluye transacciones/auth y no mide paquetes del driver. La autenticación de C1 se coordina por separado y no se atribuye a este cambio clínico.

Se añadieron tests de respuestas persistidas y precisión,0/1/50 hijos con orden/NUMERIC6 e invocaciones JDBC preparadas acotadas, rollback del segundo grupo, precedencia403/400/404, schedules vacíos inválidos/bordes de ventana y replaysDRAFT inelegibles/FINALIZED legítimos. Las suites existentes conservan concurrencia, rollback tardío de scheduling e inmutabilidad. Los tests estaban escritos y pendientes de Maven al redactar este corte: no se ejecutó Maven por este agente ni se declara mejora de latencia o SLO acreditada sin medición nueva.

## Delta de validación del fixture de reasignación

El primer `clean verify` coordinado por root ejecutó762 tests, con0 fallos,1 error y0 omitidos. El único error fue la preparación del caso REASSIGNED de `sameIntentDraftReplaysCannotExposeContentAfterEligibilityOrAssignmentIsLost`: desactivar directamente al veterinario no marca su cita para reasignación; el servicio rechazó correctamente una cita que aún estaba ASSIGNED. Se conserva ese resultado fallido como evidencia.

Antes de cambiar el test, se revisaron `AppointmentService.reassign/markVeterinarianDisabled` y el helper existente `AppointmentTransactionIntegrationTests.disableOriginalVeterinarian`. El ajuste se limita al fixture: en una transacción independiente bloquear el usuario, desactivarlo y llamar a `appointments.markVeterinarianDisabled`, que marca NEEDS_REASSIGNMENT e incrementa la versión de la cita. Leer la versión resultante para la reasignación real y conservar la posterior reactivación del veterinario anterior y sus tres replays404. No cambiar reglas, código productivo, assertions de privacidad, MIME u otras suites. La verificación posterior queda a cargo del focal y full coordinados por root; este agente no ejecuta Maven ni SQL externo.
