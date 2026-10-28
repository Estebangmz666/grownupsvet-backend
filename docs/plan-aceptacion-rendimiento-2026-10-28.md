# Plan previo de aceptación de rendimiento después del parche

28/10/2026, America/Bogota. **Plan únicamente**: no implementar otro harness ni ejecutar API, SQL, SMTP, Cloud Run o Maven para este documento. El agente principal revisará este plan antes de autorizar preparación y ejecución. Los resultados históricos permanecen en [aceptación desplegada del 28](aceptacion-backend-desplegado-2026-10-28.md); sus incumplimientos M6/M9 no se sustituyen por una muestra favorable posterior.

## Condiciones de entrada e identidad

1. Exigir CI y regresión funcional/autorización del parche antes de despliegue, incluidos retornos normalizados de PostgreSQL, rollback, concurrencia, identidad/roles frescos y programaciones V19–V22. El posible acceso por replay de un DRAFT inelegible se resuelve/revisa por separado; no simular cancelaciones o desactivaciones reales para probarlo.
2. Root publica una nueva imagen y entrega **commit fuente, revisión, digest e imagen exactos**. El contrato esperado sigue OpenAPI **0.11.0, 47 rutas y V1–V23**; si cambia, revisar primero el plan. No medir el parche contra la revisión antigua `grownupsvet-backend-admission-960c8898` por accidente.
3. Preflight valida tráfico/revisión/digest por el plano de control, contrato por su estructura real, salud mínima y acceso máquina. Perfil prod/external-postgres/monitoring, misma configuración de recursos declarada para comparar. Root mantiene mínimo 1 durante la muestra y durante cualquier ventana de envío autorizada.
4. Un run fija su identidad antes de la primera solicitud; cambio de revisión/digest/contrato exige otro run. Guardar ventanas UTC y Bogotá, perfil, N planificado, errores e intenciones. No cambiar la identidad de un checkpoint existente para mezclar muestras.

## Nuevo run e implementación pequeña

Crear después de aprobación un directorio privado nuevo, por ejemplo `%USERPROFILE%/.grownupsvet/acceptance/backend-performance-2026-10-28-<runid>`, sin reutilizar el run del 28 como destino. `parentRun` enlaza solo ruta lógica, SHA256, revisión y recursos necesarios del checkpoint del 28; conservar intactos su código, reportes, datos e intenciones. No importar tasas, muestras o PASS del padre como evidencia de la revisión nueva.

Evitar copiar o transformar todo el harness de más de mil líneas. La opción prevista es un adaptador pequeño que reutilice por importación explícita y SHA fijado el transporte HTTP/guardas/oráculo read-only ya revisados. El import no ejecuta `main`, workflows, constructor histórico, DPAPI o red; el run nuevo tiene inicializador, fases, ledger global y reporte propios. Si ese contrato de reutilización no puede quedar claro y acotado, detener la implementación para revisar un módulo de cliente mínimo, sin refactorizar los artefactos históricos.

Todas las lecturas/escrituras de texto usarán **encoding UTF-8 explícito**; el import de fuente se valida por bytes/SHA. No usar `read_text()` con encoding por defecto en Windows, recodificaciones de consola ni reparación heurística de Unicode. Las frases canónicas de fixtures con `áéíóúñ`, instrucciones y motivo de control deben verificarse antes del JSON y después de su ida/vuelta. La corrección de un fixture futuro nunca altera una intención ya enviada.

## Recursos previstos y barrera temporal

