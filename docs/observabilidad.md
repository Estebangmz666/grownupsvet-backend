# Observabilidad del backend

Preparación I7 del 25/10/2026. La opción elegida por el usuario es **Prometheus y Grafana OSS locales, sin suscripción ni vencimiento**, para consultar el backend de Cloud Run. El perfil, runtime y despliegue tienen planes y verificaciones separados; no se declara el scrape desplegado hasta comprobarlo. Las alternativas administradas descritas más adelante se conservan como referencia y no se activan.

**Verificación del 28/10:** la [cadena desplegada y los diez paneles](verificacion-monitoreo-desplegado-2026-10-28.md) se comprobaron contra la revisión real, con scrape fresco y nueve consultas PromQL satisfactorias. La [aceptación P1–P3](aceptacion-backend-desplegado-2026-10-28.md) conserva los indicadores M1–M9 y los incumplimientos medidos de latencia. Después de la prueba se aplicó el [estado de ahorro](verificacion-ahorro-post-pruebas-2026-10-28.md): monitoreo local apagado, mínimo cero y máximo uno en Cloud Run. El panel requiere encender los procesos locales; los controles y ese estado no garantizan disponibilidad de recordatorios con cero instancias.

## Opción elegida: servidor desplegado y panel local

El perfil `monitoring` conserva las probes `/actuator/health/liveness` y `/actuator/health/readiness` en el puerto API y permite exclusivamente `GET /actuator/prometheus` con un token máquina aleatorio de 256 bits. No acepta sesiones de propietarios, veterinarios o administradores. El token no concede acceso a recursos de la API.

El valor original permanece en un archivo privado del computador; Prometheus lo lee mediante `authorization.credentials_file` y envía el header por HTTPS. Cloud Run recibe solamente `MONITORING_TOKEN_SHA256`: guardar ese hash no permite usarlo como Bearer. Los archivos privados, datos y binarios portables quedan fuera de Git. No se agregan cuentas de servicio, claves Google, sidecars ni almacenamiento de métricas en nube.

Prometheus y Grafana escuchan solo en `127.0.0.1`. El panel y su histórico están disponibles mientras el computador y los procesos estén encendidos; no constituyen vigilancia continua cuando el equipo está apagado. El scrape cada 60 segundos usa la URL real de Cloud Run y añade la etiqueta `service_name` para las consultas del panel. Con el límite actual de una instancia, los contadores corresponden a una sola instancia por revisión; escalar exige revisar la recolección por instancia.

El [plan de seguridad del backend](plan-monitoreo-local-2026-10-25.md) define rechazos, configuración fail closed y pruebas HTTP. El [plan de publicación](plan-publicacion-cloud-run-p3-2026-10-25.md) exige reconstruir la imagen final y verificar la revisión desplegada antes de acreditar métricas. El perfil `observability` siguiente sigue disponible para desarrollo y topologías con loopback compartido; ambos perfiles son incompatibles entre sí.

Actualización del **26/10/2026, America/Bogota**: el helper
`deploy-google-cloud.ps1` usa ImageOnly por omisión. Su modo sin Execute es
offline; el modo ejecutable consulta el servicio y actualiza únicamente
la imagen con `gcloud run services update --image`, sin reconstruir variables
ni referencias. Conserva `monitoring`, `MONITORING_TOKEN_SHA256`, admisión
de login, claves/JWT, probes, CPU, recursos, identidad y máximo 1 mediante
comparación de configuración antes/después. No crea nube, IAM ni sidecars.

