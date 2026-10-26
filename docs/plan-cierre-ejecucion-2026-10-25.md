# Ejecución del cierre del backend

Fecha: 25 de octubre de 2026, America/Bogota.

Referencia: `output/plan-cierre-backend-p3-2026-10-23/plan-cierre-backend-p3.md` en el directorio padre del repositorio. Se conservan I0–I8 y la exclusión de Jenkins. Cada incremento debe tener su plan escrito antes de modificar su implementación y su resultado comprobado después.

## Punto de partida

- HEAD inicial: `432766397468242e8e837392340b8cec3e483f7b`.
- Existen 33 rutas con cambios locales anteriores. Se preservan y se revisan sin descartarlas ni atribuirlas al trabajo nuevo.
- Java 25, Spring Boot 4.1.1, PostgreSQL 18, Flyway V1–V18 y contrato OpenAPI 0.9.0.
- El módulo clínico todavía no existe. Las decisiones materiales de I1 se consultan antes de implementar el bloque afectado.
- Las métricas de aceptación corresponden al backend desplegado. Las pruebas locales no se presentan como evidencia de producción.

## I0a — Base reproducible y controles de integración

### Objetivo

Establecer el estado real de las pruebas y proporcionar un flujo reproducible que compile, ejecute las pruebas con PostgreSQL y valide el contrato generado y sus respuestas HTTP.

### Pasos previos a la implementación

1. Conservar inventario y copia de los cambios anteriores fuera del repositorio. Identificar configuración y credenciales por presencia, sin registrar valores.
2. Comprobar Java/Maven y PostgreSQL local. Forzar exclusivamente `jdbc:postgresql://localhost:5432/grownupsvet_test` para las pruebas; no usar la URL de despliegue heredada del entorno.
3. Ejecutar la suite existente y el validador OpenAPI para establecer una línea base. Corregir fallos atribuibles al código o a los controles nuevos y documentar cualquier limitación del entorno.
4. Revisar los scripts y fixtures existentes antes de definir el flujo CI. Conservar los mecanismos de aislamiento y limpieza de esquemas de las pruebas.

### Implementación prevista

- Añadir GitHub Actions para push y pull request con PostgreSQL 18, Java 25 y Python, permisos mínimos y cancelación de ejecuciones obsoletas.
- Usar Maven Wrapper y versiones de dependencias ya declaradas; no incorporar Jenkins.
- Ejecutar `verify`, validar `target/generated-openapi/openapi.json` y ejemplos HTTP con `scripts/validate_generated_openapi.py`.
- Conservar reportes de pruebas y contrato como artefactos de diagnóstico, incluso ante fallos. No publicar secretos, logs SMTP ni bases de datos.
- Documentar el nombre exacto del check requerido y la configuración de protección de rama necesaria. El archivo de workflow por sí solo no prueba bloqueo efectivo remoto.
- Añadir una comprobación reproducible de fallo del validador sobre una copia temporal alterada del contrato, sin cambiar el contrato de entrega.

### Criterios de aceptación

- Suite local verde sobre PostgreSQL real y contrato generado validado.
- Un contrato deliberadamente inválido produce salida distinta de cero.
- El workflow usa los mismos comandos comprobados y no omite pruebas ni tolera fallos.
- Diferencias revisables sin pérdida de los cambios previos.
- Publicación, protección remota, despliegue y recepción real de correo se acreditan por separado; no se marcan completos con evidencia local.

## I1 — Contexto y decisiones clínicas (investigación en paralelo)

Revisar los requisitos clínicos de Jira y los documentos de arquitectura, los consumidores y los patrones reales de citas, permisos, errores, concurrencia, auditoría y colas. Resolver con el usuario los campos obligatorios, acceso del veterinario/administrador, posibilidad de atención sin cita, finalización y reglas de recordatorios que las fuentes no definan. Después se escribirá el contrato y el plan de I2 antes de implementar.

## I1 — Decisiones aprobadas y contrato del borrador

El usuario aprobó el 25/10: atención únicamente desde cita CONFIRMED; veterinario asignado escribe y finaliza; antecedentes publicados de mascotas asignadas; propietario solo historia publicada propia; administración sin contenido clínico. Borrador anticipado permitido; finalización desde el inicio del turno. Campos finales obligatorios: motivo, anamnesis, examen físico, diagnóstico y plan/recomendaciones. Medicamentos y controles opcionales. Correo, inicio explícito y fin obligatorio para avisos, zona America/Bogota. Sin adendas.