- **Atención A29:** la mascota distinta y cita ficticia del 29/10 a las 06:00 de Bogotá (`11:00Z`) creada en P2Current del padre. Allí terminó CONFIRMED/ASSIGNED v1 y DRAFT v1, con cinco textos y sin tratamiento. Son datos históricos de localización: antes de escribir se deben revalidar por REST y oráculo coherente, incluyendo UUIDs, creador/veterinario vigente, contenido, versiones, estado activo y destinatario autorizado. Un cambio ajeno no explicado detiene la fase para reconciliar.
- **Cita N29 nueva, decisión revisada por root:** objetivo **29/10/2026 a las 07:00 America/Bogota (`12:00Z`)**, con **veterinario1** y una mascota ficticia nueva, separada de A29. La existente A29 conserva **29/10 a las 06:00 y veterinario2**, sin reprogramación. La mascota nueva y el motivo de N29 quedan marcados como ensayo no asistencial; su alta no envía correo.
- **Selección de turno antes de POST:** priorizar las 07:00. El preflight futuro consultará `GET /api/v1/availability-slots` con `from=to=2026-10-29`, `veterinarianId` del veterinario1 y paginación completa acotada; este endpoint del contrato 0.11 devuelve turnos solicitables. Contrastará el elegido mediante `GET /api/v1/veterinarians/{veterinarianId}/availability-slots/{slotId}` para exigir PUBLISHED, mismo veterinario, instante/versión y zona correctos. Exigir futuro, fecha legal desde mañana, horizonte/corte válidos y **menos de 24 h hasta el inicio**, para no generar otro aviso P12.
- Si las 07:00 no están disponibles, elegir el **primer turno ya publicado y libre del 29/10 cuyo inicio esté entre 07:00 y 09:00 de Bogotá, inclusive**, ordenado por startsAt y luego UUID, con el mismo límite <24 h. No cancelar, desbloquear, reasignar o mover recursos para obtener un turno ocupado.
- **Publicación condicional autorizada por root:** si no hay turno publicado libre, consultar primero la agenda completa del veterinario1 por API administrativa, con ambas clases PUBLISHED/BLOCKED y paginación verificada. Elegir el primer hueco de 30 minutos entre 07:00 y 09:00, en :00/:30, sin solapamiento con ningún turno existente. Si faltan páginas, hay intervalos/estados desconocidos o no existe hueco, guardar `NO_VALID_PLANNED_SLOT` y detener. Publicar **exactamente un turno como máximo** mediante el endpoint existente `POST /api/v1/veterinarians/{veterinarianId}/availability-slots`, con administrador activo y body `{startsAt}`. OAS0.11 y el código confirman fecha futura, veterinario activo, duración30min y respuesta201 PUBLISHED con id/versión. No usar lote ni modificar bloques existentes.
- Guardar antes del POST de publicación la intención, instante/intervalo candidato, veterinario, resultado completo del análisis de solapamientos y marca sent. El id/versión generados por el servidor se guardan inmediatamente después de201 y antes del POST de cita; no inventar un id previo. Fallo o respuesta incierta conserva ese único intento: reconciliar por agenda/eventos o detener, sin publicar en otro hueco para obtener PASS. Declarar publicación **N=1 condicional** con operación/traza elegibleM6 propia, oN=0 si se reutiliza un turno existente; fijar esta condición antes de red.
- Registrar **antes de cualquier POST del escenario N29** el slotId, versión, startsAt/endsAt UTC y Bogotá, veterinario, elección principal/fallback y momento/resultado del preflight. Una vez enviada la intención de cita, no cambiar de turno/clave tras un conflicto o timeout: conservar el intento y reconciliar/parar. La API conserva su validación atómica ante una ocupación concurrente; un resultado inesperado no se reintenta en otros turnos para conseguir un PASS.
- El scout offline del 25/26 **no encontró otra candidata pasada**. Eso no afirma el inventario productivo actual. La original del 26 ya está FINALIZED; no se reabre.

**La finalización de A29 no puede acreditarse antes del 29/10 a las 06:00 reales.** Antes de esa hora el cliente guarda `WAITING_FOR_REAL_APPOINTMENT_START` y termina sin POST de finalización. Solo una candidata ficticia preexistente identificada después, con estado actual revalidado por REST y plan revisado por root, permitiría otro caso legal hoy; no existe tal candidata en la evidencia inspeccionada. No buscar una cita real ajena para evitar la espera, retrofechar por SQL, cambiar reloj o contar un replay de finalización como escritura nueva.

## Perfiles y calentamiento

Separar dos ventanas: **A**, preparación y muestra sin finalización; **B**, finalización desde el inicio real. Ambas son secuenciales, un cliente, sin concurrencia artificial, pausas humanas fuera del reloj HTTP y cardinalidades declaradas. Cada ventana repite calentamiento acotado y conserva su etiqueta; no combinar percentiles de ventanas/revisiones.

Registrar separadamente el primer acceso después de desplegar/estar inactivo y hasta dos GET de salud de calentamiento. No usar registros de negocio para calentar. Si no se prueba que hubo arranque frío, rotular `first_request_after_deployment_or_idle`, no inventar un cold start. Un mínimo 0 configurado no demuestra cero instancias. Guardar esos tiempos con `backendLatencyEligible=false`; no ocultarlos, promediarlos con negocio ni repetir calentamientos hasta lograr un número favorable.

