# Verificación de cambios administrativos de cita — 8 de octubre de 2026

Zona de la sesión: `America/Bogota`. Incremento backend y contrato **0.8.0**; OpenAPI **3.1.0**.

Implementación autorizada por Esteban después de entregar el [plan P07](plan-implementacion-2026-10-08.md). La base del trabajo fue `main`, commit `777cd35b0a1bf589b10b1c167fcecd5d898d7cbd`, con el plan como único archivo local previo. **Al cierre de la implementación del 08/10**, los cambios eran locales, sin commit/publicación ni modificaciones de Jira. No se contactó a integrantes ni se desplegó la aplicación. El seguimiento del 09/10 se distingue más abajo.

## Resultado comprobado

| Verificación ejecutada | Resultado |
|---|---|
| Compilación | Java 25.0.4.1; compilación satisfactoria y JAR Spring Boot generado |
| Batería focalizada de citas | 65 pruebas, 0 fallos, 0 errores, 0 omisiones: HTTP 40, transaccionales 17 y dispatcher 8 |
| Suite completa final | `.\mvnw.cmd verify`: **462 pruebas**, 0 fallos, 0 errores, 0 omisiones; ejecución final completa de 1 min 47 s, terminó a las 09:09:15 del 08/10/2026 |
| Comprobación final de ejemplos HTTP | Después de ajustar la exportación de aplicación y replay para la misma cita, `-Dtest=AppointmentHttpIntegrationTests verify`: **40 pruebas**, sin fallos/errores/omisiones; 27,812 s, terminó a las 09:10:49 |
| PostgreSQL y migraciones | PostgreSQL 18.2, `grownupsvet_test`; V16 aplicada y validada con V1–V15, además de esquemas aislados para transacciones/dispatcher. No se ejecutaron migraciones sobre `grownupsvet_dev` |
| Contrato | OpenAPI 3.1.0, aplicación 0.8.0, **58 operaciones en 39 rutas**; ocho operaciones de citas |
| Validador independiente | **37 respuestas HTTP reales** válidas; comprobaciones negativas de campos requeridos, enteros, enums, nulabilidad y nuevo DTO/replay |
| Snapshot | `docs/openapi/openapi.json` copiado después de verificar; SHA-256 idéntico al documento generado |
| Diff | `git diff --check` sin errores; sin cambios en las migraciones aplicadas V1–V15 |

Las 462 son el total de la suite, **no se suman** las 65 focalizadas o las 40 repetidas. Se incorporaron **31 pruebas nuevas** respecto a las 431 históricas: 19 HTTP, 10 transaccionales y 2 de dispatcher. La última ejecución HTTP solo cambió/comprobó la exportación emparejada y su aserción de igualdad; no hubo cambios posteriores de producción.

Se conservaron los avisos de deprecación existentes de dependencias y las advertencias de springdoc sobre conversión de tipos durante generación. El contrato final servido por Spring y sus ejemplos sí pasaron la validación independiente; no se consideró suficiente que Maven estuviera en verde.

## Funcionalidad implementada

| Operación | Comportamiento verificado |
|---|---|
| Cancelación manual | Solo administrador activo; `REQUESTED`/`CONFIRMED` antes del inicio original; motivo obligatorio. `CANCELLED` libera la ocupación y conserva historia; en el instante del inicio se rechaza |
| Inasistencia | Solo administrador activo; `CONFIRMED` cuando `ahora >= fin` del intervalo de 30 minutos. `NO_SHOW` es terminal, conserva actor/motivo y no añade atención clínica |
| Reprogramación general | Solo administrador, con acuerdo previo completo; cita normalmente `ASSIGNED`, antes de su inicio original; una pendiente debe conservar vigente el corte original. Destino publicado/libre desde mañana al día 60 local inclusive |
| Identidad y estados | UUID, propietario, mascota y estado funcional se conservan al reprogramar; el destino puede ser del mismo veterinario u otro activo. Participantes activos requeridos para mover; no para registrar cancelación/inasistencia |
| Reserva e historia | Cambio de turno, nueva asignación histórica, evento, constancia e intención idempotente en una transacción. Conflicto/rollback conserva completamente el origen |
| Reintentos | Estado usa versión/replay inmediato con actor/transición/motivo. Reprogramación usa clave persistida y fingerprint del actor/contenido normalizado; actor o intención incompatibles producen conflicto |
| Corte | Una solicitud vencida no se resucita reprogramándola; el corte del nuevo día solo se calcula tras validar el original. Los plazos se vuelven a comprobar después de los bloqueos |
| Avisos existentes | El último evento `REASSIGNED`/`RESCHEDULED` decide vigencia. Un horario superado no se anuncia, aunque se vuelva al turno inicial o el payload sea antiguo. La auditoría del correo no invalida un aviso vigente |

Se amplió `PATCH /api/v1/appointments/{appointmentId}/status` y se añadió `POST /api/v1/appointments/{appointmentId}/reschedulings`. Ambas acciones reutilizan `APPOINTMENT_MANAGE`, exclusivo de administración. Propietario, veterinario y superadministrador no obtienen permiso de cambios; se conserva el aislamiento de lectura previo.

