# Plan previo: activación explícita de revisión Cloud Run

28/10/2026. Este plan se escribe antes del cambio. El preflight real encontró tráfico fijado a `grownupsvet-backend-admission-960c8898`; actualizar la imagen no implica que esa revisión nueva reciba tráfico. El incremento solo cambia el helper PowerShell y sus dobles/pruebas/documentación. No ejecutará nube, builds, API, Maven ni publicación; el agente principal conserva esas operaciones.

## Contrato y alcance

1. Añadir `-ActivateRevision`, opt-in y exclusivo de `ImageOnly`. Sin `-Execute` sigue siendo planificación offline y registra la intención. `ConfigurationTemplate` rechaza la opción. Sin el switch, el helper nunca ejecuta `update-traffic` y falla si la revisión de imagen no recibe el único100% esperado.
2. Conservar imagen fijada por digest, perfiles, variables, referencias numéricas de secretos, probes, JWT, CPU, recursos e identidad. Conservar también mínimo0/máximo1 tal como estén en la huella existente; no introducir flags de configuración. No editar `src/main`, `pom.xml`, Dockerfile o migraciones: la imagen ya construida de `fa2a9955` permanece aplicable.
3. Solo admitir la topología de tráfico actual de una asignación100% sin tags, fijada a una revisión o flotante hacia latest. Rechazar splits/tags antes de actualizar. La activación nunca borra tags ni utiliza `--to-latest`/`LATEST`.

## Secuencia y detección de interferencia

1. Leer servicio completo y contrato público como hoy; conservar evidencia saneada y huella de configuración. Validar tráfico de especificación y estado. Después del contrato, releer y exigir el mismo estado antes de enviar `update --image`.
2. Capturar la descripción devuelta por `update --image` como identidad de esta operación. Exigir nombre de servicio, imagen inmutable exacta, configuración idéntica y `latestReadyRevisionName == latestCreatedRevisionName`, no vacíos y pertenecientes al servicio. Leer de nuevo y contrastar revisión/configuración/imagen/tráfico/generación con ese resultado para detectar un despliegue ajeno intercalado.
3. El update de imagen puede conservar el tráfico fijado anterior o resolver una asignación latest existente hacia la nueva revisión. No admitir que cambie la intención de tráfico de `spec.traffic`; para tráfico fijado exigir además que el destino anterior observado se conserve. Guardar el checkpoint de imagen, sin declararlo activado.
4. Si la revisión ya recibe único100% y no hay tags, omitir activación aunque se haya pasado el switch. Si no recibe tráfico y no hay switch, guardar resultado no verificado y fallar.
5. Si se pidió activar y las guardas anteriores pasan, releer inmediatamente antes del cutover y exigir que el estado siga igual. Ejecutar una sola vez `gcloud run services update-traffic <service> --to-revisions=<revision-exacta>=100`, con proyecto/región explícitos. No derivar el destino de un latest posterior ni reintentar la mutación.
6. Releer tras la activación y exigir misma revisión/configuración/digest y único100% fijado a esa revisión, sin tags, tanto en spec como en status. Tras leer el contrato público, hacer una última lectura para detectar cambios mientras se verificaba el contrato. Solo entonces escribir `Verified=true`.
7. En fallo, conservar checkpoints/resultados con `Verified=false`, indicar si se intentó activar y no restaurar ni reintentar automáticamente. No imprimir cuerpo remoto, entorno, tokens o stderr. Mantener escrituras de evidencia exclusivas y fuera del repositorio.

La comparación del estado reutilizará canonicalización/huellas existentes y añadirá solo campos de identidad/revisión/generación/tráfico necesarios. Las lecturas detectan divergencias observadas entre fases; el CLI no proporciona aquí una transacción que abarque todas las llamadas. No se promete eliminar la ventana entre la última lectura y la mutación; un cambio observado obliga a abortar y revisar. La propagación de tráfico tampoco implica que peticiones anteriores hayan terminado.

## Pruebas offline antes de entregar

Ampliar `scripts/tests/DeployGoogleCloud.Tests.ps1`, conservando el doble funcional de SDK/HTTP dentro del proceso y la limpieza restringida del directorio temporal. Contar las mutaciones por comando/flags para comprobar que no aparecen flags de configuración, `--to-latest` ni una segunda activación.