Después, verificar identidad/readiness y ejecutar la muestra predefinida. Los GET de reconciliación y diagnóstico llevan propósito explícito. Los tres GET de detalle A29 previstos abajo son: reconciliación inicial, después de guardar textos y después de tratamiento; cuentan como muestra, no como solicitudes adicionales ocultas.

## N fijado antes de ejecutar

| Ventana / operación | N de intenciones iniciales | Recurso / cardinalidad |
| --- | ---: | --- |
| A · crear mascota ficticia | 1 | Mascota nueva independiente para N29 |
| A · consultar disponibilidad | 3 | Misma fecha legal N29, carga/paginación fija |
| A · publicar turno | 1 condicional; 0 si se reutiliza | Un único hueco validado, sin solapamientos; operación M6 separada |
| A · solicitar cita nueva | 1 | N29; confirmar elegibilidad/libertad antes del intento |
| A · confirmar cita | 1 | N29, administrador; un aviso real como máximo |
| A · crear borrador | 1 | N29; campos clínicos opcionales vacíos, sin receta/control |
| A · guardar textos | 3 | A29; nueva clave por intención, cinco campos canónicos no asistenciales |
| A · guardar tratamiento | 3 | A29; reemplazo completo, exactamente una receta y un control |
| A · detalle de atención | 3 | A29; los tres puntos declarados arriba |
| A · leer tratamiento | 3 | Uno tras cada PUT, siempre cardinalidad 1+1 |
| A · replay de cita / creación de borrador | 1 cada uno | Intención original exacta de N29; operaciones separadas de creación |
| A · replay de textos / tratamiento | 1 cada uno | Última intención propia exacta de A29, sin nuevo efecto |
| B · guardar tratamiento con ventana de envío real | 1 | A29; una dosis y un control con tiempos futuros reales |
| B · finalizar atención | 1 | A29 desde su inicio; ambas versiones frescas |
| B · replay de finalización | 1 | Mismo clientRequestId/body, grupo separado de escritura |
| B · detalle publicado / tratamiento / historia propia | 3 cada uno | A29 publicada; recurso y cardinalidad declarados |

Los casos de roles son un intento predefinido por rol/ruta pertinente y se informan como comprobaciones funcionales, sin mezclarlos con las operaciones válidas de latencia. No se crean propietarios, staff o más citas para aumentar N de este plan. La publicación se mide como operación M6 separada y tiene N=1 solo si fue necesaria y validada previamente; N=0 si se reutilizó. No ampliar el número de publicaciones después de observar un error.

Los indicadores/operaciones no ejercitados por el run nuevo quedan con N=0/no medidos; no se copian los resultados del padre para completar M1–M9. N=1 es una única observación y N=3 sigue siendo una muestra pequeña. No insistir hasta estar bajo 500 ms ni cambiar N después de observar un fallo. Si una precondición inesperada obliga a detenerse, conservar N planificado, N intentado y N pendiente; no declarar completo el grupo faltante.

## Ventana A: preparación y muestra sin finalización

1. Con tokens nuevos, revalidar A29 por detalle y por cita, propietario/mascota, veterinario vigente/creador, contenido y versiones. Snapshot SQL solo lectura por IDs con TLS verify-full y read-only real. Rechazar estados cancelados, finalizados, sin asignación, datos ajenos o destinatario distinto; no reparar por SQL.
2. Guardar tres intenciones de textos secuenciales. La primera usa la versión actual revalidada; cada siguiente usa la devuelta y confirmada por la intención anterior. Persistir clientRequestId, versión, cuerpo y resultado antes/después de cada envío. No reutilizar clave con otra versión o texto. Comprobar incremento único, autoría/eventos/operación y respuesta idéntica al valor almacenado.
3. Guardar tres tratamientos secuenciales con una receta y un control, palabras Unicode correctas y mensajes explícitos de ensayo/no administrar. Mientras DRAFT no existen programaciones ni envíos clínicos. Para esta ventana se pueden usar fechas ancladas al inicio de A29 (+10/+20/+45 minutos), registradas como tales; no se confunden con las fechas definitivas de entrega de la ventana B.
4. Seleccionar y registrar primero el turno válido N29 mediante el preflight de disponibilidad anterior. Después crear la mascota nueva y solicitar la cita con esa intención inmutable. Oráculo de fila única, relaciones/reserva/versión y replay sin escritura. Antes de confirmar, revalidar destinatario previo y condición estricta <24 h; confirmar con administrador para producir un solo aviso OWNER_CONFIRMED. Si la condición deja de ser válida o aparece un conflicto, detener sin mover la cita, alterar el turno o generar otra intención para sustituir el resultado.
5. Crear únicamente el borrador N29 y dejar sus campos clínicos opcionales vacíos: el marcado no asistencial está en mascota/motivo de cita. No añadir otro PUT de textos fuera del N declarado de A29. Dejar N29 CONFIRMED+DRAFT v0 sin receta/control, sin finalizar anticipadamente o cancelar para aparentar un caso real.
6. Verificar roles y replay sin nuevos efectos. OWNER no ve DRAFT, veterinario ajeno no accede y roles administrativos no reciben clínica; no desactivar profesionales ni cambiar permisos productivos para probar frescura. Las carreras/revocación se acreditan con pruebas aisladas previas al despliegue.

