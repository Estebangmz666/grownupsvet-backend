# Diagnóstico de latencia y plan futuro

31/10/2026, America/Bogota. El diagnóstico de Window A se elaboró mediante lectura de fuentes y evidencia conservada. No se implementaron nuevas optimizaciones. Las verificaciones funcionales posteriores se documentan por separado, con su revisión, ventana y población; no convierten hipótesis de este análisis en causas demostradas.

La prioridad comunicada por el usuario, a partir de la indicación del profesor, es que el sistema funcione correctamente y que las desviaciones de rendimiento tengan explicación y plan de mejora. Por tanto, **no se propone implementar más optimizaciones para alcanzar 500 ms en este recorrido**. El objetivo documentado sigue siendo estrictamente `<500 ms`: aceptar la desviación no cambia el umbral ni convierte las observaciones incumplidas en PASS. Corrección, seguridad, integridad de datos y contratos siguen siendo condiciones de aceptación.

## Qué se midió y qué significa

Window A corresponde a la aplicación `fa2a9955c7217c9e6bd00c8a9881ba20711b6a81`, imagen `sha256:f3456068f8fffb297f53ac7f67d75d20c1e7028150f77d403647272aad608f28`, revisión `grownupsvet-backend-00008-824`. No incluye R6 o R7.

La ventana del 28/10 fue 16:53:37.312644–16:56:31.744099 UTC. Hubo 65 HTTP con respuestas esperadas y cero resultados inciertos o inesperados; se correlacionaron 24/24 intentos elegibles de latencia con revisión, trace, método y estado compatibles. Los otros 41 HTTP no integran ese denominador.

El tratamiento medido en esa ventana tenía una receta y un control (1+1). La cobertura local de 50+50 hijos es una prueba distinta; no se atribuye esa cardinalidad a las solicitudes medidas.

El perfil fue secuencial, un cliente, sin carga concurrente artificial. Se utilizó calentamiento explícito antes de las operaciones y mínimo 1 temporal durante Sample, según la operación registrada por el agente principal. La fuente conserva los perfiles `A` y `A_WARMUP`; los accesos `first_after_deployment_or_idle` y `warmup_*` no son elegibles para la latencia de negocio. Esto describe un ensayo calentado, no prueba ausencia de reinicios o pausas durante cada solicitud ni sustituye una medición específica de arranque frío. No se consultó infraestructura para revalidar hoy esas condiciones.

| Operación incumplida | N | P95 Cloud Run, ms | Resultado frente a <500 ms |
| --- | ---: | ---: | --- |
| Confirmar cita | 1 | 545.292614 | Incumplido; desviación aceptada para la entrega |
| Crear borrador clínico | 1 | 615.718719 | Incumplido; desviación aceptada para la entrega |
| Guardar textos clínicos | 3 | 546.875291 | Incumplido; desviación aceptada para la entrega |
| Leer tratamiento | 3 | 595.488405 | Incumplido; desviación aceptada para la entrega |
| Guardar tratamiento | 3 | 693.694336 | Incumplido; desviación aceptada para la entrega |

Se usó nearest rank `techo(0,95 × N)`: con N=1 o N=3, el P95 de muestra es el máximo observado de ese pequeño grupo. No es una estimación estable del P95 poblacional, una prueba de capacidad, ni evidencia de disponibilidad sostenida. Las 14 operaciones completas permanecen en el corte original; no se seleccionan solo las cinco anteriores para recalcular tasas.

Cloud Run informa latencia de solicitud de plataforma, no CPU exclusiva de Java. La duración del cliente incluye Internet y se conserva separada. No se suman ni promedian esas medidas. El panel Prometheus posterior devolvió NaN para el P95 en su ventana: que la consulta funcionara no aporta un percentil finito ni reemplaza el colector exacto. Tampoco se comparan los seis incumplimientos del corte anterior con los cinco de Window A como una tasa de mejora: cambian revisión y población.

## Factores acreditados por fuentes y contadores

- Los caminos clínicos ejecutan varias operaciones JDBC seriales: adquisición ordenada de contexto/locks, idempotencia, escritura del recurso y auditoría. El tratamiento reemplaza los dos grupos de hijos antes de avanzar versión y registrar auditoría. No se trata de una única escritura SQL aislada.
- La base de datos utilizada en el ensayo es PostgreSQL remoto de Render; Cloud Run está en `us-central1` y la conexión Render identificada previamente pertenece a Ohio. La separación de proveedor/región es un dato de topología del ensayo, no una medición de RTT ni una demostración de la ruta física de red.
- Las restricciones realizan trabajo adicional dentro de PostgreSQL: V19 protege identidad, versión y snapshot del encuentro; V20 exige DRAFT y toma el lock del padre en mutaciones de hijos; V21 comprueba al commit la consistencia FINALIZED/COMPLETED; V22 valida los orígenes de recordatorios. Su existencia está acreditada; su costo temporal individual no está medido. V23 protege admisión de login y no convierte por sí misma la autenticación en la causa de estas duraciones.
- Los contadores instrumentados de pruebas acreditan sentencias de aplicación, no paquetes de red, tiempo por sentencia ni SQL interno de triggers. La existencia de una transacción durable tampoco cuantifica cuánto consume su commit.