`RescheduleAppointmentRequestDTO` exige intención, destino, versiones, motivo y tres datos del acuerdo. La fecha requiere texto ISO 8601 con offset y no puede ser futura. Las propiedades desconocidas y conversiones silenciosas de tipos son rechazadas. El resultado es privado y no almacenable; los errores mantienen `application/problem+json`.

V16 amplía CHECK de estados, eventos, acuerdo y tipo `RESCHEDULING` en `appointment_operation_idempotency`; exige eventos humanos administrativos con motivo y forma válida. V1–V15, índices de ocupación y triggers de historia/snapshots se mantienen. Se reutilizó la persistencia JDBC existente, con auxiliares compartidos para movimiento y disponibilidad del destino; no se añadió repositorio ni infraestructura paralela.

## Evidencia de escenarios críticos

Pruebas en `src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/appointment/`:

| Escenario | Método de prueba representativo |
|---|---|
| Cancelación libera y solo repite intención humana | `AppointmentHttpIntegrationTests.administrativeCancellationReleasesTheSlotAndReplaysOnlyTheSameHumanIntent` |
| Frontera exacta de inicio | `cancellationAllowsTheInstantBeforeTheStartButRejectsTheStartAndLater` |
| Frontera exacta de fin y terminal sin reapertura | `noShowRequiresAConfirmedAppointmentAtOrAfterItsEndAndCannotReopenIt` |
| Permisos y participantes desactivados | `p07ManagementDeniesOwnersVeterinariansAndTheSuperAdministrator`; `terminalAdministrativeChangesDoNotRequireActiveParticipants` |
| Acuerdo/tipos/versiones sin escrituras parciales | `reschedulingRequiresCompleteStrictAgreementAndVersionsWithoutPartialWrites` |
| Ventana local y corte guardado | `reschedulingUsesTheLocalTomorrowThroughSixtyDaysWindow`; `reschedulingCannotResurrectARequestAtItsOriginalCutoff` |
| Identidad, cutoff nuevo y replay | `reschedulingPreservesIdentityAndRequestedStateAndUpdatesItsCutoffAtomically`; `reschedulingReplayIsPersistentAndRejectsAnotherActorOrChangedIntent` |
| Carreras entre conexiones | `AppointmentTransactionIntegrationTests.cancellationAndConfirmationCommitOnlyOneEffectiveTransition`; `cancellationAndReschedulingCommitOnlyOneEffectiveChange` |
| Reprogramación concurrente | `concurrentReschedulingReplaysCommitOneEventAssignmentAndIdempotencyRecord`; `concurrentReschedulingToDifferentDestinationsPreservesOneCompleteResult` |
| Rollback real tras guardar evento/intención | `rollbackAfterChangingTheReservationRestoresTheOriginalHistoryAndAllowsTheSameRequestToCommit` |
| Plazo vence mientras espera bloqueo PostgreSQL | `reschedulingWaitingForTheDestinationCannotPassTheOriginalStartDeadline`; `reschedulingWaitingForTheDestinationCannotEscapeTheOriginalConfirmationCutoff` |
| CHECK e historia inmutable | `noShowIsTerminalAndItsNewHistoryIsImmutableInPostgreSql`; `databaseRequiresRecordedAgreementAndPreservedStatusForReschedulingHistory` |
| Aviso obsoleto moderno/legacy | `AppointmentEmailDispatcherIntegrationTests.reschedulingWithTheSameVeterinarianAndReturningToTheOriginalSlotSupersedesAnOlderNotice` |
| Auditoría del correo no cambia vigencia | `auditedOwnerEmailAccessDoesNotSupersedeTheCurrentAssignmentNotice` |

Las carreras utilizan PostgreSQL real, conexiones/transacciones independientes y bloqueos coordinados. Los escenarios de dispatcher conservan pruebas de commit/rollback, adquisición, obsolescencia y worker vencido, con `JavaMailSender` simulado. Esto no demuestra SMTP remoto ni entrega a un buzón.

## Contrato y documentación

La validación independiente encontró y permitió corregir dos diferencias de evidencia: `@Size` sobrescribía el mínimo documental del motivo/nota a cero, corregido en Java con `min = 1`; y los ejemplos de aplicación/replay provenían inicialmente de citas diferentes, corregido exportando y comparando la misma operación HTTP. El snapshot nunca se corrigió manualmente.

Los seis ejemplos adicionales son `appointment-cancelled.json`, `appointment-no-show.json`, `appointment-rescheduled.json`, `appointment-rescheduling-replay.json`, `appointment-rescheduling-conflict-error.json` y `appointment-administrative-event-page.json`, generados con datos ficticios. Se encuentran, junto a los anteriores, en `target/generated-openapi/examples/`; el directorio generado no se publica como evidencia permanente por sí solo.

Se actualizaron README y guías de citas, disponibilidad, permisos, personal, mascotas y OpenAPI. Las menciones antiguas a citas inexistentes y reservas futuras se contrastaron con el código actual. La descripción de un destino inexistente se corrigió al `409` que realmente devuelve el servicio; `404` corresponde a cita inexistente. La guía de [OpenAPI](openapi/README.md) prepara la revisión concreta de estados, operación nueva, errores y permisos con ambos consumidores.

