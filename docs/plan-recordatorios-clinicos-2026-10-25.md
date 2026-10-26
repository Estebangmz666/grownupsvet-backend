# Plan previo de recordatorios clínicos — I6

25 de octubre de 2026, America/Bogota. Este archivo define el incremento antes de implementarlo. No modifica contratos, migraciones, colas ni configuración del backend.

## Alcance confirmado y decisiones operativas previas a implementar

Confirmado por el usuario: canal correo, prescripción con inicio y fin explícitos, ningún envío iniciado después del fin, control con fecha/hora explícita, zona de presentación America/Bogota y ausencia de adendas. La atención y su tratamiento quedan inmutables al finalizar; solo una atención FINALIZED cuya cita está COMPLETED puede originar avisos clínicos. Se conserva la separación respecto de los avisos de citas P12.

El usuario aprobó antes de implementar I6 las siguientes reglas:

1. Control: aviso 24 horas antes; si se finaliza cuando faltan menos de 24 horas, aviso inmediato; si ya llegó la fecha del control, omitirlo.
2. Medicamentos: omitir dosis vencidas y no recuperar en ráfaga las perdidas durante una interrupción. Para absorber el retraso normal del poll se adopta una ventana operativa configurable de cinco minutos, siempre limitada por la siguiente ocurrencia y el fin del tratamiento. Es un límite técnico de emisión, no un criterio médico.

El usuario autorizó resolver las decisiones operativas restantes. Durante una desactivación se suprimen las ocurrencias afectadas; al reactivar solo se evalúan las futuras, sin recuperar anteriores. No se realizará backfill automático de atenciones finalizadas antes de esta migración: P3 todavía no tiene registros desplegados y un backfill futuro requeriría un alcance explícito. El listener solo programa finalizaciones nuevas. No se modifica la historia finalizada.

## Arquitectura y límites

Se incorporarán dos estructuras propias: programaciones y tareas de correo clínicas. No se amplían los enums ni los payloads de `appointment_email_tasks`, recuperación o invitaciones. Las claves existentes conservan su significado para procesos de versiones anteriores.

La finalización ya publica `ClinicalEncounterFinalizedEvent(encounterId)` dentro de su transacción. Un listener `@TransactionalEventListener(phase = BEFORE_COMMIT)` escribirá las programaciones con propagación MANDATORY mediante `TransactionTemplate`: Spring 7 restringe la combinación directa de `@Transactional` con listeners transaccionales. No utilizará `fallbackExecution`, AFTER_COMMIT ni REQUIRES_NEW para esa escritura: un fallo debe revertir atención, cita, eventos y programaciones juntos. Un evento repetido no crea programaciones duplicadas gracias a la unicidad de origen. Referencia: [factory oficial de listeners transaccionales](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/RestrictedTransactionalEventListenerFactory.html).

Un procesador materializa únicamente trabajo próximo o vencido aún entregable. Otro procesador reclama una tarea mediante lease, confirma la transacción y realiza SMTP fuera de ella. La base conserva las intenciones; el executor en memoria solo permite despertarlas antes del próximo poll. Reiniciar la aplicación o rechazar un envío al executor no elimina la programación durable.

No se precreará una fila por dosis futura ni una fila por cada omisión histórica. Una prescripción de 366 días cada hora puede producir miles de ocurrencias; el almacenamiento inicial será una programación por prescripción/control, un cursor y contadores de omisión. Los cálculos para saltar períodos serán aritméticos, sin un bucle por cada dosis pasada.

## Modelo de datos propuesto

La siguiente migración libre, previsiblemente V22, creará las tablas después de comprobar el número real de migraciones al iniciar I6. No se editarán migraciones ya verificadas.

`clinical_reminder_schedules`:

- Identidad UUID, encounter_id, source_type PRESCRIPTION/CONTROL y source_id. Unicidad `(encounter_id, source_type, source_id)`.
- FK compuesta al origen clínico correspondiente, mediante columnas específicas anulables y un CHECK de forma, para impedir programaciones que apunten a otra atención. La migración elegirá una forma explícita; no quedará una referencia polimórfica sin integridad.
- Inicio/fin o fecha de control, intervalo para prescripción, `next_due_at`, índice de próxima ocurrencia, fecha de creación y estado ACTIVE/FINISHED/SUPERSEDED.
- Contadores de ocurrencias omitidas y última fecha omitida, sin copiar texto clínico ni correos en payloads.
- Índice parcial `(next_due_at, id)` para ACTIVE; checks de intervalo, fechas, origen y cursor no negativo. Datos temporales UTC, zona de negocio fija America/Bogota.

`clinical_reminder_email_tasks`:

- UUID, schedule_id, scheduled_at, expires_at, recipient_id, estado PENDING/SENDING/SENT/FAILED/SUPERSEDED/EXPIRED, attempt_count, next_attempt_at, lease_token, lease_until, created_at y sent_at.
- Unicidad `(schedule_id, scheduled_at)`: reclamar, reiniciar o repetir el materializador no crea otra tarea para la misma ocurrencia.
- CHECK de lease: exclusivamente SENDING exige token y vencimiento. Índice parcial por próxima tentativa para PENDING/SENDING. Fechas de ocurrencia y de reintento son campos diferentes.
- Código de error técnico limitado y permitido; no persistir excepciones SMTP completas, credenciales, tokens ni copias del contenido clínico.
- La dirección vigente se resolverá al reclamar; se llevará como snapshot efímera junto con el lease y se comparará otra vez antes de SMTP. Si se decide persistir una dirección por auditoría, se justificará su necesidad y protección antes de hacerlo.

La programación registra datos temporales derivados de un tratamiento finalizado inmutable; el contenido del mensaje se obtiene de sus relaciones originales. No se permite cambiar su origen ni adjuntar posteriormente una prescripción de otra atención.

## Secuencia de implementación

1. Cerrar las reglas pendientes y añadirlas al plan de cierre principal. Congelar contratos y nombres de estados/códigos. I6 no necesita un endpoint nuevo para programar: la finalización es su única entrada de negocio.
2. Escribir la migración nueva con FK, checks, índices y unicidad. Probar una actualización desde V21 con datos clínicos existentes; no realizar backfill automático de correos anteriores sin decidir primero qué hacer con tratamientos finalizados antes de I6.
3. Crear `ClinicalReminderProperties` con validación de configuración: poll, tamaño máximo del lote, capacidad de cola, lease, tentativas y backoff. Reutilizar `MAIL_FROM`, el `JavaMailSender` y los timeouts SMTP existentes; no duplicar credenciales.
4. Implementar `ClinicalReminderSchedulingService`: listener BEFORE_COMMIT, lectura de prescripciones/controles inmutables y creación idempotente de programaciones. Máximo inicial: una fila por elemento, actualmente 50 prescripciones y 50 controles por atención.
5. Implementar `ClinicalReminderMaterializer`: transacciones breves y lotes limitados; reclamar programaciones con `FOR UPDATE SKIP LOCKED`, insertar como máximo una ocurrencia vigente por programación y actualizar su cursor en la misma transacción. Usar `ON CONFLICT` sobre su clave natural, sin esconder fallos de FK o estado.
6. Implementar `ClinicalReminderEmailDispatcher`: claim transaccional mediante token nuevo y lease, envío SMTP sin transacción y confirmación condicional por token. Un worker antiguo no puede confirmar, fallar o reactivar una tarea que ya posee otro lease.
7. Incorporar mantenimiento acotado de tareas vencidas, agotadas e inelegibles. No escanear ni bloquear toda la tabla en un poll. Los índices deben corresponder a las consultas realmente utilizadas.
8. Añadir presentación de correo usando `TransactionalEmailTemplates` y su escape HTML. Reutilizar su armazón, alternativa de texto y enlaces validados; no construir HTML mediante concatenación de datos clínicos sin escapar. Mantener asuntos genéricos sin diagnóstico o contenido clínico innecesario.
9. Incorporar pruebas durables y de transporte, repetir regresión P12/recuperación/invitaciones y actualizar documentación, expectativas de migración y contrato únicamente cuando cambien de verdad.

## Cálculo temporal y expiración

Prescripción: la ocurrencia n corresponde a `startsAt + n × intervalHours`, con n entero no negativo y fecha estrictamente anterior a endsAt. `endsAt` es un límite exclusivo. No se genera una dosis adicional exactamente al finalizar. Las fórmulas de avance deben evitar desbordamientos y respetar la precisión de microsegundos ya usada por el módulo.

Al finalizar y al reiniciar se elegirá el primer índice futuro o todavía entregable según la regla de tolerancia aprobada. Si existen muchas dosis omitidas, se avanzará directamente a ese índice y se acumulará el número omitido. No se enviará un resumen que sugiera administrar dosis acumuladas ni se alterará la prescripción.

`expires_at` para medicamento será el mínimo entre el límite de tolerancia aprobado, la siguiente ocurrencia y endsAt. Para control será, como máximo, scheduledAt. Si `now >= expires_at`, la tarea se marca EXPIRED; si `now >= endsAt`, ningún claim ni reintento de prescripción podrá iniciar un envío.

Una tarea SENDING con lease vigente no se expira desde mantenimiento mientras puede estar dentro de SMTP: se impide iniciar un envío fuera de la ventana y se permite registrar su aceptación posterior si comenzó a tiempo y aún conserva el lease. `missed_count` contabiliza únicamente ocurrencias omitidas durante la materialización; las tareas ya creadas que caduquen o agoten tentativas se reflejan en EXPIRED/FAILED, sin duplicar ese contador.

La zona America/Bogota se aplica al texto y a la interpretación de la regla de control acordada. La recurrencia de horas se calcula sobre Instant/Duration; no depende de la zona local del servidor. No se convierte una hora de medicamentos en una recomendación médica.

## Revalidación y privacidad

Antes de crear trabajo y nuevamente al reclamar se verificará la elegibilidad. Inmediatamente antes de SMTP se repetirá la lectura que comprueba:

- Atención FINALIZED y cita asociada COMPLETED.
- Origen presente e inmutable, con vínculo a la misma atención.
- Propietario actual ACTIVE, rol OWNER, mascota activa y relaciones mascota/propietario coincidentes con la atención.
- Destinatario y dirección iguales a la identidad vigente que autoriza el mensaje.
- Lease/token aún propios y no vencidos; tarea no superada ni expirada; ocurrencia dentro de su ventana temporal y prescripción antes de endsAt.

Un cambio que vuelva obsoleta la tarea la marca SUPERSEDED/EXPIRED y anula su lease. No se informa al cliente de errores privados del proveedor ni se registran texto clínico, direcciones, tokens o payloads en logs. Los identificadores operativos de tarea y códigos técnicos bastan para diagnosticar.

## Concurrencia, recuperación y garantía real

El materializador no mantendrá locks durante el claim del dispatcher ni durante SMTP. Los listeners de finalización solo toman locks de las nuevas filas bajo el orden existente; el trabajador no debe bloquear programaciones y luego intentar adquirir locks de propietario/cita en orden inverso. La revalidación será lectura; si una transición requiere mutación de varios recursos, se explicitará y probará su orden de bloqueo.

Un lease vencido permite que otro worker recupere la tarea; cada actualización de resultado exige estado SENDING y el mismo token. Los reintentos tienen backoff y máximo de intentos y nunca amplían expires_at. La falta de configuración SMTP pospone el trabajo durable sin consumir tentativas inútiles, pero no prolonga su vigencia.

SMTP ofrece entrega **al menos una vez**, con posibilidad excepcional de duplicado incierto: si el proveedor acepta un mensaje y el proceso cae antes de guardar SENT, el siguiente worker no puede saber que fue aceptado. El token impide confirmar con un lease obsoleto; no puede retirar un correo ya aceptado. Se comprobará elegibilidad justo antes del envío y se documentará esa pequeña carrera externa sin prometer exactamente una vez ni ausencia absoluta de mensajes después de una transición concurrente.

## Matriz de verificación

Usar PostgreSQL real en esquemas UUID aislados, reloj mutable, transacciones independientes y workers reconstruidos. Las pruebas de concurrencia tendrán barreras y timeouts, sin sleeps arbitrarios ni procesos SMTP reales de usuarios.

| Escenario | Evidencia necesaria |
| --- | --- |
| Finalización y programaciones | Crea una por origen en el mismo commit; replay no duplica; fallo en el listener revierte atención/cita/eventos/programaciones. |
| Inicio, intervalo y fin | Ningún aviso antes de inicio; ocurrencias exactas; límite exclusivo; cero envíos al llegar o superar el fin. |
| Controles | Regla aprobada de 24 horas/inmediato; ninguno después de scheduledAt; fechas UTC equivalentes y presentación Bogotá. |
| Interrupción prolongada | Reiniciar materializador/worker desde BD; salto aritmético sin ráfaga; omisiones registradas y siguiente ocurrencia correcta. |
| Cota de crecimiento | Tratamiento de 366 días cada hora crea una programación inicial; un poll materializa trabajo limitado, sin precrear miles de tareas. |
| Repetición y competencia | Dos materializadores no duplican la misma ocurrencia; dos workers no reclaman a la vez el mismo lease vigente. |
| Lease obsoleto | Worker nuevo recupera vencido; resultado tardío del anterior no marca SENT/FAILED ni reabre la tarea. |
| SMTP y backoff | Error transitorio reintenta dentro de ventana; agotamiento conserva código técnico; vencimiento detiene reintentos. |
| Obsolescencia | Desactivar propietario/mascota o cambiar la dirección antes del envío impide usar un snapshot obsoleto. Revalidar también entre construir el mensaje y llamar al transporte. |
| Transacciones | El transporte comprueba que no hay transacción activa durante SMTP. Un fallo tardío de finalización/programación deja cero efectos parciales. |
| Contenido | Texto y HTML contienen los mismos datos autorizados; nombres e instrucciones con `<`, `&`, comillas o saltos quedan escapados. |
| Compatibilidad | P12, recuperación e invitaciones conservan enums, payloads y comportamiento; migración completa y actualización desde V21 pasan. |

Para el transporte se preferirá un servidor SMTP local de prueba que capture el MIME real; las pruebas de coordinación pueden usar un sender controlado. Ninguna de estas pruebas demuestra recepción en Gmail real. La entrega desplegada requiere su revisión, configuración y un recorrido de correo acordado, conservando evidencia sanitizada.

## Criterio de cierre y orden de trabajo

Primero se revisan respuestas pendientes y se consolida este plan; después se implementan migración y programación atómica; luego materialización, leases/transporte y pruebas. La suite de regresión y la verificación de despliegue se realizan después de congelar código y migraciones, evitando corridas superpuestas sobre el mismo esquema.

I6 se considera implementado localmente cuando la matriz relevante pasa, las reglas temporales están acordadas, no existen escrituras parciales ni duplicaciones de intención y la evidencia distingue PostgreSQL/local SMTP de aceptación SMTP o recepción del entorno desplegado. Publicar el código por sí solo no acredita recordatorios en producción.
