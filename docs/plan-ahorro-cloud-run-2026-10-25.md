# Plan previo de ahorro entre ensayos

25/10/2026, America/Bogota. El usuario aprobó aplicar **mínimo 0 y máximo 1 después de las pruebas**, manteniendo la API accesible con cold start y aceptando que los recordatorios no se ejecuten cuando haya cero instancias. Esta decisión sustituye la preferencia anterior de mantener una instancia continua; no autoriza apagar manualmente el servicio ni modificar datos.

## Secuencia antes de cambiar configuración

1. Terminar el despliegue P3 y la verificación activa correspondiente. Conservar el digest, revisión, tráfico y evidencia de base de datos; no cambiar el mínimo durante una comprobación de correo/recordatorio.
2. Esperar a que termine el envío autorizado de las tareas de esa comprobación. Dejar cualquier etapa futura como checkpoint explícito; para reanudarla, volver a mínimo 1 y verificar readiness antes de escribir.
3. Detener Prometheus y Grafana mediante sus helpers de identidad de procesos. Un scrape cada 60 segundos mantendría tráfico hacia Cloud Run e impediría aprovechar el escalado a cero.
4. Confirmar escalado automático, una sola revisión con el 100 % del tráfico y ausencia de tags de prueba que mantengan revisiones adicionales. Conservar CPU siempre asignada, probes, memoria, concurrencia, imagen, perfil y referencias de secretos. Solo cambiar el mínimo del servicio a 0 y conservar su máximo en 1.
5. Ejecutar la actualización con proyecto/región/servicio explícitos y verificar el manifiesto resultante. Cambiar el mínimo del servicio no necesita una nueva imagen o revisión. No cambiar simultáneamente a facturación por solicitudes: las tareas de fondo y sondas actuales se diseñaron para CPU siempre asignada.
6. No realizar sondeos continuos para comprobar el apagado. Revisar la configuración mediante el plano de control; cualquier nueva visita HTTP vuelve a activar la aplicación. No confundir `min=0` con una prueba instantánea de cero instancias activas ni con un límite financiero.

## Comandos operativos

Con `$GcloudPath` apuntando al SDK oficial ya autenticado:

```powershell
# Al acabar las pruebas; antes, apagar el monitoreo local.
& $GcloudPath run services update grownupsvet-backend `
  --project=project-4f1c67ce-5621-40e0-96c --region=us-central1 `
  --min=0 --max=1 --quiet

# Antes de ensayar recordatorios o de la sustentación.
& $GcloudPath run services update grownupsvet-backend `
  --project=project-4f1c67ce-5621-40e0-96c --region=us-central1 `
  --min=1 --max=1 --quiet
```

Preparar la sustentación 10–15 minutos antes: mínimo 1, readiness saludable, nuevo login si corresponde, monitoreo local encendido y scrape de la revisión esperada. No cambiar la base de datos ni recrear el servicio para encenderlo. Después repetir la secuencia de ahorro.

## Coste y límites funcionales

Se configuran instancias mínimas, no cero CPU por instancia. Con cero instancias no hay cómputo de esas instancias; mientras exista una instancia, la facturación actual por instancia incluye su tiempo activo e inactivo. Cloud Run puede conservarla hasta unos 15 minutos desde la última petición. Visitas, tráfico automático y Prometheus vuelven a generar actividad.

El cálculo base histórico de 1 vCPU/2 GiB continuamente activos es aproximadamente US$57,024 por 30 días, antes de cuotas gratuitas y conceptos adicionales. Mínimo 0 elimina la obligación de sostener esa instancia, pero no garantiza una factura total de cero: permanecen imágenes, secretos, almacenamiento, compilaciones y tráfico. No actualizar la cuenta Free Trial a pago.

Los jobs Java no despiertan una aplicación escalada a cero. Citas, borradores e historia permanecen en PostgreSQL; recordatorios y cortes se ejecutan al volver a existir una instancia. Las dosis vencidas se omiten según la regla aprobada. Por eso una evaluación de envío con reloj real debe mantener mínimo 1 durante toda su ventana.

Fuentes primarias consultadas el 25/10: [mínimos y facturación](https://docs.cloud.google.com/run/docs/configuring/min-instances), [escalado y tareas de fondo](https://docs.cloud.google.com/run/docs/configuring/billing-settings), [precios](https://cloud.google.com/run/pricing). Este plan precede a la actualización; no declara aplicado el mínimo 0.