## Trazabilidad y límites de cierre — corte del 08/10

| Seguimiento | Estado acreditado por este trabajo |
|---|---|
| SCRUM-43 / backend P07 | Cancelación, reprogramación e inasistencia implementadas y comprobadas **localmente** |
| SCRUM-81 / contrato P07 | Parte P07 generada y validada localmente; publicación y revisión compartida pendientes |
| SCRUM-81 / P12 | No completo: conserva contrato de avisos generales/recordatorios pendiente |
| SCRUM-13 | No se acredita cierre integral: portales, SCRUM-44/45/46, revisión e integración/aceptación pendientes |
| SCRUM-44 | Ajuste de título/criterios propuesto por administración exclusiva; no se modificó tarea ni frontend de Juan José |
| SCRUM-47 / SCRUM-15 | Se conservan avisos excepcionales e invalidación; no se implementan avisos generales ni recordatorios |
| SCRUM-79 / SCRUM-12 | La revisión/aceptación P06 registrada como pendiente en el plan no se realizó ni se reemplaza con estas pruebas |

No se implementaron frontend, clínica, reapertura de terminales, nuevos canales o tipos de correo, Thymeleaf ni cambios de proveedor. La hora comercial real sigue siendo configuración externa obligatoria; no se adoptó la hora del perfil de pruebas como regla comercial. Un correo en tránsito no puede retirarse y una respuesta SMTP incierta puede originar reenvío.

Al cierre del 08/10, commit, push y modificaciones de Jira no estaban ejecutados. Esteban autorizó esos tres pasos el 09/10. Mensajes a integrantes y despliegue siguen siendo acciones distintas, no realizadas. La confirmación de publicación se acredita con el commit en el remoto y los comentarios de seguimiento de Jira, sin convertir las pruebas backend en aceptación de interfaces.

## Verificación previa a publicación — 9 de octubre de 2026

Se actualizó `origin` con `git fetch --no-tags origin`: antes de publicar, `main` y `origin/main` continuaban en `777cd35b0a1bf589b10b1c167fcecd5d898d7cbd`, con cero commits exclusivos por lado. Se revisaron los archivos del incremento y se conservó intacta V1–V15.

La revisión detectó que distintos capturadores MockMvc podían generar `http://localhost` como servidor en lugar de `http://localhost:8080`. Se fijó el puerto en las solicitudes de documento de citas/disponibilidad y se alineó la captura de login con la URL explícita ya usada por registro. Las pruebas comprueban esa URL antes de exportar; el snapshot se regeneró desde Java, sin edición manual.

| Comprobación del 09/10 | Resultado |
|---|---|
| Suite completa con capturas de citas/disponibilidad corregidas | `.\mvnw.cmd verify`: **462 pruebas**, 0 fallos, 0 errores, 0 omisiones; 1 min 27 s; finalizó a las 22:24:46, America/Bogota |
| Captura final de login | `-Dtest=UserLoginHttpIntegrationTests verify`: **9 pruebas**, sin fallos/errores/omisiones; 21,001 s; finalizó a las 22:25:27. Es una repetición de pruebas incluidas en las 462, no un total adicional |
| Validación independiente final | OpenAPI 3.1.0, contrato 0.8.0; **37 respuestas HTTP** válidas y comprobaciones negativas de esquema/replay |
| Servidor de desarrollo | `http://localhost:8080`, comprobado en el documento generado antes de copiar el snapshot |
| Publicación y Jira | Autorizadas por Esteban el 09/10; confirmación mediante remoto y seguimiento posterior. Solo SCRUM-43 cubre cierre backend; SCRUM-81 conserva P12/revisión y SCRUM-13 integración/aceptación pendientes |

Registros locales de esta revisión: `target/p07-publication-verify-2026-10-09.log`, `target/p07-publication-login-capture-2026-10-09.log` y `target/p07-publication-openapi-2026-10-09.log`. Se publican código, migración, guías, plan, informe y snapshot; los logs, credenciales y artefactos de `target` no forman parte del commit.

## Comandos y resultados reproducibles

Desde el repositorio backend, con `JAVA_HOME` apuntando al JDK 25 y credenciales PostgreSQL proporcionadas por el entorno, sin registrarlas en archivos:

```powershell
.\mvnw.cmd "-Dtest=AppointmentHttpIntegrationTests,AppointmentTransactionIntegrationTests,AppointmentEmailDispatcherIntegrationTests" test
.\mvnw.cmd verify
.\mvnw.cmd "-Dtest=AppointmentHttpIntegrationTests" verify
& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py
Copy-Item -LiteralPath target/generated-openapi/openapi.json -Destination docs/openapi/openapi.json
git diff --check
```

Los registros de esta sesión están en `target/p07-focused-tests.log`, `target/p07-verify-final.log`, `target/p07-http-contract-final.log` y `target/p07-openapi-validation.log`; los reportes JUnit están en `target/surefire-reports`. Son archivos locales generados, no commit/publicación ni evidencia de entorno externo.