- Tráfico fijado sin switch: actualización de imagen, cero activaciones y resultado falso.
- Tráfico fijado con switch, revisión lista/configuración/digest correctos: exactamente una activación a la revisión capturada, luego único100% sin tags y contrato esperado.
- Revisión no lista, digest equivocado o configuración alterada: cero activaciones y fallo.
- Latest ya resuelto a la nueva revisión: cero activaciones, incluso con switch.
- Cambio ajeno de tráfico/revisión/configuración antes de actualización, antes de activar y después de activar: aborto, sin otra mutación ni restauración.
- Split/tag, opción en ConfigurationTemplate y planificación offline: rechazo o ausencia de red según corresponda.
- Conservar pruebas existentes de configuración/secreto/cap/contrato y comprobar evidencia sin valores privados.

Ejecutar únicamente parser PowerShell y esta suite con SDK simulado; no ejecutar el helper contra Cloud Run. Documentar default/opción y el mayor número de lecturas; enviar el código congelado al agente principal. PR8 y despliegue real requieren su coordinación, sin ampliar alcance.

## Delta previo de integración CI

El agente principal comprobó que la suite PowerShell de despliegue no tiene un invocador Java directo. Antes de publicar se añadirá un paso explícito del job requerido `Backend verification`, con `shell: pwsh`, para ejecutar `DeployGoogleCloud.Tests.ps1` y sus 31 escenarios simulados. El job ya exige PowerShell 7; esta suite sustituye SDK/HTTP en el propio proceso y no necesita credenciales o herramientas Google. Se comprobarán parser, ejecución local de esa suite y actionlint; la CI remota ejecutará también la regresión Java sin modificar fuentes de aplicación ni reconstruir la imagen existente.

Resultado local del agente principal: los **31 escenarios**, incluidos 18 de activación, pasaron con `CloudActionsExecuted=false`; actionlint pasó para el workflow ampliado. La ejecución real espera la CI de este incremento. No hay cambios en las 292 fuentes de la imagen ya construida.

## Fuentes primarias consultadas

- [CLI update-traffic](https://docs.cloud.google.com/sdk/gcloud/reference/run/services/update-traffic): asignación explícita por revisión y distinción con latest/tags.
- [Cloud Run: migración de tráfico](https://docs.cloud.google.com/run/docs/rollouts-rollbacks-traffic-migration): persistencia de asignaciones anteriores entre despliegues y propagación no instantánea.

## Resultado local del incremento

Helper y suite escritos conforme a la secuencia anterior. El parser PowerShell de ambos archivos pasó sin errores. `pwsh -NoProfile -File scripts/tests/DeployGoogleCloud.Tests.ps1` pasó **31 escenarios funcionales**, incluidos **18 de activación**; los dobles SDK/HTTP se mantuvieron dentro del proceso, sin credenciales ni nube (`CloudActionsExecuted=false`). Se conservaron los13 casos previos de configuración, secreto, cap y contrato.

Las guardas adicionales acreditan rechazo de nombres de revisión vacíos, configuración/digest incorrectos, topología con tag/split, cambio ajeno de configuración antes de imagen, revisión posterior distinta, tráfico antes de activar, generación distinta, configuración posterior al cutover, tráfico cambiado durante el contrato y un SDK que retorna éxito sin aplicar el cutover. Esos resultados se refieren a dobles funcionales, no a un despliegue real.

El diff queda restringido al helper, sus pruebas, este plan y las guías de despliegue/observabilidad. No se modificaron fuentes de aplicación, pom o Dockerfile y no se ejecutaron Maven, Cloud Run, build o publicación. La imagen inmutable construida para `fa2a9955` sigue siendo el artefacto de aplicación previsto. El agente principal conserva la revisión/publicación PR8 y toda operación real posterior.

La revisión independiente del diff no encontró defectos materiales. Se precisó en la guía que, si el SDK falla antes de otra lectura, `.after.json` es el último estado observado y no una fotografía fresca posterior al fallo; `Stage`, `ActivationAttempted` y `Verified=false` deben interpretarse juntos. No hubo nuevas ejecuciones reales para completar esa evidencia.