La combinación de SQL serial y PostgreSQL remoto hace **plausible** que los viajes repetidos contribuyan a la duración. No demuestra que expliquen todo el exceso, no permite atribuirles un porcentaje ni calcular RTT dividiendo la latencia total entre el número de sentencias.

## Lo que ya se mejoró, sin atribuirle un P95 nuevo

R6 agrupó evento y operación clínica con un CTE dependiente de `RETURNING`, manteniendo el padre separado, y agrupó la lectura de hijos mediante `UNION ALL` tipado. Su regresión local terminó con 764 pruebas/65 suites correctas. Los presupuestos de servicio comprobados incluyen 10 sentencias para crear borrador/textos y 12–14 para guardar tratamiento según listas vacías o pobladas; 1+1 y 50+50 usan el mismo número de sentencias de aplicación.

R7 reutilizó participantes ya bloqueados para confirmar citas, con guardas de identidad y el mismo predicado de elegibilidad, y reunió encuentro autorizado, versión y tratamiento en un snapshot de sentencia. Su regresión local terminó con 769 pruebas/65 suites correctas. Confirmación tiene un máximo de 11 sentencias de servicio y GET tratamiento dos consultas. Se mantuvieron las pruebas de replays, carreras, permisos, snapshots, precisión y rollback.

Estas reducciones son hechos de código y pruebas locales. El análisis histórico anterior a la publicación no contenía mediciones desplegadas de R6/R7. La actualización siguiente registra la nueva muestra sin restar milisegundos estimados a fa2 ni atribuir causalidad a las diferencias entre perfiles.

## Actualización: muestra desplegada de R7

La aplicación `222131c5704652aa85cf79f2548d8c225f590e54`, imagen `sha256:3f1ed36f60bdaa245d529cc1fb56e59d29255f9d3c94446d1c46a88f7e06ca11`, quedó publicada como `grownupsvet-backend-00009-vmd`. El colector verificó **18/18 intentos elegibles**, con un intento por grupo: 12 grupos INITIAL, tres REPLAY y tres RECONCILIATION. La conciliación adicional conserva su clasificación y no cuenta como escritura nueva. Fuente congelada: `r7-measurement-source-initial.json`, con 96 HTTP; las observaciones auxiliares posteriores no cambian esa población.

| Grupo R7 incumplido | N | P95 Cloud Run de la muestra, ms | Resultado |
| --- | ---: | ---: | --- |
| Guardar tratamiento 1+1 | 1 | 722.465284 | No cumple <500 ms |
| Finalizar atención | 1 | 630.391351 | No cumple <500 ms |

Los otros 16 grupos quedaron por debajo de 500 ms en esta muestra; incluyen lecturas, replays y conciliaciones. No son 16 escrituras nuevas ni acreditan un SLO general. La creación clínica R7 tiene N=0: se continuó un borrador heredado de fa2. Las métricas de registro y citas conservan sus cortes anteriores; no se inventan mediciones R7 de operaciones no ejecutadas.

Que una medición aislada de finalización sea mayor que la de fa2 no prueba una regresión causal, y que otras bajen no demuestra qué cambio la causó. Cambian revisión, instante, recurso y perfil; falta la descomposición por componente y una comparación controlada. Se conservan ambas muestras y sus límites. La aceptación funcional y la recepción humana de sus dos avisos están documentadas aparte del incumplimiento numérico. El plan futuro siguiente permanece **sin implementar**.

## Hipótesis que aún no son causas demostradas

| Hipótesis | Evidencia que falta para valorarla |
| --- | --- |
| RTT repetido entre API y PostgreSQL | Duración JDBC cliente frente a ejecución del servidor, con adquisición de conexión y commit separados; no basta el nombre de región. |
| Esperas por locks o concurrencia externa | Esperas y sesiones bloqueantes correlacionadas con las solicitudes concretas; el cliente secuencial no excluye jobs u otros actores. |
| Plan SQL, índices, volumen o estadísticas | Planes y ejecución sobre datos representativos; no se infiere lentitud de un JOIN o trigger solo por existir. |
| Espera o creación de conexiones Hikari/TLS | Tiempos de adquisición/uso y ocupación del pool durante la ventana, distinguiendo conexión reutilizada de conexión nueva. |
| CPU, GC, JIT o serialización | Métricas/perfilado temporal correlacionado; exponer gauges de JVM o memoria no demuestra saturación o pausas. |
| Commit durable o restricciones | Separación temporal de commit/esperas/ejecución SQL y revisión de triggers; las pruebas de atomicidad no son un benchmark de ese costo. |

No hay evidencia aquí para ordenar esas hipótesis por porcentaje de contribución o afirmar una causa exclusiva. No se ha probado saturación, regresión causal de una consulta concreta, ni que cambiar proveedor/región o recursos sea necesario.

