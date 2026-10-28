# Verificación del monitoreo desplegado — 28/10/2026

Resultado R3: **runtime local y scrape real comprobados contra la revisión productiva vigente**. Se mantuvo el monitoreo encendido para la aceptación I8. Este incremento no modificó Cloud Run, escalado, secretos, Java, BD o correo, ni ejecutó Maven.

## Identidad y evidencia actual

- Código operativo inspeccionado: `2b3f0d01baa0a4164aef4781099a2a9ef4b9733a`, con el parsing de fechas DateTime/DateTimeOffset/ISO y fracciones de nanosegundos integrado durante R1.
- Revisión observada en las métricas: `grownupsvet-backend-admission-960c8898`.
- Imagen informada por la verificación del plano de control del agente principal: `sha256:23e561476bfed1bba13bb34dc87644444b294700dae55b159f9cf53ab93d275d`. Este ensayo no realizó consultas/mutaciones del plano de control.
- Origen comprobado: `https://grownupsvet-backend-w5glrcsyda-uc.a.run.app`.
- Corte de observación: aproximadamente **09:06 America/Bogota / 14:06 UTC** del 28/10/2026. La evidencia del 25/10 se conserva por separado y no se reutiliza como estado actual.

Reporte saneado: `output/backend-cierre-2026-10-28/observability-production-verification.json`, fuera del repositorio backend. El verificador privado no persiste métricas crudas, URI completas, credenciales o headers. Solo ejecutó GET/probes y consultas de métricas; no inició sesión humana ni escribió negocio.

## Runtime local y controles

Se validaron los ocho hashes registrados de ejecutables/configuraciones, los archivos privados de clave/admin, sus ACL usuario/SYSTEM y la coherencia de clave/hash sin imprimir valores. Se reutilizaron **Prometheus 3.15.1** y **Grafana OSS 13.2.4**. Datasource de loopback, dashboard read-only y flags de retención/admin fueron comprobados por sus APIs.

La primera apertura fría de Grafana excedió los 45 segundos originales del helper; este retiró únicamente sus procesos nuevos. El segundo intento completó el arranque. Ese fallo inicial se conserva como evidencia. La posterior prueba del acceso estable Encender terminó satisfactoriamente en unos nueve segundos.

### Plan delta R3a, escrito antes del ajuste

El agente principal autorizó una ventana anterior a I8 para mejorar el primer arranque local. Se modificará únicamente `Start-LocalMonitoring.ps1`: una variable de nombre claro fijará la espera máxima en **90 segundos**. Se conservarán los reintentos, probes, cleanup y validación de PID/ejecutable/inicio; no se añadirá parametrización, cambios de autenticación o operaciones de nube. Se validarán parser, guardas existentes y un ciclo Stop/Start desde la copia estable actualizada; al finalizar se mantendrá encendido. No se forzará una espera literal de 90 segundos ni se atribuirá a ese ensayo una prueba exhaustiva del peor arranque frío.

Resultado R3a: se aplicó `$maximumStartupWaitSeconds = 90` al deadline existente, conservando reintentos y cleanup. Parser sin errores, **21 guardas existentes PASSED** y un ciclo real Stop/Start de la nueva copia estable terminaron correctamente. Esto amplía el margen para el arranque frío observado; no se ensayó una demora artificial de 90 segundos ni se garantiza un tiempo máximo para cualquier equipo. El fallo histórico con 45 segundos permanece registrado. Evidencia: `output/backend-cierre-2026-10-28/monitoring-startup-r3a-verification.json`.

Los tres accesos propios del escritorio fueron inventariados y reinstalados mediante el instalador existente, sin sobrescribir enlaces ajenos. La nueva copia estable corresponde a:

`%USERPROFILE%\.grownupsvet\monitoring\controls\versions\1a6066476503e868`

Se probaron desde esa copia, sin abrir navegador/diálogos durante la prueba:

1. **Apagar:** terminaron únicamente los procesos registrados; no quedaron listeners 3000/9090. Durante esa parada, liveness de la API respondió **200**.
2. **Encender:** restauró los dos procesos locales y comprobó datasource/panel. La copia estable ejecuta los helpers actuales con hashes y ACL verificados.

Estado que se dejó para I8 después de R3a:

| Proceso | PID | Listener | Inicio UTC |
| --- | ---: | --- | --- |
| Prometheus | 24600 | `127.0.0.1:9090` | 14:11:16 |
| Grafana OSS | 36256 | `127.0.0.1:3000` | 14:11:16 |

Panel: <http://127.0.0.1:3000/d/grownupsvet-observability>. No se añadieron servicios, tareas, autostart o recursos de pago. El respaldo estable Render y su automatización permanecieron intactos. La parada final y el ahorro de Cloud Run se coordinan separadamente al terminar las pruebas activas.

## Scrape, autenticación y etiquetas

`Test-LocalMonitoring.ps1 -RequireRemoteUp` terminó correctamente: `up=1`, target sano y scrape fresco posterior al inicio del Prometheus actual. La muestra histórica de la TSDB anterior al arranque no se aceptó como prueba.

- Un único target canonical: `/actuator/prometheus`, intervalo **60 s**, timeout **15 s**.
- GET de métricas con la credencial máquina privada: **200**, `Cache-Control: no-store`.
- GET sin credencial a métricas y `/api/v1/pets`: **401** en ambos casos.
- Etiquetas verificadas por la datasource Grafana: `application=grownupsvet-backend`, `service_name=grownupsvet-backend`, `revision=grownupsvet-backend-admission-960c8898`.
- Etiquetas HTTP observadas: application, error, exception, le, method, outcome, revision, status y uri. No se encontraron emails, UUID o query strings en las URI de esa muestra; se registró solo su cantidad, sin publicar sus valores.

## Familias y consultas de los diez paneles

Se encontraron uptime, memoria JVM, conexiones Hikari, HTTP count/sum/bucket y `grownupsvet_business_commits_total`. El bucket **`le="0.5"`** apareció tanto en el endpoint real como en Prometheus consultado mediante Grafana (cuatro series en esa instantánea). Las siete etiquetas de operación de negocio estuvieron presentes.

El dashboard tuvo diez paneles: uno de texto y nueve consultas PromQL ejecutadas satisfactoriamente mediante la datasource provisionada. Se sustituyeron variables de servicio/revisión por la revisión actual y el intervalo de rate por cinco minutos.

| Panel de consulta | Series en el corte |
| --- | ---: |
| P95 HTTP aproximado | 1 |
| Peticiones por segundo | 1 |
| 5xx por segundo | 0 |
| Estados HTTP | 1 |
| Heap JVM | 1 |
| Conexiones PostgreSQL | 1 |
| Disponibilidad del scrape | 1 |
| Uptime del proceso | 1 |
| Commits por operación | 7 |

La consulta de 5xx fue válida y no produjo series en ese corte; eso no demuestra ausencia histórica de errores. Las siete series de commits acreditan su presencia, no un volumen mínimo de negocio. `instance` identifica el target HTTPS, no una JVM física de Cloud Run.

Este ensayo acredita transporte, configuración y datos reales disponibles en Grafana. No constituye carga sostenida, P95 exacto de cliente, numeradores/denominadores de aceptación o certificación M1–M9; esos resultados corresponden a la aceptación separada de I8.

Después del último control de I8 se apagaron los dos procesos locales y se dejó Cloud Run con mínimo cero y máximo uno. La evidencia y los límites de ese estado posterior están en [verificación de ahorro](verificacion-ahorro-post-pruebas-2026-10-28.md). Los PID y el `up=1` anteriores describen la ventana de prueba; no implican monitoreo permanente.