La nueva fecha N29 del 29/10 a las 07:00 —o el fallback válido hasta las 09:00— permite **solicitar, confirmar y crear el borrador hoy 28/10**, siempre que el preflight real acredite <24 h y las demás precondiciones. Se elimina la espera del objetivo anterior; no se cambió la cita existente A29 ni su barrera de finalización. Solo se permite la publicación condicional única descrita arriba; no se hace reschedule para eludir condiciones.

El presupuesto adicional de esta ventana es **un solo correo de confirmación**, al mismo Gmail previamente autorizado. No hay correos de registro, reprogramación, cancelación o recuperación; no leer OTP ni reenviar. La condición <24 h evita crear otro aviso P12 posterior. Guardar el estado durable por el endpoint propio de notificaciones, no por un nuevo lector directo de BD.

## Ventana B: finalización y reloj real

Root autoriza expresamente la reanudación desde el inicio real de A29. Repetir identidad/revisión, login y lecturas actuales antes de escribir. Si el registro ya finalizó mediante una intención propia incierta, reconciliar/replay sin declararlo otra escritura; si finalizó fuera de este run, detener para revisar.

Antes de finalizar, crear **una nueva intención de tratamiento** mientras siga DRAFT: startsAt aproximadamente `ahora+10 min`, endsAt `ahora+20 min`, intervalo 1 h y control scheduledAt `ahora+45 min`. Existe una sola dosis futura y el aviso de control es inmediato por quedar <24 h. No cambiar silenciosamente la intención de A ni extender una ventana vencida; persistir la nueva clave y versión antes del PUT. Si se pierde esta ventana, detener y revisar el caso antes de definir otra intención.

Finalizar una sola vez con ambas versiones actuales; comprobar FINALIZED/COMPLETED en un snapshot coherente, creador intacto, responsable final, un evento/operación por transición y dos programaciones. Replay conserva versiones/eventos/programaciones y no se cuenta como nueva finalización. Las tareas asíncronas pueden avanzar legítimamente entre lecturas: distinguir esa evolución de duplicación de ocurrencias.

Root mantiene mínimo 1 durante toda la ventana. Observar una sola tarea CONTROL y una PRESCRIPTION, destinatario autorizado, tiempos/expiry y estados. Hacer lecturas acotadas alrededor de la dosis y después de endsAt+15 s, sin bucles largos ni forzar workers. No enviar después del fin ni recuperar dosis vencidas. SENT significa aceptación SMTP; recepción y contenido requieren evidencia separada, con USER_REPORTED si procede. Esta ventana permite como máximo dos avisos clínicos al mismo destinatario, y requiere su autorización temporal antes de ejecutarla.

## Cliente, identidad y admisión compartida