### Contrato I2, establecido antes de implementar

- `POST /api/v1/clinical-encounters`: `clientRequestId`, `appointmentId`; crea un único borrador vacío por cita. 201 con Location; replay equivalente 200; clave reutilizada con otra intención 409.
- `GET /api/v1/clinical-encounters/{encounterId}`: inicialmente lectura del borrador por su veterinario asignado. I5 ampliará exclusivamente la lectura de finalizados según las reglas aprobadas. Recurso inexistente/ajeno 404; rol sin permiso 403.
- `GET /api/v1/appointments/{appointmentId}/clinical-encounter`: descubre la atención de una cita sin necesitar conocer previamente su ID; aplica exactamente las mismas reglas de lectura, incluido 404 para borradores no visibles.
- `PUT /api/v1/clinical-encounters/{encounterId}`: reemplaza campos del borrador (`reason`, `anamnesis`, `physicalExamination`, `diagnosis`, `carePlan`), con `expectedVersion` y `clientRequestId`. Texto opcional hasta 4000 caracteres por campo; valores blancos normalizados a null. Campos omitidos se limpian, según semántica de reemplazo. No modifica relaciones.
- Respuesta: id, appointmentId, petId, ownerId, veterinarianId, createdBy, status DRAFT/FINALIZED, version, los cinco campos, createdAt, updatedAt y finalizedAt nullable. Nunca correo o información de otras mascotas. Cache-Control privado/no-store.
- Errores mediante el Problem Details existente: 400 validación, 401 autenticación, 403 permiso, 404 recurso no visible, 409 estado/versión/idempotencia.

## I2 — Borrador autorizado, durable y auditable

1. Nueva V19, sin editar V1–V18: encounter con FK a cita/mascota/propietario/veterinario, unicidad por cita, versión, estado y límites de texto; eventos inmutables y operaciones idempotentes persistidas.
2. Seguir JDBC/transacciones del módulo de citas. Orden de bloqueo: propietario, mascota, veterinario, cita, atención. Revalidar cuenta activa, rol, CONFIRMED, ASSIGNED y veterinario vigente en cada escritura.
3. Crear sin aceptar IDs clínicos vinculantes del cliente. Resolver mascota y propietario desde la cita bloqueada. Guardado requiere versión exacta, incrementa versión y persiste auditoría sin duplicados. Repetir la misma clave/intención no vuelve a escribir.
4. Autorización de recurso en servicio además de permisos del controlador. Veterinario distinto, OWNER y ADMINISTRATOR no pueden ver borradores. Reasignación impide al autor anterior continuar editando.
   El usuario aprobó continuidad tras reasignación: nuevo asignado puede continuar el borrador, conservando `createdBy` y autoría de eventos; `veterinarianId` identifica al responsable del guardado/finalización más reciente.
5. Pruebas HTTP reales con PostgreSQL: creación/replay, guardado/replay, clave alterada, versión obsoleta, otra cita/otro veterinario/roles ajenos, citas no confirmadas y participantes inactivos, campos inválidos y persistencia/auditoría.
6. Actualizar expectativa de cantidad de migraciones del test de arranque, preservando comprobación de huellas de migraciones anteriores. Evolucionar OpenAPI a 0.10.0 y regenerarlo desde HTTP tras probar el incremento.

## I3 — Fórmula y controles estructurados

Plan previo a implementación, tras validar I2:

1. Mantener el texto clínico separado de la fórmula. Recurso `GET/PUT /api/v1/clinical-encounters/{encounterId}/treatment`, con las mismas reglas de lectura/escritura del borrador.
2. PUT reemplaza listas completas con `expectedVersion`, `clientRequestId`, `prescriptions` y `controls`; no cambia los cinco textos clínicos. Un único número de versión protege el agregado entero. El replay compara toda la intención, incluidas listas y versión, y no duplica eventos.
3. Cada prescripción tiene ID UUID estable proporcionado por cliente, medicamento, dosis decimal positiva, unidad, vía, intervalo entero de horas, inicio explícito y fin obligatorio posterior al inicio, e instrucciones opcionales. La duración se expresa mediante inicio/fin; no duplicar campos de duración contradictorios. Unidades y vías son texto clínico validado, sin inventar catálogos farmacológicos.
4. Cada control tiene ID UUID, fecha/hora explícita y motivo. Límites de protección: 50 elementos por lista, texto acotado, intervalo 1–168 h y ventana de tratamiento hasta 366 días. Estos límites son técnicos, no recomendaciones médicas; el sistema no calcula dosis.
5. V20 crea tablas hijas con FK y restricciones; no editar V19 ya verificada. Bloquear primero la atención autorizada; modificar hijos y aumentar versión/auditoría en una transacción. DB impide mutar hijos de atención finalizada. Reutilizar autorización y bloqueo mediante helpers cohesivos del módulo, evitando duplicar reglas.
6. HTTP/PostgreSQL: ida/vuelta con decimales/fechas, borrado deliberado por listas vacías, invalidaciones, IDs duplicados, claves con otra intención, acceso ajeno, versión obsoleta y rollback ante fallo de hijo/auditoría. Conservar ejemplos HTTP y ampliar el validador de contrato.

## I4 — Finalización clínica y cita en una transacción

Plan previo a implementación, tras validar I3:

1. `POST /api/v1/clinical-encounters/{encounterId}/finalizations`, con `clientRequestId`, `expectedVersion` y `expectedAppointmentVersion`. Respuesta de atención finalizada. No aceptar estado clínico en el endpoint administrativo de citas.
2. Reutilizar orden de bloqueos del borrador. Solo veterinario vigente, participantes activos, cita CONFIRMED/ASSIGNED, desde startsAt. Exigir los cinco textos no vacíos y validar el agregado de tratamiento persistido. Retornar 409 para estado/versiones incompatibles y 400 para contenido incompleto.
3. Guardar responsable final, FINALIZED, finalizedAt, versión y evento clínico; cambiar cita a COMPLETED con versión/evento administrativo de trazabilidad en la misma transacción. Ante cualquier fallo, revertir atención, cita, eventos y programación clínica.
4. Nueva migración para admitir COMPLETED y evento correspondiente sin modificar migraciones aplicadas; preservar las restricciones y transiciones anteriores. Impedir SQL destructivo sobre atención/hijos finalizados. Las operaciones administrativas no reabren una cita COMPLETED.
5. Idempotencia durable por actor, tipo y clientRequestId. Replay equivalente devuelve el recurso actual, sin otro evento ni correo; misma clave con intención distinta da 409. Supresión de avisos de cita obsoletos al completar, sin alterar enums ni payloads persistidos P12.
6. Pruebas de finalización válida, anticipada, incompleta, ajena y con versiones obsoletas; replay, competencia entre finalizaciones/escrituras y fallo tardío que demuestra rollback total con conexiones reales. La inmutabilidad se verifica también directamente en PostgreSQL usando savepoints/esquemas aislados.
7. Defensa de BD: constraints diferidos exigen que FINALIZED y COMPLETED se confirmen juntos; preflight de migración rechaza datos previos incoherentes. Los eventos COMPLETED exigen valores no nulos. El evento Java de finalización se publica dentro de la transacción para incorporar programación durable en I6.

### Compatibilidad de sesiones

P3 agrega permisos OWNER/VETERINARIAN. El validador JWT existente exige que los permisos del token coincidan con los vigentes: las sesiones anteriores de esos roles deberán iniciar sesión de nuevo al publicar esta revisión. No se relaja esa validación ni se cambia la duración de sesión.

## I5 — Historia publicada y lectura autorizada

Plan previo a implementación, tras validar I4:

1. `GET /api/v1/pets/{petId}/clinical-history`, paginado (page>=0, size 1–100), orden finalizedAt descendente e ID como desempate. Solo FINALIZED; índices ya previstos por mascota/fecha.
2. Propietario ACTIVE accede a mascotas propias, incluso si una mascota está archivada; no cambia la propiedad histórica. Veterinario ACTIVE consulta todos los antecedentes publicados de una mascota mientras tiene una cita CONFIRMED/ASSIGNED vigente; después conserva únicamente las atenciones que él finalizó. Esta retención acotada fue aprobada explícitamente por el usuario el 25/10. Administración y superadministración no tienen lectura clínica.
3. Aplicar idéntica autorización de finalizados a detalle, tratamiento y búsqueda por cita. Para acceso ajeno devolver 404 sin metadatos; roles sin permiso 403. Una asignación cancelada/rechazada o reasignada deja de conceder nuevos accesos.
4. Pruebas con dos propietarios, varios veterinarios, reasignación/cancelación, mascota archivada, borradores mezclados, lista vacía, límites y paginación estable. Capturar ejemplos HTTP y actualizar OpenAPI generado.