La [guía de despliegue](google-cloud.md#5-script-de-despliegue-y-validación-offline)
describe el plan y los manifiestos saneados fuera de Git. El helper falla
si no verifica imagen, configuración, revisión lista, tráfico y contrato;
no cambia el tráfico por su cuenta ni vuelve automáticamente a una imagen
vieja. La plantilla completa queda restringida a validación offline y admite
referencias numéricas `MONITORING_TOKEN_SHA256` y
`LOGIN_RATE_LIMIT_HMAC_SECRET`; la activación real se prepara como cambio
separado. El token Bearer original de monitoring permanece solo en el equipo,
y el secreto HMAC de login no se pasa en flags ni logs.

## Activación local y superficie accesible

Conservar las variables privadas de base de datos/JWT habituales y activar `dev,observability`. Por ejemplo, en PowerShell desde el repositorio:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,observability'
.\mvnw.cmd spring-boot:run
```

El perfil `observability` añade un servidor de gestión en `127.0.0.1:9091`. Solo permite GET de `/actuator/prometheus`, `/actuator/health/liveness` y `/actuator/health/readiness`. No requiere una sesión humana. La protección es el confinamiento de red y la lista explícita de rutas/métodos; los procesos locales/de la misma instancia pertenecen al perímetro de confianza. No publicar, redirigir ni tunelizar ese puerto a Internet.

La API conserva autenticación JWT y deniega `/actuator/prometheus` incluso con sesión. Al separar gestión, las probes públicas son GET `/livez` y `/readyz`, solo con el estado mínimo. Readiness incluye PostgreSQL; liveness no depende de servicios externos. Sin este perfil se conservan las probes anteriores `/actuator/health/liveness` y `/actuator/health/readiness`.

El arranque rechaza sobrescrituras que abran la dirección, usen el puerto API, amplíen exposición, cambien rutas o revelen detalles de salud. El puerto aleatorio de gestión solo se admite con perfil `test`.

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:9091/actuator/prometheus'
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/readyz'
```

[prometheus-local.yaml](../scripts/observability/prometheus-local.yaml) sirve para Prometheus ejecutándose en el mismo host/espacio de red. El `127.0.0.1` de un contenedor Docker separado no representa el host Windows. No ampliar la dirección de gestión para resolver esa diferencia: usar una topología con red compartida o ejecutar el recolector en el host. Si se dispone del binario, validar con `promtool check config scripts/observability/prometheus-local.yaml` antes de arrancar `prometheus --config.file=scripts/observability/prometheus-local.yaml`.

## Alternativa preparada: colector administrado en Cloud Run

[run-monitoring.yaml](../scripts/observability/run-monitoring.yaml) es configuración del sidecar administrado, **no** una especificación completa del servicio. El colector puede usar loopback porque los contenedores de una instancia Cloud Run comparten red. La ruta estándar de Actuator y el puerto requieren esta configuración personalizada; la configuración predeterminada del colector busca `/metrics` en 8080. [Guía del sidecar](https://docs.cloud.google.com/run/docs/monitoring-managed-prometheus-sidecar), [red de contenedores](https://docs.cloud.google.com/run/docs/deploying).

El usuario inicialmente permitió servicios administrados priorizando lo gratuito y después eligió la opción local para evitar pruebas con vencimiento y gasto adicional. La siguiente secuencia documenta una alternativa pendiente, sin autorización para generar gasto en efectivo; no forma parte del despliegue elegido.

Antes de desplegar I7b:

1. Verificar el plan gratuito/los créditos reales disponibles, retención, destino Grafana y permisos de lectura; registrar cualquier coste incremental antes de habilitarlo.
2. Exportar y revisar la configuración real de Cloud Run; conservar variables, referencias numéricas a secretos, montaje JWT, identidad, conexión de base, tráfico y límites actuales. No reemplazarla por un ejemplo incompleto.
3. Añadir `observability` a los perfiles existentes (`prod,external-postgres,observability` en Render). Mantener ingress en 8080 y gestión en loopback 9091, sin declarar 9091 como puerto ingress.
4. Cambiar las probes HTTP del contenedor API a `/readyz` para startup/readiness y `/livez` para liveness en 8080. Mantener tiempos/umbrales existentes, salvo una justificación medida.
5. Añadir el sidecar oficial con versión/digest revisados, recursos explícitos y dependencia de inicio respecto al contenedor API. Conservar CPU fuera de peticiones; el servicio ya la usa para trabajos persistentes.
6. Entregar la configuración personalizada en `/etc/rungmp/config.yaml` mediante el mecanismo de Secret Manager documentado. El contenido de ese archivo no es una credencial. Quien aprovisiona crea el recurso; el runtime necesita acceso limitado a esa configuración, no administración general de secretos.
7. Verificar Monitoring API, escritura de métricas y logs por la identidad correcta, con privilegios mínimos (`monitoring.metricWriter`/`logging.logWriter` según corresponda). No descargar llaves de cuenta de servicio.
8. Publicar una revisión identificable y comprobar API/probes, ausencia de scrape público, recolección de cada instancia y etiquetas `revision_name`, `service_name`, `instance`. Comparar la etiqueta de aplicación `revision` con `K_REVISION` y vincularla al commit/imagen comprobados.
9. Conectar Grafana al almacén Prometheus elegido con lectura autorizada; comprobar renovación de autenticación, permisos y consultas sobre esa revisión. [Grafana con Prometheus administrado](https://docs.cloud.google.com/stackdriver/docs/managed-prometheus/query).

ImageOnly rechaza una topología de varios contenedores; no usar el helper
para incorporar esta alternativa. La preparación del colector exige su
revisión específica. Este incremento no ejecuta despliegues ni modifica
IAM/costes.

## Panel y consultas

### Alternativa administrada priorizando cuota gratuita

Precios oficiales consultados el 25/10/2026: Grafana Cloud Free publica 10 000 series activas y 14 días de retención, sin tarjeta; Google Managed Service for Prometheus cobra USD 0.06 por millón de muestras en el primer tramo y no incluye cuota gratuita de ingesta. Como estimación, 1000 series cada 30 segundos durante 30 días producirían 86.4 millones de muestras, unos USD 5.18 de ingesta Google, sin contar colector ni otros conceptos. [Grafana Cloud Free](https://grafana.com/pricing/), [precios Google](https://cloud.google.com/products/observability/pricing).

La alternativa preparada en [grafana-cloud.alloy](../scripts/observability/grafana-cloud.alloy) usa Alloy para recoger el formato Prometheus y enviar por `remote_write` a Grafana Cloud, cuyo almacenamiento es compatible con Prometheus. Se conserva `run-monitoring.yaml` para la alternativa Google; se elige un único recolector al desplegar para evitar doble consumo. Esta compatibilidad no convierte Grafana Mimir en un servidor Prometheus propio si una rúbrica lo exigiera literalmente. [Integración oficial](https://grafana.com/docs/grafana-cloud/send-data/metrics/metrics-prometheus/prometheus-config-examples/integration-guide/).

Preparación necesaria:

- Cuenta/stack **Free** real con el usuario como propietario y acceso al panel. No convertir a Pro ni introducir un plan de pago para terminar la configuración.
- Endpoint HTTPS y tenant de métricas desde ese stack. Token con alcance de escritura de métricas únicamente, suministrado al sidecar mediante archivo secreto `/var/secrets/grafana-cloud/metrics-write-token`; no al API ni al repositorio. El archivo Alloy no contiene el valor.
- Versión/digest oficial de Alloy fijados y configuración validada con esa versión antes del despliegue. Confinar su servidor HTTP a `127.0.0.1:12345`; almacenamiento temporal privado y límites de recursos. La cola local se pierde al destruir la instancia: comprobar recuperación y señalar posibles huecos; no usarla como almacenamiento histórico.
- La identidad de instancia se obtiene exclusivamente de `/computeMetadata/v1/instance/id`, sin pedir tokens a metadata. Esto evita mezclar series de varias instancias con dirección `127.0.0.1:9091`; si no hay identidad válida, el target se omite hasta recuperarla. [Metadata de Cloud Run](https://docs.cloud.google.com/run/docs/container-contract).
- Scrape cada 60 segundos, filtro de métricas del panel y límite defensivo de 9000 muestras por scrape. Ese límite **no garantiza** quedar bajo 10 000 series del stack: sumar instancias, revisiones simultáneas, cambios de etiquetas y demás fuentes. Al exceder el límite, falla el scrape completo; no se ocultan muestras selectivamente.
- Medir series activas y uso desde Grafana antes/durante la evaluación; reducir dimensiones o buckets si hace falta conservando el umbral 500 ms. El envío a Grafana puede consumir egreso de Google y el sidecar consume CPU/memoria. El plan gratuito de Grafana no demuestra coste total cero o saldo de créditos.

En la inspección de acceso no se encontró `gcloud` en PATH ni ubicaciones estándar de Windows; el inventario de UI no tenía navegadores/sesiones disponibles. No apareció integración operativa de Grafana/Google Cloud en la búsqueda de plugins. No se leyeron credenciales, crearon cuentas, iniciaron contenedores ni habilitaron recursos. I7b necesita acceso efectivo al proyecto y stack antes de verificarlo en producción.

Importar [grownupsvet-dashboard.json](../scripts/observability/grownupsvet-dashboard.json), seleccionar una fuente Prometheus autorizada y comprobar las variables servicio/revisión. El archivo usa el modelo Classic compatible con importación; no incluye credenciales ni URL privada. [Modelo de dashboards de Grafana](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/view-dashboard-json-model/).

La revisión de aplicación procede de `K_REVISION` (o `local` fuera de Cloud Run). Las métricas HTTP conservan rutas de plantilla, método y estado; la cardinalidad de URI está limitada a 128. No se añaden IDs, correo, tokens, query strings ni contenido clínico. Los filtros del panel excluyen probes, gestión y tráfico ajeno a `/api/v1`.

Ejemplo de P95 aproximado por operación/revisión:

```promql
histogram_quantile(0.95,
  sum by (le, method, uri, revision) (
    rate(http_server_requests_seconds_bucket{application="grownupsvet-backend",uri=~"/api/v1/.*"}[5m])
  )
)
```

El histograma incorpora bucket 500 ms y rango 1 ms–30 s, además del bucket infinito. El P95 por histograma es una aproximación; el protocolo académico exige también la muestra y el cálculo exacto `t[techo(0.95 × N)]`. Conservar errores/timeouts y separar operaciones/carga/período, sin excluir fallos para mejorar el resultado. [Histogramas y percentiles](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).

## Relación con M1–M9

### Escrituras confirmadas por operación

I7a.1 añade `grownupsvet_business_commits_total{operation=...}` con siete valores cerrados: `owner_signup`, `pet_create`, `appointment_create`, `clinical_draft_create`, `clinical_draft_save`, `clinical_treatment_save` y `clinical_finalize`. Un listener incrementa el contador solo después de un commit correcto; los replays y rollbacks no añaden mutaciones. El evento interno solo contiene el enum de operación, sin datos de personas o recursos. Un fallo del registro o contador se aísla y se registra únicamente por su tipo; no cambia el resultado de negocio.

El panel de commits muestra tasa de escrituras efectivas:

```promql
sum by (operation, revision) (
  rate(grownupsvet_business_commits_total{application="grownupsvet-backend"}[$__rate_interval])
)
```

Son contadores de proceso, no auditoría durable: una caída tras commit o antes de scrape puede dejar un hueco; los reinicios reinician el valor. Usar `rate`/`increase` y contrastar con la BD y las pruebas controladas. Un registro no disponible al arrancar omite esa serie hasta reiniciar; la ausencia de serie no representa cero escrituras. No dividir estos contadores por HTTP para calcular M1/M4/M7, porque ese denominador mezcla solicitudes inválidas, replays y fallos de transporte. La finalización tampoco prueba entrega o recepción de correo. El [plan transaccional](plan-metricas-transaccionales-2026-10-25.md) detalla verificación y limitaciones.

| Indicador | Evidencia que falta además del panel |
| --- | --- |
| M1 Registro exitoso | Intentos válidos de usuario/mascota y lectura que confirme persistencia correcta. |
| M2 Rechazo correcto | Conjunto conocido de solicitudes inválidas, resultado esperado y contraste con solicitudes válidas. |
| M3 Latencia P1 | Perfil de carga, operación y revisión; muestra completa y P95 exacto. |
| M4 Creación de citas | Solicitudes elegibles y citas correctas efectivamente persistidas. |
| M5 Conflictos | Escenarios conocidos/concurrentes y oráculo de BD que demuestre el conflicto impedido. |
| M6 Latencia P2 | Separar disponibilidad, solicitud y gestión por operación/carga/revisión. |
| M7 Registro clínico | Intentos clínicos válidos y persistencia correcta en la versión clínica. |
| M8 Integridad clínica | Verificar mascota/profesional/atención en una muestra persistida. |
| M9 Latencia P3 | Operaciones clínicas separadas, carga/muestra/revisión y P95 exacto. |

Las metas son ≥99% para M1/M4/M7, 100% del conjunto conocido para M2/M5/M8 y P95 <500 ms para M3/M6/M9. Un HTTP 2xx no demuestra persistencia/integridad. Sin denominador válido se informa **No evaluado**, sin convertir ausencia de datos en cero o cien por ciento. Los paneles de CPU/JVM/pool/colas son auxiliares y no certifican esos indicadores.

No se instrumentan contadores de negocio con semántica no comprobada. La prueba HTTP local de exposición/histogramas no satisface la exigencia de medición del backend desplegado.