- HTTPS/origen fijo, certificados normales, sin proxy ni redirects. Rutas/métodos derivados del contrato local fijado; ningún endpoint nuevo. JWT solamente en memoria por proceso, login una vez por actor y reutilización durante esa fase.
- **Un ledger global privado de login**, compartido entre runs/fases y protegido por mutex: máximo conservador de cinco intentos por cuenta/15 minutos, reservado antes de POST. Referencia estable de cuenta sin correo en logs; no usar un contador nuevo para eludir intentos recientes del padre. Integrar los intentos recientes conocidos y respetar también el servidor ante actividad externa desconocida.
- 429, credencial inválida o transporte incierto no disparan brute retry. Persistir Retry-After/notBefore, incluido bloqueo global si corresponde, y terminar la fase. No cambiar credenciales, identidades o mecanismos para esquivar una guarda o un rechazo automático.
- Passwords DPAPI existentes o de fixtures, solo memoria/STDIN; nada en argv/logs/reportes. No persistir tokens, OTP, headers, contraseñas, destinatarios o cuerpos SMTP. Ficheros UTF-8 explícito, ACL usuario/SYSTEM, mutex por run y escritura atómica del checkpoint.
- SQL únicamente SELECT por recursos del ensayo en BEGIN READ ONLY/ROLLBACK con TLS verify-full. No seeds, writes, reloj artificial, inventario productivo general ni nuevas credenciales de BD. La API de notificaciones propias acredita metadatos de correo sin revelar payloads.

## Intentos, trazas y valoración

Cada HTTP conserva traceId técnico nuevo y los headers revisados X-Cloud-Trace-Context/traceparent, caso lógico, tipo INITIAL/REPLAY/RECONCILIATION, operación, perfil, revisión, estado, UTC y tiempo monotónico. No querystrings/PII en traces o etiquetas. Conservar todos los errores y timeouts; tiempos de timeout son censurados. Una conciliación exitosa no convierte en éxito observado el primer envío incierto.

N de la tabla cuenta intenciones iniciales. Replays deliberados tienen N propio; una reconciliación extraordinaria conserva la intención original y agrega su intento, no una escritura nueva. Informar N inicial, replays/reconciliaciones y total HTTP por separado, sin eliminar el inicial fallido de su grupo. La elegibilidad/grupo del intento se fija antes de enviarlo; no reclasificar el error después de verlo.

El colector proyecta solo trace/revisión/método/estado/timestamp/duración, exige una entrada compatible por intento elegible y conserva faltantes/duplicados como no verificados. Guardar fuente/SHA, ventana y población exacta; si falta correlación, el P95 completo no se acredita. No pedir ni exportar URL, IP, headers o cuerpos.

Por operación/perfil/revisión, ordenar duraciones decimales completas y usar **nearest rank `techo(0,95×N)`**, sin redondear antes de ordenar. Medida principal: latencia de solicitud Cloud Run, no CPU Java exclusiva. Cliente E2E incluye Internet; Micrometer es otra aproximación. Presentar por separado las tres medidas y el frío/caliente declarado; no promediar percentiles o mezclar ventanas.

La meta es estrictamente `<500 ms`, conservando también igualdad o exceso como incumplimiento. Una muestra nueva favorable no borra el corte histórico ni demuestra un 99% poblacional/SLO sostenido. La finalización seguirá **PENDIENTE POR INICIO REAL** si no se ejecuta legalmente; no completar su fila con un replay o con datos aislados de CI.

## Entrega y revisión antes de acción

Implementación futura pequeña y pruebas offline de parser, imports sin efectos, Unicode antes del JSON, preservación del padre, global login ledger, pin de revisión/digest, N/errores/replays, ventanas reales y oráculos. Root revisa el código/manifest de ejecución antes de red. Después de las ventanas activas, root coordina apagar monitoreo y volver a mínimo 0/máximo 1 preservando imagen/configuración; el harness no cambia cloud.

Este plan no crea datos, envíos, alarmas o automatizaciones ni declara terminado el trabajo de rendimiento. Las decisiones y bloqueos temporales quedan explícitos para reanudar sin eludir reglas.

## Contrato mínimo del adaptador aprobado para implementación offline

Se preparará un cliente pequeño con importación por SHA de únicamente los métodos revisados request/db y helpers puros del padre28. No se invocará su constructor ni se heredarán sus workflows/recursos. Inicialización, ledger global, fases y reporte serán explícitos en módulos nuevos; no copiar/transcodificar el archivo completo. El import compila bytes UTF8 de fuente fijada sin pycache en el padre y sin ejecutar main. Pines del padre: cliente46e64904…, contrato3a1087a2… y checkpointd9b68c71…; un cambio inesperado aborta. No se crean credenciales ni run real durante tests.

El launcher nuevo exigirá identidad exacta de despliegue entregada por root y modo Execute antes de cargar DPAPI; por defecto ejecutará solo selftests. El ledger global quedará en un directorio nuevo de control protegido, con bloqueo entre procesos y reserva antes de login, sin modificar el padre. No se ejecutará ninguna fase real hasta revisión de código y entrega de la nueva revisión/digest.
