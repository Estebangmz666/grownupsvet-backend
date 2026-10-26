# Monitoreo local Windows: Grafana OSS y Prometheus

La alternativa elegida usa **Grafana OSS 13.2.4** y **Prometheus 3.15.1** portátiles, sin suscripción, trial, servicio Windows, Docker o autostart. Toda la instalación y su información permanecen en `%USERPROFILE%\.grownupsvet\monitoring`, fuera de Git. [Plan previo del runtime](plan-runtime-monitoreo-local-2026-10-25.md) y [contrato del backend](plan-monitoreo-local-2026-10-25.md).

Panel: <http://127.0.0.1:3000/d/grownupsvet-observability>. Prometheus: <http://127.0.0.1:9090>. Ambos escuchan exclusivamente en **127.0.0.1**. Grafana permite Viewer anónimo local, sin permisos de guardar/editar; la cuenta `local-admin` tiene contraseña aleatoria privada. No se necesita conocerla para consultar el panel.

## Comandos

Desde la raíz del backend, con PowerShell 7.4 o posterior:

```powershell
# Instala/configura; rechaza reinstalar mientras los procesos propios siguen activos.
.\scripts\observability\local\Install-LocalMonitoring.ps1

# Arranque oculto e idempotente; verifica identidad de procesos y puertos.
.\scripts\observability\local\Start-LocalMonitoring.ps1

# Comprobación local sin modificar recursos remotos.
.\scripts\observability\local\Test-LocalMonitoring.ps1

# Después de desplegar el perfil monitoring y su hash, exige scrape remoto reciente.
.\scripts\observability\local\Test-LocalMonitoring.ps1 -RequireRemoteUp

# Detiene exclusivamente los dos procesos identificados y conserva sus datos.
.\scripts\observability\local\Stop-LocalMonitoring.ps1

# Guardas reproducibles con fixtures sintéticos; no modifica credenciales reales.
.\scripts\observability\local\Test-LocalMonitoringGuards.ps1
```

El instalador reutiliza el ZIP verificado de Prometheus localizado en `output/backend-cierre-2026-10-25/observability-tools` del workspace padre. `-PrometheusToolsDirectory` permite indicar su ubicación si el checkout cambia. El paquete Grafana OSS se descarga de su distribución oficial y se verifica antes de extraer; no se usa el instalador Enterprise. Una reinstalación valida primero el manifiesto existente, por lo que un binario/configuración alterado no recibe un hash nuevo silenciosamente. Un directorio de herramientas preexistente sin procedencia se rechaza.

No se detienen procesos por nombre/puerto. `processes.json` guarda PID, ruta y hora de inicio; start/stop comprueban las tres propiedades y mantienen el handle del proceso al detenerlo. Un puerto ocupado, PID reutilizado o ejecutable ajeno aborta. Si una instalación quedó incompleta, revisar su etapa y el árbol privado antes de recuperar herramientas; no borrar datos TSDB/SQLite ni otros directorios del usuario para reintentar.

## Credencial y despliegue

Se generan 32 bytes CSPRNG como Base64URL de 43 caracteres sin padding. La clave original está exclusivamente en `secrets\scrape-token.txt`, con ACL explícita usuario/SYSTEM. Prometheus la lee mediante `authorization.credentials_file`; no se envía al navegador, argv o Git.

`secrets\backend-monitoring.env` contiene **solo** `MONITORING_TOKEN_SHA256=<hex64>`. Ese es el archivo que debe leer quien prepare la variable de entorno del backend. El hash se calcula sobre los 43 caracteres exactos UTF-8. **No cargar el archivo de clave original al backend.** El instalador no rota una clave existente; detecta ausencias, corrupción o incoherencia de hash. La normalización/validación de ACL también evita reutilizar secretos con permisos explícitos de otros usuarios.

`secrets\grafana-admin-password.txt` y `secrets\grafana-secret-key.txt` son independientes de la clave de scrape. Grafana los lee con file provider. Se verifica formato y ACL al instalar/arrancar/comprobar. Los procesos hijos reciben un entorno reducido, sin secretos heredados de aplicación/nube.