## Plan futuro opcional, sin ejecución autorizada en este borrador

1. **Conservar la entrega funcional y la línea base.** Registrar la desviación aceptada, mantener las cinco cifras y sus identidades, cerrar primero los pendientes funcionales y de seguridad. No crear más citas, mensajes o escrituras para obtener una muestra favorable. No llamar escritura nueva a un replay.
2. **Definir una investigación pequeña si vuelve a ser necesaria.** Fijar revisión/digest, caso y cardinalidad, calentamiento, N, presupuesto y criterio de comparación antes de medir. Separar arranque frío, uso calentado y concurrencia; conservar errores, timeouts y reconciliaciones. Reutilizar logs/métricas existentes de bajo costo antes de proponer instrumentación.
3. **Analizar PostgreSQL local con datos sintéticos representativos.** En un esquema aislado con V1–V23, estudiar planes de lecturas, índices y las formas 0/1/50 de hijos. Empezar por `EXPLAIN` sin ejecución. Un eventual `EXPLAIN ANALYZE` de DML ejecuta escrituras: solo se consideraría en la copia local desechable, con controles de transacción y limpieza propios; nunca se describe como consulta inocua de producción. Un buen plan local no elimina la hipótesis de red, esperas o diferencias de datos.
4. **Si faltan tiempos por componente, diseñar tracing controlado.** Muestrear de forma acotada adquisición de conexión, contexto/bloqueos, escrituras, auditoría, commit y serialización. Registrar duraciones, conteos y etiquetas técnicas de cardinalidad limitada; no SQL/binds, tokens, headers, destinatarios, IDs de pacientes o cuerpos clínicos. Distinguir spans anidados/solapados para no sumarlos dos veces. Si requiriera instrumentación nueva o recursos externos, presentarla como otro incremento con autorización previa; este documento no la habilita.
5. **Elegir una mejora solo después del diagnóstico.** Si domina ejecución, evaluar el plan/índice concreto; si domina adquisición, revisar el pool con evidencia; si domina el recorrido serial remoto, estudiar una agrupación SQL pequeña. Como candidato futuro, padre → evento → operación podría comunicar datos exclusivamente por `RETURNING`, conservar UUIDs/versiones/autores/instantes y exigir cardinalidad completa. Eso ahorraría una sentencia adicional frente a R6, no un tiempo garantizado. Requiere pruebas PostgreSQL explícitas de FK, snapshots, triggers inmediatos/diferidos, fallo/omisión de cada rama y respuesta basada en filas persistidas antes de proponer código.
6. **Validar cualquier cambio futuro de forma independiente.** Conservar locks, permisos frescos, fingerprints, replays, inmutabilidad, NUMERIC(18,6), precisión temporal y métricas AFTER_COMMIT; ejecutar la regresión y después una muestra desplegada comparable y autorizada. No usar funciones PL/pgSQL monolíticas, cambiar orden de locks, desactivar restricciones o agrupar DELETE/INSERT sobre los mismos hijos suponiendo un orden textual de CTE. No fusionar por conveniencia la finalización y los recordatorios, cuyas validaciones de estado son distintas. La mejora se informa aunque siga sobre 500 ms; no se reetiqueta el resultado para alcanzar PASS.

No se propone ahora cambiar infraestructura, instalar agentes, habilitar sondeos, generar gasto, mover la base de datos o iniciar un experimento productivo. Estas fases son una reserva de trabajo opcional, no un bloqueo a la entrega funcional aceptada.

## Procedencia y límites del borrador

- `grownupsvet-backend/docs/verificacion-despliegue-rendimiento-2026-10-28.md`: revisión, ventana, 65 HTTP, 24 correlaciones y 14 operaciones.
- `output/backend-cierre-2026-10-28/cloud-run-latencies-postpatch-window-a.json`: colector exacto; SHA256 `4d3d84d8fefcf435c766f12acaed0f58a61e6f01dba968a163a35481fb9400a6`, coincidente con el documento conservado. Solo se proyectaron valores agregados para este diagnóstico.
- `output/backend-cierre-2026-10-28/postpatch-measurement-window-a-source.json`: perfiles, calentamiento excluido y clasificación previa de intentos. No se copiaron filas HTTP o identificadores al borrador.
- `grownupsvet-backend/docs/verificacion-rendimiento-r6-2026-10-28.md` y `verificacion-rendimiento-r7-2026-10-28.md`: resultados locales y límites de atribución.
- Fuentes inspeccionadas: `ClinicalEncounterService`, `ClinicalTreatmentService`, migraciones V19–V23 y planes R6/R7. No se modificaron estas fuentes ni se ejecutaron sus sentencias.

Revisado por el agente principal el 31/10. Las observaciones históricas de Window A se mantienen separadas de Window B/fa2 y de la aceptación R7 del 31/10; cada una conserva identidad y evidencia propias.
