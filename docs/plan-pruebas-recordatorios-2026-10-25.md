# Verificación I6 antes de escribir las pruebas

25/10/2026, America/Bogota. Complementa el plan productivo de recordatorios; no amplía sus reglas. La implementación se prueba con PostgreSQL real, reloj mutable y JavaMailSender capturador en memoria, sin contactar SMTP externo.

## Aislamiento y coordinación

`ClinicalReminderIntegrationTests` tendrá esquema UUID propio, sin transacción exterior de prueba. Se crean y confirman fixtures por los servicios reales. Los schedulers de citas se sustituyen para impedir envíos ajenos a los escenarios; `grownupsvet.clinical.reminders.polling-enabled=false` deja los workers manuales disponibles. Maven lo ejecuta exclusivamente la raíz después de congelar implementación, pruebas y migraciones.

El sender conserva MIME y estado de transacción al enviar, permite fallar un número acotado de tentativas y ofrece barreras para probar un worker cuyo lease envejece. Un hook al construir MIME puede avanzar el reloj o desactivar al destinatario antes de llamar al transporte. La prueba verifica que la revalidación impide entonces transmitir. Solo se consultan metadatos/IDs de los fixtures; no hay datos o credenciales reales.

## Escenarios y oráculos

1. Finalización con una prescripción y un control crea dos programaciones compactas, ninguna fila por dosis futura. Replay no duplica. Un fallo al final del listener revierte atención/cita/auditoría/programación.
2. Materializar antes de inicio no crea tarea; hacerlo en la dosis exacta crea una. Repetir y competir entre dos materializadores conserva la unicidad. SMTP está fuera de transacción y confirma solo el lease propio.
3. Una interrupción prolongada salta aritméticamente dosis vencidas, registra omisiones y conserva únicamente ocurrencias entregables de la ventana de cinco minutos. Reconstruir los workers usa la misma BD. Ningún procesamiento produce una ráfaga de recuperación ni miles de filas anticipadas.
4. Control a más de 24 horas se programa 24 horas antes; con menor anticipación se programa inmediato. Controles vencidos no crean avisos útiles y nunca se envían tras su fecha.
5. Alcanzar el fin exclusivo del medicamento o la expiración de tarea impide SMTP/reintentos. El hook de `createMimeMessage` comprueba también la última revalidación después de construir el mensaje.
6. Propietario/mascota inactivos suprimen ocurrencias. Reactivar solo permite futuras dosis, sin reponer antiguas. Cambiar la dirección durante la construcción no reutiliza un destinatario obsoleto.
7. Dispatcher concurrente no duplica un lease vigente. Un segundo worker recupera lease vencido; la confirmación/fallo tardíos del anterior no sobrescriben estado ni token nuevos.
8. Fallo SMTP conserva código técnico, backoff y límite de intentos; no amplía vigencia. El vencimiento posterior corta reintentos. La ausencia de configuración no consume intentos inútiles.
9. MIME de texto/HTML usa los datos de la mascota/tratamiento correctos y escape de contenido `<`, `&` y comillas. El asunto no expone diagnóstico.

Las barreras/timestamps permiten intercalaciones deterministas, con timeouts y sin sleeps. La garantía externa sigue siendo al menos una vez: no se interpreta un lease como exactamente una vez frente a una caída después de aceptación SMTP. Esta suite no acredita entrega Gmail ni operación de la revisión desplegada.

## Resultado local comprobado

La ejecución focal coordinada por la raíz produjo `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.ClinicalReminderIntegrationTests.xml` en `target/surefire-reports`: **18 pruebas, cero fallos, errores u omisiones**. Se comprobó la matriz implementada con PostgreSQL real y MIME capturado en memoria, incluidos rollback del listener, reconstrucción del materializador, concurrencia, retención del lease nuevo frente a confirmación obsoleta y revalidación después de construir MIME. SMTP externo y la revisión desplegada continúan sin acreditarse mediante esta suite.
