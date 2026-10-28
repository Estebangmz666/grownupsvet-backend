# Plan previo de reanudación del cierre

28/10/2026, America/Bogota. Se retoma el objetivo completo del plan del 23/10, conservando todas las decisiones clínicas aprobadas, el presupuesto adicional de US$0 y el umbral de parada al detectar 20% o menos de cupo semanal disponible. No se retoma automáticamente una ejecución interrumpida ni se presupone que el estado del 25/10 siga vigente.

## Evidencia inicial y límites

- Este checkout conserva `7368058` y pendientes operativos del 25/10. El remoto main reporta `70b772a`; hay commits posteriores de auditoría y worktrees independientes.
- El chat de auditoría figura inactivo. Su entrega declara P1/P2 web y P3 API, con limitación de login y un kit de BD hasta V23. Sus resultados se contrastarán con Git, CI y despliegue; no se reutilizan como instrucciones para ejecutar acciones nuevas.
- Cloud Run está en la revisión `grownupsvet-backend-admission-960c8898`, imagen `sha256:23e561476bfed1bba13bb34dc87644444b294700dae55b159f9cf53ab93d275d`, perfiles `prod,external-postgres,monitoring`, una revisión con tráfico 100%, CPU siempre asignada, 1 CPU/2 GiB y mínimo/máximo 1.
- Los manifiestos operativos y checkpoints privados quedan fuera de Git. La configuración nueva de auditoría, sus secretos y sus migraciones se preservan; no se despliega la imagen anterior de P3 sobre ella.

## Incremento R1: recuperar una base actual y preservar pendientes

1. Inventariar los archivos pendientes propios y guardar fuera de Git una copia ZIP y manifiesto SHA256. Excluir cachés y no incluir archivos privados de configuración o credenciales.
2. Confirmar el commit remoto y su CI, comparar sus rutas modificadas con los pendientes locales y validar el contenido actual del contrato y migraciones.
3. Actualizar esta rama a main mediante fast-forward si es compatible con los pendientes. Ante un solapamiento, conservar ambas versiones y resolver expresamente el contenido, sin reset destructivo, stash global ni edición en el worktree de auditoría.
   El único solapamiento localizado es el parsing de `lastScrape` en `Test-LocalMonitoring.ps1`: auditoría añadió DateTime/DateTimeOffset y cultura invariante; el pendiente local añade además compatibilidad con fracciones Go de nanosegundos. Registrar los pendientes propios en un commit local de esta rama todavía no publicada, rebasarlo sobre main y conservar la versión que cubre ambas condiciones. Ejecutar la suite de 14 casos de auditoría y añadir el caso real de fracción de nueve dígitos antes de publicar.
4. Revisar los helpers de monitoreo pendientes y el cambio de parsing de fechas, preservando el dashboard fuente y las guardas de archivos/procesos. Ejecutar únicamente su verificación pertinente; la regresión Java ya acreditada en CI se repite solo si cambia código Java/configuración o aparece una preocupación concreta.

## Incremento R2: cerrar aceptación real del despliegue vigente

1. Leer el checkpoint privado con campos seleccionados y contrastarlo con consultas de BD en transacciones read-only con TLS verify-full. Un checkpoint sirve para localizar el ensayo, no prueba por sí solo que una operación terminó.
2. Adaptar explícitamente el harness al contrato, migraciones y límite de login vigentes antes de ejecutarlo. Mantener redirecciones desactivadas, destinatario real previamente autorizado, allowlist de direcciones ficticias, mutex de ejecución y ausencia de contraseñas/JWT/códigos en reportes.
3. Reanudar la misma intención idempotente cuando exista. Si una escritura no idempotente quedó incierta, reconciliar primero sus identificadores mediante API/BD; no volver a crear a ciegas. Conservar las fechas originales y no falsear el reloj para finalizar.
4. Comprobar P1–P3, roles, relaciones y persistencia, finalización/COMPLETED e historia. Para el único ensayo clínico de correo, actualizar el tratamiento mientras siga DRAFT con una sola dosis futura y un control futuro a menos de 24 horas; después de finalizar no editarlo.
5. Mantener mínimo 1 durante la ventana de la dosis y control. Verificar estado durable y aceptación SMTP de cada aviso; la recepción en buzón y contenido requieren evidencia propia. No leer OTP ni provocar mensajes a destinatarios nuevos.
6. Registrar M1–M9 por operación con numeradores/denominadores, muestras, errores/timeouts y P95 exacto; mantener separado el histograma aproximado de servidor. No presentar una muestra funcional pequeña como garantía poblacional ni borrar un resultado que incumpla una meta.

## Incremento R3: observabilidad y entrega verificables

1. Comprobar los procesos locales por ejecutable/PID/inicio y sus sockets loopback; reutilizar o iniciar solamente las instalaciones propias verificadas. Contrastar scrape y consultas Grafana con la revisión productiva actual.
2. Completar la evidencia pública con commit, imagen, revisión, contrato, resultados de aceptación y límites reales. Actualizar los pendientes históricos sin cambiar el alcance aprobado. Conservar el corte histórico de auditoría y no sobrescribir su paquete.
3. Publicar los cambios propios mediante PR y los gates exigidos por main. No reconstruir/desplegar una imagen si solo cambian documentación o helpers locales; verificar la correspondencia del código de aplicación en la imagen existente.

## Incremento R4: dejar el ahorro aprobado aplicado

1. Después de los ensayos activos, verificar que terminó el envío autorizado y detener Prometheus/Grafana por sus helpers de identidad. Conservar historial, secretos y datos.
2. Aplicar mínimo 0/máximo 1 en el servicio Cloud Run, preservando imagen, tráfico, CPU, probes, variables y referencias de secretos. Verificar el manifiesto por el plano de control; no hacer sondeos HTTP que impidan dormir al servicio.
3. Entregar los controles locales y el procedimiento para volver a mínimo 1 antes de la sustentación. El backup programado del 13/11 usa Render directamente y no depende de mantener Cloud Run encendido.

Cada incremento se cierra con evidencia de su alcance. Si una dependencia externa impide un requisito, se documenta el requisito pendiente y se continúa con los pasos independientes; no se declara completo el objetivo con evidencia ausente.
