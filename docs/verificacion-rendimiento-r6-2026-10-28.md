# R6 verificado localmente: P95 todavía no demostrado

28/10/2026. La regresión completa local de R6 terminó correctamente: **764 pruebas, 65 suites, cero fallos, errores u omisiones**. Duró **4 min 27 s** y finalizó a las **13:44:18 de America/Bogota (18:44:18 UTC)**. El comando ejecutado por root fue `clean verify`; este documento registra sus resultados y el archivo de evidencias conservado.

R6 **no está desplegado**. Su resultado local no demuestra una reducción del P95 ni sustituye la aceptación de la aplicación `fa2a9955`, imagen `f3456068…`, revisión activa `grownupsvet-backend-00008-824`. Window A y la Window B pendiente de esa revisión mantienen su identidad y población independientes.

## Alcance probado

El [plan previo R6](plan-rendimiento-r6-2026-10-28.md) conserva únicamente dos optimizaciones SQL:

1. Agrupar evento y operación de auditoría clínica mediante un CTE dependiente de `RETURNING`, conservando la escritura previa del padre, los UUID distintos, la versión, el actor, los fingerprints y el instante.
2. Leer recetas y controles en una sentencia `UNION ALL`, con discriminador y NULL tipados, conservando autorización, `REPEATABLE_READ`, precisión y orden por posición.

Los tres archivos modificados y probados fueron:

- `src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/service/ClinicalEncounterService.java`.
- `src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/service/ClinicalTreatmentService.java`.
- `src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/ClinicalEncounterTransactionIntegrationTests.java`.

Las fuentes permanecieron inmóviles durante la ejecución final. Root contrastó los 293 SHA del manifiesto antes de registrar R6 en el commit **`fecdd2fae1fe96ce6f2a2d5d57520a6777d6cbf2`**. El manifiesto de aplicación identifica las dos fuentes productivas; el manifiesto de reportes identifica los XML de las suites ejecutadas, incluida la suite transaccional clínica. Esta evidencia corresponde a R6; los cambios locales posteriores de R7 no se utilizan como evidencia de esta ejecución.

Se conservaron permisos, locks, transacciones, replays, restricciones diferidas y métricas `AFTER_COMMIT`. La regresión incluye las pruebas existentes de concurrencia, idempotencia y rollback, además de las pruebas R6 de fallo tardío de la operación de auditoría y lectura de hijos con sus conteos acotados. No se cambió la confirmación de citas, el contrato público, las migraciones V1–V23 ni la configuración productiva. Menos sentencias JDBC no equivalen por sí solas a menos de 500 ms en Cloud Run.

## Resultados y separación de ejecuciones

| Comprobación | Resultado local |
| --- | --- |
| `clean verify` final | PASS: 764 pruebas, 65 suites; 0 fallos, 0 errores, 0 omitidas |
| Gate de reportes completos | PASS: 65 XML, sin fallos ni pruebas omitidas |
| Contrato OpenAPI y ejemplos | PASS: OpenAPI 3.1.0, API 0.11.0 y 51 ejemplos HTTP |
| Comparación semántica del snapshot | PASS: contrato generado coincidente con el versionado |
| Gates negativos controlados | PASS: orden inocuo aceptado; deriva de contrato, OpenAPI inválido, pruebas omitidas y reportes faltantes rechazados |

La primera ejecución se interrumpió. Sus **26 XML** se conservaron en `output/backend-cierre-2026-10-28/r6-interrupted-reports` y su estado sigue siendo **INTERRUPTED / NOT PASS**. No se completó retrospectivamente ni se mezclaron sus reportes con la ejecución final.

La repetición completa y sus **65 XML** están en `output/backend-cierre-2026-10-28/r6-resumed-final-reports`. Una lectura posterior de estos XML confirmó los totales 764/65 y cero fallos, errores u omisiones. Esta lectura no ejecutó nuevamente las pruebas.

## Integridad del archivo conservado

Los archivos siguientes son evidencias privadas fuera del repositorio; aquí se registran sus nombres relativos al workspace y SHA-256, sin credenciales ni contenido de los reportes.

| Archivo | SHA-256 |
| --- | --- |
| `output/backend-cierre-2026-10-28/r6-interrupted-reports/manifest.json` | `58af07450edbb06f0acbcffed8ca655efffc609d3a13893f8c1e5a98918bf518` |
| `output/backend-cierre-2026-10-28/r6-resumed-final-reports/report-manifest.json` | `8634abc11b016534ac6228e0c69788bb2f762e04ea40da899a8d07d00b661451` |
| `output/backend-cierre-2026-10-28/r6-resumed-final-reports/application-source-manifest.json` | `7cb65d28489c434d2ed1328efd4c15413b3822ab6eda9cdcea1794323637330c` |
| `output/backend-cierre-2026-10-28/r6-resumed-final-reports/openapi.json` | `2109adcd1c915f6c9b714d9a8aa1a15a591064eafe7a20c89ddf3056eda89412` |

El manifiesto de aplicación contiene **293 archivos**, incluido `mvnw.cmd`. Es la población de este archivo local de verificación; no debe compararse como si fuera la subida de 292 archivos utilizada para construir la imagen ya desplegada.

## Límites y continuación

Este corte acredita la regresión y el contrato **locales** de R6. No acredita CI remoto, build, despliegue, reducción de latencia ni cumplimiento general del SLO. No se enviaron nuevos correos ni se ejecutaron mutaciones productivas como parte de esta verificación documental.

La [verificación de despliegue y Window A](verificacion-despliegue-rendimiento-2026-10-28.md) permanece histórica e independiente. La finalización, historia y recordatorios de Window B se medirán sobre la revisión prevista, sin anticipar el inicio real del 29/10/2026 a las 06:00 de Bogotá (11:00 UTC). R7 queda fuera de esta evidencia y tendrá validación propia.