## I7a — Preparación de observabilidad (independiente de clínica)

Antes de implementar: incorporar registro Prometheus bajo BOM existente y perfil opt-in `observability`, gestión solo en loopback/puerto 9091; nunca publicar métricas en el puerto de la API. Añadir histogramas HTTP con umbral 500ms y etiquetas constantes de aplicación/revisión, sin datos personales. Preservar probes con `/livez` y `/readyz` en el puerto público al activar el perfil. Verificar mediante servidor HTTP real, dos puertos y PostgreSQL local: scrape interno permitido sin JWT, scrape público denegado y probes accesibles sin revelar detalles. Preparar configuración de recolección Cloud Run y consultas/panel Grafana. La recolección, IAM, costes, despliegue y evidencia M1–M9 siguen pendientes hasta verificarlos en el entorno desplegado.

## Resultado

### I8a — Compatibilidad del generador de demostración

Plan previo al ajuste: la primera regresión completa detectó que `create-demo-data.ps1` exige literalmente 0.9.0. Sus rutas y payloads P1/P2 no cambian en 0.10.0; se revisó el preflight antes de modificarlo. Admitir explícitamente esas dos versiones conocidas con una aserción de nombre claro, sin aceptar versiones futuras desconocidas ni omitir precondiciones. Actualizar su documentación; ejecutar `DemoDataScriptHttpIntegrationTests` sobre servidor HTTP/PostgreSQL reales y repetir la regresión completa. No producir datos de demostración en producción ni modificar cuentas existentes.

- I2/I7a: primera validación focalizada con 99 pruebas, cero fallos, errores u omisiones. Incluye 24 escenarios HTTP clínicos, 7 pruebas propias de observabilidad y regresión de arranque, permisos, probes y contrato. Log: `output/backend-cierre-2026-10-25/i2-observability-focused.log` en el directorio padre.
- OpenAPI 0.10.0 de I2: documento 3.1 válido y 45 ejemplos HTTP verificados; controles negativos de CI aprobados. Snapshot regenerado desde HTTP. I3 ampliará ambos.
- I0a: workflow con acciones oficiales fijadas a SHA, PostgreSQL efímero y gates; actionlint sin diagnósticos. Pendientes publicación y ejecución remota/protección de rama.
- Las primeras corridas no son evidencia de aceptación: un override de datasource interfería con pruebas de configuración y una corrida coincidió con la adición de V19 mientras el test de huellas aún esperaba 18. La validación focalizada definitiva usó código/migraciones congelados y expectativas actualizadas.
- I3: 94 pruebas focalizadas aprobadas, incluidas 16 transaccionales con conexiones independientes para carreras y rollback de hijos/auditoría. Contrato validado con 46 ejemplos HTTP.
- I4: 167 pruebas focalizadas aprobadas, incluidas 25 transaccionales clínicas y regresión de citas/correo. Verificados commit consistente, replay concurrente, escrituras competitivas, dos fallos SQL tardíos, constraints al commit e inmutabilidad. Contrato validado con 48 ejemplos HTTP. Log `output/backend-cierre-2026-10-25/i4-focused.log`.
- I5: implementación de OWNER propio y veterinario con asignación CONFIRMED/ASSIGNED; en validación. El usuario aprobó conservar después únicamente lectura de atenciones que el veterinario finalizó. Esa política ya se comparte entre historial, detalle, tratamiento y búsqueda por cita; se prueba que ser creador de un borrador reasignado no concede acceso posterior.
- I6: plan previo y aprobaciones en `plan-recordatorios-clinicos-2026-10-25.md`; código congelado para validación. El usuario aprobó control 24 horas antes (o inmediato si falta menos) y omitir dosis vencidas. Las decisiones operativas —gracia de cinco minutos, leases y procesamiento acotado— se resolvieron técnicamente, sin nuevas consultas.
- I7a.1: contadores de siete escrituras efectivas después del commit, sin datos personales ni duplicación por replay. No representan porcentajes académicos ni sustituyen el oráculo de persistencia.
