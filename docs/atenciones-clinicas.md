# Atenciones clínicas

El contrato canónico se genera desde los controladores y DTO en `/v3/api-docs`. Los datos clínicos requieren JWT y permisos clínicos específicos; los permisos de administración de citas no habilitan su lectura ni escritura.

## Borrador

`POST /api/v1/clinical-encounters` crea un borrador único para una cita confirmada, con `appointmentId` y `clientRequestId`. El servidor deriva mascota, propietario y veterinario. La creación devuelve 201 y Location; repetir la misma intención devuelve 200. Reutilizar la clave con otra intención devuelve 409.

`GET /api/v1/clinical-encounters/{encounterId}` y `GET /api/v1/appointments/{appointmentId}/clinical-encounter` consultan el borrador. Su veterinario vigente tiene acceso; el anterior lo pierde tras reasignación. El nuevo asignado puede continuar conservando `createdBy` y autoría de los eventos. `veterinarianId` representa al responsable del guardado más reciente.

`PUT /api/v1/clinical-encounters/{encounterId}` reemplaza `reason`, `anamnesis`, `physicalExamination`, `diagnosis` y `carePlan`. Son opcionales en borrador, con máximo 4000 caracteres cada uno. Un campo omitido, null o blanco se limpia. Requiere `expectedVersion` y `clientRequestId`.

Los propietarios reciben 404 para borradores propios o ajenos; los roles administrativos carecen de permiso clínico. Las respuestas autorizadas usan `Cache-Control: no-store, private`.

## Fórmula y controles

`GET/PUT /api/v1/clinical-encounters/{encounterId}/treatment` mantiene la fórmula separada de los cinco textos clínicos. PUT requiere `clientRequestId`, `expectedVersion`, `prescriptions` y `controls`. Ambas listas son obligatorias y se reemplazan completas. Enviar `[]` elimina los elementos correspondientes. Cada lista admite hasta 50 elementos, con IDs UUID estables y no repetidos dentro de la misma lista; el orden enviado se conserva.

Cada prescripción contiene:

- `id`, `medicationName` (hasta 200 caracteres), `dose`, `doseUnit` (hasta 50) y `administrationRoute` (hasta 100).
- `intervalHours`, entero de 1 a 168; `startsAt` y `endsAt`, fechas con offset explícito, fin posterior al inicio y duración máxima de 366 días.
- `instructions`, opcional, hasta 2000 caracteres; valores blancos se normalizan a null.

`dose` es positiva, con hasta 12 dígitos enteros y 6 decimales; no se redondean dosis silenciosamente. La duración se deriva de inicio y fin para evitar valores contradictorios. Los límites son técnicos y el sistema no calcula dosis ni recomienda tratamientos.

Cada control contiene `id`, `scheduledAt` con offset y `reason` no vacío de hasta 1000 caracteres. No se exige que las fechas del borrador sean futuras. Las fechas se conservan como instantes UTC a precisión de microsegundos; la zona de negocio de los recordatorios es America/Bogota.

## Consistencia y errores

La versión protege el agregado completo: modificar textos o tratamiento incrementa el mismo `version`. Una versión obsoleta devuelve 409 `CONCURRENT_UPDATE`. El replay equivalente se verifica antes de exigir la versión actual y devuelve el estado vigente, sin volver a escribir ni duplicar auditoría. Las claves se separan por veterinario y tipo de operación; cambiar contenido, versión u orden de listas con la misma clave devuelve 409 `CLINICAL_IDEMPOTENCY_CONFLICT`.

Cada escritura bloquea propietario, mascota, veterinario, cita y atención en ese orden y revalida participantes activos y asignación. Reemplazo de hijos, versión, responsable, auditoría e intención idempotente forman una transacción. La base protege relaciones, historia inmutable y mutación de hijos de atenciones finalizadas. La lectura de tratamiento utiliza una instantánea consistente para evitar mezclar una versión con listas de otra escritura.

Los errores usan el Problem Details estándar: 400 validación, 401 autenticación, 403 permisos, 404 recurso no visible y 409 incompatibilidad de estado, versión o intención.

Los recordatorios clínicos se implementan mediante programaciones y tareas durables propias; la validación de entrega se documenta por separado de borrador, finalización e historial.

## Finalización

`POST /api/v1/clinical-encounters/{encounterId}/finalizations` requiere `clientRequestId`, `expectedVersion` y `expectedAppointmentVersion`. Solo el veterinario vigente de la cita confirmada puede finalizar, desde la hora de inicio del turno. Motivo, anamnesis, examen físico, diagnóstico y plan son obligatorios; fórmula y controles son opcionales.

