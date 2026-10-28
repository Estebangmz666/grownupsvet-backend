# Ahorro después de pruebas — verificación del 28/10/2026

Aplicado después del corte clínico14:48:05Z, que acreditó fin de receta, una ocurrencia por fuente y los tres avisos previstos aceptados por SMTP. El usuario confirmó recepción; el problema de codificación en datos ficticios se documenta por separado. Se siguió el [plan de ahorro aprobado](plan-ahorro-cloud-run-2026-10-25.md).

## Monitoreo local detenido

A las 14:51:42Z (09:51:42 Bogotá), el helper existente `scripts/observability/local/Stop-LocalMonitoring.ps1`, sin parámetros, identificó por PID, ejecutable e inicio y detuvo exclusivamente Prometheus 24600 y Grafana 36256, iniciados 14:11:16Z. La comprobación de sistema operativo confirmó estado vacío, ausencia de ambos procesos y cero listeners 3000/9090. Se conservaron TSDB, SQLite, configuración y secretos. No se detuvo por nombre/puerto ni se modificó el backup o su automatización.

```powershell
.\scripts\observability\local\Stop-LocalMonitoring.ps1
```

No se realizaron sondeos HTTP de API/salud/métricas después de la parada.

## Cloud Run: mínimo 0 y máximo 1

Se utilizó el SDK oficial portátil 590.0.1 ya autenticado, con proyecto/región/servicio explícitos. El preflight proyectado verificó generación 10 observada, mínimo 1/máximo 1 del servicio, mínimo 0 de revisión, revisión `grownupsvet-backend-admission-960c8898` al 100% sin tags y digest esperado. No se imprimieron manifiestos completos ni valores de entorno.

A las 14:54:41Z (09:54:41 Bogotá) terminó con código0 el único cambio:

```powershell
& $GcloudPath run services update grownupsvet-backend `
  --project=project-4f1c67ce-5621-40e0-96c --region=us-central1 `
  --min=0 --max=1 --quiet --format=none
```

`$GcloudPath` corresponde al SDK portátil verificado en el directorio output del25/10; no se instaló globalmente ni se cambió PATH. Las verificaciones posteriores usaron `run services describe` y `run revisions describe` con proyección JSON de escalado/generación/revisión/digest/tráfico/recursos/probes, excluyendo env y secretos.

| Propiedad | Antes | Después |
| --- | --- | --- |
| Mínimo del servicio |1|0(default al retirar minScale tras --min=0)|
| Máximo del servicio |1|1|
| Generación / observada |10/10|11/11|
| Revisión / tráfico |admission-960c8898 /100%|misma /100%|
| Nueva imagen o revisión |—|ninguna|
| CPU / memoria / concurrencia |1vCPU /2GiB /8|sin cambios|
| CPU throttling |false|false|
| Startup / liveness probes |readiness / liveness actuales|sin cambios|

La imagen sigue siendo `sha256:23e561476bfed1bba13bb34dc87644444b294700dae55b159f9cf53ab93d275d`. La proyección completa de la revisión y la plantilla seleccionada son idénticas antes/después. La revisión inmutable conserva perfiles, variables y referencias de secretos; el comando solo cambia escalado del servicio. El máximo histórico 20 de la revisión no se alteró; se conserva el máximo 1 configurado al nivel del servicio.

## Evidencia y límites

Se guardaron fuera de Git `cloud-savings-{service,revision}-{before,after}.json`, `monitoring-stop-{owned-before,r4-verification}.json` y `cloud-savings-r4-verification.json`, con fecha y SHA256, en output/backend-cierre-2026-10-28 del directorio padre. Todos son proyecciones sanitizadas; las credenciales y datos privados no se publican.

La API permanece disponible y puede arrancar ante una petición. Mínimo0 permite escalar a cero; no prueba que la instancia haya desaparecido inmediatamente ni garantiza gasto total cero. Con cero instancias no corren jobs Java ni recordatorios; las dosis vencidas se omiten conforme a la regla aprobada. Antes de un ensayo de correo se debe volver a mínimo 1 y mantenerlo durante toda su ventana, según el plan. Se dejó el monitoreo apagado para evitar tráfico periódico que mantenga despierto el servicio.

No hubo nueva petición de negocio, SMTP, SQL, compilación, despliegue de imagen, commit o push para esta operación. El objetivo completo del cierre requiere revisión de las evidencias restantes; este documento acredita únicamente R4.