El runtime consulta el origen ya usado por el gateway, `https://grownupsvet-backend-w5glrcsyda-uc.a.run.app`, cada **60 segundos**, timeout 15 segundos, TLS con CA/hostname válidos, sin redirects, endpoint exacto `/actuator/prometheus`. El perfil `monitoring` debe estar desplegado con el hash anterior. Antes de ese despliegue se espera `up=0` o `PENDING_FIRST_SCRAPE`; un runtime local sano no acredita todavía métricas remotas.

No se crearon recursos cloud ni servicios de pago. Los scrapes usan el Cloud Run existente y sus cuotas; su consumo se suma al resto del tráfico. El equipo Windows debe estar encendido y los procesos activos para recolectar datos.

## Datos, panel y evidencia

- Prometheus: `data\prometheus`, retención configurada de 30 días y 512 MB. El límite de retención puede acortar el historial; los archivos de herramientas, WAL/head, logs y SQLite también ocupan disco. No se garantiza un directorio total de 512 MB ni un mes completo de datos.
- Grafana: `data\grafana`, datasource provisionada `grownupsvet-local-prometheus`, apuntando a `127.0.0.1:9090`.
- Dashboard: copia privada read-only del JSON fuente `scripts/observability/grownupsvet-dashboard.json`, sin modificar ese original. Se fija datasource y selector de servicio. El panel `up` muestra disponibilidad de scrape del servicio; no atribuye una revisión o instancia física al target HTTPS.
- Las series de aplicación conservan `application`/`revision` del backend y `service_name=grownupsvet-backend` del scrape. `instance` representa el target recolectado; Cloud Run puede cambiar la JVM que atiende una petición.
- `installation.json` registra fuentes, versiones y hashes de ejecutables/configuraciones. `verification.json` registra sockets, APIs, datasource y panel. `guard-verification.json` registra las pruebas sintéticas. Ninguno publica claves, headers o credenciales.

`-RequireRemoteUp` exige simultáneamente muestra `up=1`, target sano y último scrape reciente posterior al arranque del Prometheus actual. No acepta una muestra vieja de la TSDB como prueba del estado actual. Esa comprobación acredita scrape; los histogramas, percentiles y operaciones de negocio requieren además comprobar sus familias y generar tráfico pertinente. No equivale por sí sola a certificar M1–M9.

## Verificación del 25/10/2026

Se comprobaron SHA oficiales de los paquetes, `promtool check config`, instalación, arranque oculto, datasource/panel por API, Viewer sin guardado, diez paneles y sockets loopback. Se ensayaron arranque idempotente, parada y nuevo arranque con persistencia conservada. La suite de guardas completó **21 comprobaciones**, incluyendo PID/ejecutable ajeno, hash alterado en memoria, puerto ocupado, ACL adicional rechazada/normalizada y secreto sintético vacío.

Paquetes verificados:

| Herramienta | Archivo | SHA-256 |
| --- | --- | --- |
| Prometheus 3.15.1 | `prometheus-3.15.1.windows-amd64.zip` | `ba25b5e590520cb9fb028edef99bdc8523244d2d489b8c629e0d453d94fb030b` |
| Grafana OSS 13.2.4 | `grafana_13.2.4_38252365904_windows_amd64.tar.gz` | `3e005ff50fe3bb6b8cb428ef127636fac4ee676bf9b6d819c6a2297d21f4bcbd` |

El resultado inicial acredita el runtime local. El reporte se actualiza al ejecutar el comprobador; el agente principal realizará por separado el despliegue de `monitoring` y la comprobación del scrape real.

Fuentes: [Grafana OSS Windows 13.2.4](https://grafana.com/grafana/download/13.2.4?edition=oss&platform=windows), [Prometheus 3.15.1](https://github.com/prometheus/prometheus/releases/tag/v3.15.1), [configuración de Prometheus](https://prometheus.io/docs/prometheus/latest/configuration/configuration/), [configuración/file provider de Grafana](https://grafana.com/docs/grafana/latest/setup-grafana/configure-grafana/) y [provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).
