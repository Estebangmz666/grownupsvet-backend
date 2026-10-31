# Plan previo: aceptación desplegada de R7 con el segundo borrador ficticio

> Plan histórico previo a la implementación. Su ejecución terminó el 31/10; el resultado y sus límites constan en la [aceptación desplegada R7](aceptacion-backend-r7-desplegado-2026-10-31.md). El texto siguiente conserva las condiciones y barreras planteadas antes de ejecutar.

31/10/2026. Solo planificación; no autoriza implementar ni ejecutar el nuevo adaptador. No modificar el harness/checkpoint padre28 ni publicar otra imagen antes de terminar su corte posterior al fin de medicación. La ventana fa2 comunicada por root tiene dosis21:44:41Z, fin21:54:41Z y control22:19:41Z; el corte debe ocurrir después de21:54:56Z.

## Objetivo y separación de evidencia

Comprobar en la imagen R7 las escrituras y lecturas clínicas, autorización, finalización, idempotencia y recordatorios. Registrar tiempos reales y sus incumplimientos; no repetir hasta lograr500ms ni introducir refactors de latencia. Cloud Run mide latencia de solicitud de plataforma; cliente E2E y métricas de aplicación siguen siendo medidas distintas. N pequeño no acredita SLO poblacional.

El recurso elegido ya existe: segundo escenario `newScenario` del run privado `backend-performance-2026-10-28`, cita29/10/2026 07:00 America/Bogota, veterinario1, mascota ficticia distinta de la atención finalizada por veterinario2. Su última evidencia guardada acredita CONFIRMEDv1 y DRAFTv0. La confirmación ya figura SENT con un intento; se volverán a leer solo metadatos actuales. No se crea otra cita, mascota, actor o aviso de confirmación.

## Herencia explícita e identidad nueva

1. Esperar a que root cierre y archive la observación final de fa2. Solo entonces fijar SHA256 del checkpoint y reporte padre definitivos: todavía están avanzando durante las observaciones. El nuevo adaptador leerá esos archivos por SHA, nunca los escribirá.
2. Crear posteriormente código privado en `output/backend-aceptacion-r7-2026-10-31/acceptance` y un run independiente en `%USERPROFILE%\.grownupsvet\acceptance\backend-performance-r7-2026-10-31`. Reusar el ledger global existente `performance-control`; no reiniciar presupuestos de login.
3. Guardar lineage: run/revisión/SHA del padre, selector exacto `newScenario`, origen de cada ID técnico y versiones esperadas0/1. Vincular appointment/pet/encounter/veterinario de ese escenario contra sus operaciones originales y actores. No tomar `existing`, que pertenece a la atención finalizada por veterinario2.
4. El checkpoint nuevo comienza con operaciones, intentos HTTP, checks y tasas vacíos. Importa únicamente identidad de actores y recurso, no solicitudes, JWT, muestras, contadores ni claves idempotentes del padre. La creación del borrador sigue acreditada históricamente a fa2 y tiene N=0 como creación R7.
5. Root aportará manifest sanitizado, SHA y pines reales de revisión nueva/digest/commit de la imagen R7. Deben diferir de fa2 y corresponder al código revisado/CI de PR9; no inventar el digest a partir del merge. Mantener proyecto, servicio/región, contrato0.11.0/47rutas y V23. Verificar contrato completo salvo servers y perfil de monitoreo protegido. Guardar hashes de todas las fuentes nuevas y del padre importado.

## Adaptación mínima recomendada

Copiar con UTF8 explícito el cliente privado pequeño, guardas de ficheros y launcher revisados; cambiar solo inicialización/lineage/identidad y rutas nuevas. Mantener transporte request/db/login, intenciones durables y reporte. Continuar importando el transporte histórico revisado por SHA sin constructor/main/pycache, como ya hace el cliente pequeño.

Crear un workflow corto para este recurso heredado. Reutilizar de forma explícita los oráculos clínicos existentes, normalización de hijos, auditoría por clave y validadores de roles; no heredar el Workflow anterior con sus fases Sample/Prepare ni sus alias fijos de veterinario2. No copiar el workflow completo para editarlo por reemplazos globales. El mapeo de caso debe declarar veterinario1 como responsable/creador y veterinario2 como ajeno.

Conservar ACL usuario/SYSTEM, rechazo de reparse/leaf, temporales exclusivos, mutex global/run y escritura atómica. DPAPI únicamente después de validaciones del launcher; passwords por STDIN/memoria, JWT solo RAM. Sin claves/correos/payloads SMTP en logs o reporte sanitizado. Ninguna nueva dependencia, endpoint o infraestructura.

## Fases y barreras

### Preflight y Reconcile, sin escrituras de negocio

- Comprobar pines, salud/warmup por separado, contrato, revisión de métricas, máquina200/anónimo401/humano401/hash401 y token máquina sin autoridad de API.
- Verificar BD solamente con helper SELECT READ ONLY/ROLLBACK, TLS verify-full/raíces confiadas y reloj cercano al cliente. Sin SQL writes, semillas, OTP, cambios de contraseña, estado de personal o reloj artificial.
- Obtener tokens nuevos con cuentas existentes y presupuesto global5intentos/15min. Respetar Retry-After y no reintentar429 o credencial inválida.
- Leer por IDs exactos la cita y el encuentro; exigir CONFIRMED/ASSIGNEDv1, DRAFTv0, dueño/mascota/veterinario activos, creador veterinario1, destinatario autorizado, cinco textos nulos, listas vacías, un evento/operación de creación y cero programaciones. Comparar contra lineage, no solo coherencia interna.
- Leer tratamiento vacío y descubrimiento por cita. Dueño no ve borrador; veterinario asignado sí; ajenos y roles administrativos conservan sus404/403. Leer metadatos de la confirmación propia existente, sin reenviarla.
- Si cambió cualquier precondición, detener para revisión; no resetear el checkpoint, buscar otro recurso o actualizar versiones para forzar éxito.