La misma transacción registra FINALIZED, el responsable y la fecha, completa la cita a COMPLETED, incrementa ambas versiones, agrega las auditorías e invalida avisos de cita pendientes. Un fallo revierte todo. Constraints diferidos de PostgreSQL impiden confirmar solo una mitad del resultado. El endpoint administrativo no puede completar ni reabrir citas clínicas.

La atención y sus hijos finalizados son inmutables. Un replay equivalente devuelve el recurso sin nuevas escrituras; una intención diferente requiere otra clave y no puede sobrescribir el finalizado. Las adendas quedan fuera del alcance aprobado. El evento interno de finalización incorpora las programaciones durables dentro del mismo commit.

Los permisos clínicos nuevos requieren volver a iniciar sesión con OWNER/VETERINARIAN tras publicar esta revisión; se conserva la validación estricta de los permisos vigentes de JWT.

## Historia publicada

`GET /api/v1/pets/{petId}/clinical-history` consulta únicamente atenciones FINALIZED, con page desde 0 y size entre 1 y 100. Cada resumen contiene `id`, `appointmentId`, `petId`, `veterinarianId`, `veterinarianFullName`, `reason` y `finalizedAt`. El nombre profesional corresponde al perfil conservado del responsable; el detalle y la fórmula se consultan en los recursos clínicos existentes.

El propietario activo consulta solo las atenciones publicadas de sus mascotas, incluidas las archivadas. Se comprueban tanto propiedad de la mascota como propietario histórico de la atención. Una lista vacía no elimina esa comprobación de acceso. Los borradores permanecen privados y las cuentas administrativas no reciben contenido clínico.

El veterinario activo consulta todos los antecedentes publicados de una mascota mientras tiene una cita CONFIRMED/ASSIGNED actual de esa mascota. Después conserva acceso únicamente a las atenciones que él finalizó, según la decisión explícita del usuario del 25/10. La identidad responsable final del registro inmutable concede esta lectura, no haber creado un borrador. Cancelar o reasignar la cita deja de conceder acceso a finalizados de otros veterinarios; nuevas publicaciones ajenas tampoco amplían ese acceso. Borradores de otras citas siguen privados aunque se pueda leer historia publicada de la misma mascota. Listado, totales, detalle, fórmula y descubrimiento por cita comparten esta política.

El orden es `finalizedAt` descendente e ID descendente como desempate. Permiso, total y filas de una respuesta se leen en la misma instantánea PostgreSQL. La paginación es una **vista viva**: otra finalización entre solicitudes puede desplazar páginas; para recargar el historial, comenzar por page 0. No se garantiza una instantánea conservada entre peticiones. Todas las respuestas usan `Cache-Control: no-store, private`.

El acuse de una finalización autorizada devuelve su resultado dentro de la transacción. La consulta posterior aplica por separado la política de lectura y no convierte un replay en acceso a toda la historia.

## Recordatorios clínicos

La finalización crea una programación compacta por prescripción/control, sin precargar todas las dosis futuras. Los medicamentos usan el inicio, intervalo y fin registrados por el profesional; la ventana de entrega predeterminada es de cinco minutos y nunca supera la próxima dosis ni el fin exclusivo. Al reiniciar se omiten ocurrencias vencidas, sin ráfagas. Los controles se notifican 24 horas antes o inmediatamente al finalizar si falta menos; no se inicia un envío después de la fecha del control.

La cola revalida atención publicada, cita completada, propietario/mascota activos y destinatario vigente antes de reclamar y justo antes de SMTP. No guarda contenido clínico ni dirección de correo en los payloads de tareas. SMTP ocurre fuera de transacción, con lease, intentos y vencimiento persistidos. La garantía es al menos una vez dentro de su ventana: una aceptación SMTP seguida de pérdida de confirmación puede causar un duplicado incierto; no se promete entrega exactamente una vez.

Las ocurrencias suprimidas durante inactividad no se recuperan al reactivar; se consideran únicamente las futuras. Las programaciones anteriores a la instalación no se reconstruyen automáticamente. No se cambian enums ni payloads de citas P12. Los parámetros, decisiones, pruebas y límites están en el [plan de recordatorios](plan-recordatorios-clinicos-2026-10-25.md) y el [plan de sus pruebas](plan-pruebas-recordatorios-2026-10-25.md).
