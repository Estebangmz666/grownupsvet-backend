# Plan previo para latencias verificables del despliegue

28/10/2026, America/Bogota. El Plan de Calidad exige P95 exacto por operación de backend; los histogramas son aproximados y la medición desde Windows incluye Internet. Se conservarán ambas mediciones, identificando su alcance.

## Inspección y decisión

Cloud Run ya genera request logs con `httpRequest.latency`, trace, método, estado, timestamp y revisión. Una lectura limitada confirmó esos campos para la revisión vigente, sin descargar URL, IP, headers o cuerpos. Se reutilizará esa telemetría existente; no se modifica el backend, se despliega instrumentación, se habilita Cloud Trace ni se crean recursos nuevos.

1. Verificar con un único GET público de salud si un identificador técnico aleatorio propagado mediante headers estándar permite localizar exactamente su request log. No añadir datos de usuarios a ese identificador, query strings ni etiquetas Prometheus.
2. Si se comprueba la correlación, el harness asigna un trace aleatorio por intento HTTP y persiste únicamente trace/operación/método/estado/timestamp/latencia del cliente. Cada intento conserva su identidad; un retry recibe otro trace y no reemplaza la primera observación.
3. Consultar Cloud Logging por proyecto, servicio, revisión, ventana UTC y los traces del ensayo. Usar proyección explícita de los campos necesarios: no recuperar IP, URL con IDs, Authorization, cuerpos ni mensajes de aplicación. No exportar credenciales Google; usar la sesión CLI ya autorizada.
4. Exigir una sola entrada por intento, revisión y método coincidentes, estado compatible y duración válida. Missing, duplicados o incompatibilidades se reportan como no verificados; no se eliminan del denominador ni se mezclan con otros ensayos.
5. Convertir la duración decimal a milisegundos sin redondear antes de ordenar. Calcular `t[techo(0,95*N)]` por operación y el perfil declarado, incluyendo errores de las solicitudes elegibles. Informar tamaño, ventana, revisión, cobertura de correlación y errores/timeouts de cliente.
6. Nombrar la medida como **latencia de solicitud en Cloud Run**, que incluye el procesamiento observado por la plataforma. Mantener separadas la latencia extremo a extremo desde Windows y la aproximación Micrometer/Prometheus. No presentarla como medición interna exclusiva de código Java.

Si la correlación no funciona o hay logs faltantes, mantener esos indicadores no verificados y documentar la limitación; no inventar tiempos ni sustituir muestras de otra revisión. El ensayo funcional y los oráculos de persistencia siguen siendo obligatorios.

Fuentes primarias: [request logs automáticos](https://docs.cloud.google.com/run/docs/logging), [propagación de contexto](https://docs.cloud.google.com/trace/docs/trace-context), [campos de LogEntry](https://docs.cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry).