### Mutate: tres intenciones nuevas y sus replays

1. Guardar antes del PUT una clave nueva y expectedVersion0. Escribir los cinco textos completos en Unicode correcto, inequívocamente ficticios y no asistenciales. Exigir versión1, relaciones/creador intactos y exactamente un evento/operación nuevos. Replay con el mismo cuerpo conserva versión y auditoría.
2. Preparar y guardar otra intención con expectedVersion1 y exactamente una receta ficticia y un control. Ancla en reloj real en ese momento: startsAt=+10min, endsAt=+20min, intervalo1h, control=+45min. La ventana admite exactamente una dosis. Guardado esperado versión2; comprobar persistencia/tipos/orden/Unicode, dos hijos y todavía cero programaciones. Replay no modifica nada.
3. La cita del29 puede finalizarse el31: el servicio exige haber alcanzado el inicio, no un plazo máximo posterior al turno. Revalidar cita/actores/destinatario y ambas versiones; guardar nueva intención antes del POST, expectedEncounterVersion2 y expectedAppointmentVersion1. Exigir FINALIZEDv3/COMPLETEDv2 atómicos, creador intacto, responsable veterinario1, cuatro eventos/operaciones acumulados y dos programaciones durables. Replay final conserva todo.

No ejecutar si startsAt de la intención nueva ya venció. Si la fase se interrumpe y la ventana guardada deja de ser válida, detener sin extender/reemplazar timestamps ni claves. Una respuesta incierta conserva UNKNOWN/intención y requiere conciliación read-only; nunca repetir a ciegas. Respuesta recibida pero oráculo fallido se revalida sin emitir otro write. Reanudación de intención verificada no duplica el replay deliberado.

### Lecturas publicadas y recordatorios

- Dueño y veterinario responsable leen detalle, tratamiento, descubrimiento e historia; dueño ajeno/veterinario2/administradores conservan restricciones. Verificar historia por ID exacto y ausencia de borradores, además del oráculo BD.
- Leer metadatos clínicos inmediatamente para el control, alrededor de startsAt+15–30s para la dosis y después de endsAt+15s para el cierre. No bucles largos, worker manual, alarmas ni SMTP directo. Root mantiene mínimo1 durante aproximadamente20min20s desde el ancla y coordina el monitoreo.
- Máximo dos nuevos avisos clínicos, al mismo destinatario Gmail previamente autorizado: control inmediato por estar a menos de24h y una dosis futura. Verificar origen exacto, destinatario booleano, TTL, intentos y errorCode whitelist; al final, dos cursores FINISHED y ninguna dosis nueva tras el fin. No recuperar dosis vencidas.
- SENT acredita aceptación SMTP; recepción/contenido se registran aparte como USER_REPORTED cuando proceda. No acreditar recepción o Unicode del mensaje entregado solo por inspeccionar la plantilla o la respuesta HTTP. Conservar EXPIRED/FAILED/UNKNOWN sin convertirlos en PASS ni reenviar para mejorarlos.

## N declarado antes de ejecutar

| Operación | Intenciones INITIAL | REPLAY |
|---|---:|---:|
| Creación de propietario/mascota/cita/confirmación/borrador | 0 | 0 |
| Guardado de cinco textos | 1 | 1 |
| Guardado de tratamiento1+1 | 1 | 1 |
| Finalización | 1 | 1 |
| GET tratamiento vacío DRAFT | 1 | 0 |
| GET tratamiento no vacío DRAFT | 1 | 0 |
| GET tratamiento FINALIZED | 1 | 0 |
| GET detalle DRAFT / FINALIZED | 1 / 1 | 0 |
| Descubrimiento por cita DRAFT / FINALIZED | 1 / 1 | 0 |
| Historia publicada dueño / responsable | 1 / 1 | 0 |

Privacidad, preflight, login, oráculos y observaciones SMTP son intentos auxiliares explícitos; conservan HTTP/traces pero no se mezclan con los grupos anteriores. N de cada estado/perfil queda separado; las conciliaciones extraordinarias agregan intentos identificados, no nuevas intenciones. M8 tiene un registro distinto heredado con varias observaciones, no varios registros nuevos.

Reusar measurementSchemaVersion1, trace nuevo por intento y colector revisado. Guardar errores/timeouts y elegibilidad antes de red, UTC y tiempos monotónicos. Informar latencias/violaciones observadas; nearest rank exacto si se presenta P95, con N y correlación completa. No mezclar padre fa2 con R7 ni borrar sus fallos o muestras.

## Validación y aprobación antes de red

Después de aprobar este plan: implementar únicamente código privado, probar offline mapeo N29/veterinario1, preservación de padre, pines nuevos, global ledger, no repetir inciertas/replays, ventanas vigentes/vencidas y Unicode antes del JSON. Revisar parser/ValidateOnly sin DPAPI y fuentes por SHA; revisión independiente del diff congelado. Root autorizará por separado credenciales/lecturas y las tres escrituras/ventana SMTP después del despliegue y de cerrar fa2.

Este plan no modifica backend, producción, datos ni el harness padre. Si la adaptación exige acoplamiento opaco o copiar el cliente histórico completo, detener y presentar una alternativa antes de hacerlo.
