# Colector de latencias de Cloud Run — diseño y verificación

28/10/2026, America/Bogota. Implementa el [plan previo de latencias](plan-latencias-despliegue-2026-10-28.md), sin modificar backend, harness, productos cloud o configuración desplegada.

## Diseño detallado antes de implementar

1. Añadir `scripts/collect-cloud-run-latencies.py`, Python estándar, consumidor de `measurementSchemaVersion=1` y `requestSamples` del harness. Validar revisión, identidad única `attemptId=traceId` hexadecimal de 32 caracteres, método/estado, tiempos UTC y elegibilidad explícita. Solo `backendLatencyEligible=true`, `backendMetric=M3|M6|M9` y una `latencyOperation` técnica habilitan medición; no inferirla del nombre del caso ni de un HTTP exitoso.
2. Acotar todas las lecturas al proyecto fijo `project-4f1c67ce-5621-40e0-96c`, servicio `grownupsvet-backend`, región `us-central1`, revisión exacta, request log automático, ventana UTC de los intentos y hasta 50 traces por lote. Aplicar un límite finito por consulta. Recuperar únicamente `trace`, `httpRequest.latency/status/requestMethod`, `timestamp` y `resource.labels.revision_name`.
3. Ejecutar exclusivamente `gcloud logging read`. El filtro es su argumento posicional server-side; el `--filter` global no se utiliza. Las opciones documentadas van en un flags-file JSON temporal. Para `gcloud.cmd` de Windows, resolver el `lib/gcloud.py` y Python incluido en el mismo SDK; ejecutar una lista de argumentos con `shell=False`, sin `cmd.exe`, y Python aislado con `-I`. Si ese SDK no tiene Python incluido, fallar sin usar un shell alternativo. En otros sistemas admitir el ejecutable `gcloud` mediante argumentos literales.
4. Capturar stdout y stderr del proveedor sin mostrarlos ni conservar logs crudos. Deshabilitar prompts, logging HTTP, archivos de diagnóstico del SDK y envío de estadísticas de uso en el proceso hijo. Rechazar campos fuera de la proyección, traces ajenos, respuestas truncadas y valores inválidos. El diagnóstico público usa códigos fijos y contadores, sin URL de negocio, IP, cuerpo, headers, credenciales o mensajes de aplicación.
5. Exigir exactamente una fila por intento, con trace del proyecto esperado, revisión, método, estado observado compatible y duración válida. Mantener los intentos con faltantes/duplicados/incompatibilidades como no verificados. Un timeout de cliente con estado desconocido puede tener latencia de plataforma verificable, conservando que el cliente no observó respuesta y sin inferir éxito de negocio.
6. Reconsultar únicamente traces faltantes, con un máximo de tres lecturas por lote por defecto y esperas acotadas. Reemplazar la ausencia por la observación posterior; no sumar una misma fila por repetición de lectura. No reintentar ninguna solicitud HTTP de negocio. Duplicados o incompatibilidades no se reparan eligiendo una fila conveniente.
7. Convertir segundos decimales a milisegundos con `Decimal`, sin redondear antes de ordenar. Calcular P95 nearest rank por pareja métrica/operación. El P95 acreditable de la muestra solo existe con correlación completa; el P95 del subconjunto observado se etiqueta aparte. Conservar denominador, errores, timeouts y latencia cliente por separado. Cero muestras no produce percentil ni aprobación.
8. Guardar un informe JSON saneado en un archivo nuevo, distinto del input. Las duraciones decimales se representan como cadenas exactas. Añadir pruebas `unittest` con proveedor simulado: ausencia/duplicados, duración negativa/inválida, método/estado/revisión incorrectos, prefijo de trace y filtros, timeout, retry solo de faltantes, proyección privada, lotes, percentiles 0/1/20 y construcción segura de argv Windows. No ejecutar CLI real, API, SQL ni SMTP en estas pruebas.

## Contrato y límites

La medida es **latencia de solicitud observada por Cloud Run**, no tiempo exclusivo del código Java. La latencia extremo a extremo del cliente y la aproximación de Prometheus permanecen separadas. Correlación completa acredita únicamente la muestra declarada; no equivale a aceptación funcional, persistencia, recepción de correo, carga representativa o aprobación global de M1–M9.

El SDK y su sesión autenticada ya deben existir. El script no instala herramientas, inicia sesión, crea recursos, habilita servicios ni despliega. Las consultas leen request logs existentes; la ejecución real queda a cargo del agente principal después de autorizar la muestra. La ventana de consulta incluye un margen temporal explícito para diferencias de reloj/registro, y el trace sigue siendo la identidad exacta del intento.

## Verificación de esta implementación

**24 pruebas offline aprobadas**, ejecutadas con:

```powershell
python -B -m unittest discover -s scripts/tests -p test_collect_cloud_run_latencies.py -v
```

Incluyen faltantes, duplicados idénticos, método/estado/revisión incorrectos, duración negativa o inválida, errores HTTP, estado de cliente desconocido, datos privados fuera de proyección, trazas ajenas, respuesta limitada, reconsultas acotadas solo de faltantes, separación por operación/métrica, lotes de 50 y percentiles 0/1/20. El proveedor de prueba es simulado: cero ejecuciones de gcloud, HTTP, SQL o SMTP. También se comprobó en lectura el ejemplo congelado `request-measurement-schema-example.json` del harness, correlacionado exclusivamente con una fila sintética; schema 1 compatible.

Se verificaron espacios finales y `git diff --check`. En esta etapa offline no se ejecutó Maven ni se modificó el harness, Java, despliegue, IAM o configuración cloud. El SDK 590.0.1 existente contiene ambos entrypoints requeridos; su parser/código se inspeccionó sin ejecutar el CLI. La correlación real de salud previa al incremento pertenece a `output/backend-cierre-2026-10-28/trace-correlation-{probe,log}.json` del workspace padre y no se presenta como ejecución real de este colector.

## Invocación y resultado

Una vez definidos `$reportPath`, `$gcloudPath` y `$newOutputPath` con rutas locales, el siguiente comando lee únicamente logs existentes. El directorio de salida debe existir y el archivo debe ser nuevo; no se reemplazan observaciones previas.

```powershell
python -B scripts/collect-cloud-run-latencies.py `
  --report $reportPath `
  --expected-revision grownupsvet-backend-admission-960c8898 `
  --gcloud-path $gcloudPath `
  --output $newOutputPath
```

Los controles opcionales son `--read-attempts` (1–4, predeterminado 3) y `--retry-delay-seconds` (0–20, predeterminado 10). El margen de tiempo es 120 segundos por lado y la ventana máxima por consulta siete días; cada lote contiene hasta 50 traces y un límite de `2*N+1` filas para detectar truncamiento. El formato de salida no incluye las filas crudas ni el filtro completo.

- Código de salida `0`: todos los intentos elegibles tienen una correlación válida; alcance `CORRELATED_SAMPLE_ONLY`, sin certificar funcionalidad ni SLO.
- Código `2`: se conserva el informe, pero falta correlación completa o no existen intentos elegibles. `p95Milliseconds` queda `null` para una operación incompleta; `observedSubsetP95Milliseconds` identifica el subconjunto observado y mantiene el denominador original.
- Código `1`: argumento/input/salida inválido o condición local que impide preparar el informe. El diagnóstico es un código fijo; no contiene detalles crudos del proveedor.

`sourceReportSha256` identifica exactamente los bytes que se interpretaron al inicio. Cada intento conserva su trace, método, estado/timeout y tiempo del cliente; cada operación declara los recuentos, errores y ambos percentiles separados. Las duraciones se serializan como cadenas decimales exactas, sin conversión intermedia a float. No hay reintentos de peticiones de negocio.

## Ejecución real posterior al ensayo

El agente principal autorizó y ejecutó el colector contra el corte congelado `measurement-source-after-finalize.json`, de SHA256 `dd7c15c627ffc75e20cf846986e5d241860e7e32c05577f13c88432874cdfb78`. El resultado `cloud-run-latencies-after-finalization.json`, conservado en `output/backend-cierre-2026-10-28` del workspace padre, correlacionó **28 de 28 intentos elegibles**, sin faltantes, con `grownupsvet-backend-admission-960c8898`. No se hicieron peticiones de negocio adicionales para obtener esa correlación.

El corte contiene escrituras de citas y clínica por encima de 500 ms. Correlación completa significa que se pudo medir esa muestra, no que todas las operaciones cumplan la meta. El informe de aceptación y los planes de rendimiento conservan esos incumplimientos. Las verificaciones offline de las 24 pruebas volvieron a pasar después de la integración del paso de CI; `actionlint` también pasó. La evidencia funcional posterior y sus nuevos requestSamples no se agregan retrospectivamente a este corte congelado.

## Referencias primarias consultadas

- [Request logs automáticos de Cloud Run](https://docs.cloud.google.com/run/docs/logging).
- [gcloud logging read](https://docs.cloud.google.com/sdk/gcloud/reference/logging/read).
- [Flags-file JSON/YAML](https://docs.cloud.google.com/sdk/gcloud/reference/topic/flags-file).
- [LogEntry y HttpRequest.latency](https://docs.cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry).
- [Lenguaje de filtros de Cloud Logging](https://docs.cloud.google.com/logging/docs/view/logging-query-language).
- También se inspeccionó el parser/entrypoint del SDK 590.0.1 instalado, sin ejecutar el CLI.
