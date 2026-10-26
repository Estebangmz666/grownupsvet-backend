# Verificación de configuración de observabilidad con CLI oficiales

25 de octubre de 2026, America/Bogota. Validación local de archivos de configuración, sin ejecutar recolectores, consultar metadata de Cloud Run ni enviar métricas a un servicio remoto.

## Resultado

| Herramienta | Versión exacta | Archivo comprobado | Comando | Salida |
| --- | --- | --- | --- | --- |
| promtool | 3.15.1, revisión `bae3eced0a5369e050e83ea73bc381d7c4b1a719` | `scripts/observability/prometheus-local.yaml` | `promtool check config scripts/observability/prometheus-local.yaml` | Exit 0, configuración Prometheus válida |
| Grafana Alloy | 1.21.2, revisión `a69b1ae` | `scripts/observability/grafana-cloud.alloy` | `alloy validate scripts/observability/grafana-cloud.alloy` | Exit 0, sin diagnósticos |

También se ejecutó `--version` en ambos binarios, con salida 0. La validación usó los archivos originales del repositorio y no requirió corregirlos.

## Procedencia y SHA256

Se descargaron los archivos portátiles Windows AMD64 de los repositorios oficiales, fuera del repositorio backend. Cada SHA256 se comparó tanto con el archivo de sumas de la misma release como con el digest de su asset publicado por GitHub, antes de ejecutar los binarios.

| Archivo | SHA256 verificado |
| --- | --- |
| `prometheus-3.15.1.windows-amd64.zip` | `ba25b5e590520cb9fb028edef99bdc8523244d2d489b8c629e0d453d94fb030b` |
| `alloy-windows-amd64.exe.zip` de v1.21.2 | `d57d63cf9f3033d521454e44f88aee4daa229a8a148b5123ff51bcc0a3dae819` |

Fuentes: [Prometheus 3.15.1](https://github.com/prometheus/prometheus/releases/tag/v3.15.1) y sus [sumas SHA256](https://github.com/prometheus/prometheus/releases/download/v3.15.1/sha256sums.txt); [Grafana Alloy 1.21.2](https://github.com/grafana/alloy/releases/tag/v1.21.2) y sus [sumas SHA256](https://github.com/grafana/alloy/releases/download/v1.21.2/SHA256SUMS). Las versiones coinciden con las releases estables observadas al verificar, sin depender de `latest` para repetir los comandos.

Los binarios portátiles, archivos de sumas, registros de versión y validación y `validation-manifest.json` permanecen en `output/backend-cierre-2026-10-25/observability-tools` del directorio padre del backend. El manifiesto conserva las huellas de las configuraciones que se comprobaron. No se instaló un servicio, modificó PATH ni generaron credenciales.

## Variables ficticias de Alloy

La validación estática recibió únicamente valores de prueba en el proceso de PowerShell:

```text
GRAFANA_CLOUD_PROMETHEUS_URL=https://example.test/api/prom/push
GRAFANA_CLOUD_PROMETHEUS_USER=tenantfixture
K_SERVICE=grownupsvet-fixture
K_REVISION=grownupsvet-fixture-revision
```

Alloy no exigió que existiera el archivo Linux del token durante `validate`; no fue necesario crear una copia temporal ni leer un secreto. Se validó el archivo original. No se ejecutó `alloy run`, Prometheus servidor, remote_write, una consulta de metadata ni una autenticación real.

## Alcance y límites

`promtool check config` acredita que el archivo local tiene una configuración Prometheus válida. `alloy validate` comprueba sintaxis, componentes, propiedades requeridas y propiedades desconocidas; su alcance está descrito en la [documentación oficial de validate](https://grafana.com/docs/alloy/latest/reference/cli/validate/). Las opciones de Promtool se documentan en su [referencia oficial](https://prometheus.io/docs/prometheus/latest/command-line/promtool/).

Estos resultados no prueban conectividad con el endpoint Micrometer, identificación real de una instancia Cloud Run, permisos o cuotas de Grafana Cloud, presencia del token montado, ingesta, persistencia ni visualización de series en un panel. Tampoco acreditan indicadores M1–M9 del backend desplegado. La configuración de RunMonitoring, cuando se utilice, requiere su herramienta y formato correspondientes; no se presentó como un archivo Prometheus ni se certificó mediante promtool.
