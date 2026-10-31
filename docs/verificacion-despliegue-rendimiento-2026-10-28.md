# Despliegue de rendimiento y Window A — 28/10/2026

La revisión **grownupsvet-backend-00008-824** quedó activa al 100%, con la imagen de aplicación `fa2a9955` y configuración conservada. **El objetivo completo sigue abierto:** Window A correlacionó 24/24 intentos y conserva cinco operaciones con P95 de muestra igual o superior a 500 ms. Finalización, historia y recordatorios sobre esta revisión esperan Window B, después del inicio real de la cita el **29/10/2026 a las 06:00 de America/Bogota (11:00 UTC)**.

Esta es evidencia posterior al [corte previo al refactor](aceptacion-backend-desplegado-2026-10-28.md). Sus revisiones, poblaciones y resultados se conservan por separado. El [resumen agregado nuevo](evidencias/aceptacion-2026-10-28/resumen-postpatch-window-a.json) no contiene filas HTTP, identidades, destinatarios, datos clínicos, tokens o configuración privada.

## Identidad, CI y publicación

| Elemento | Evidencia verificada |
| --- | --- |
| Fuente de aplicación | `fa2a9955c7217c9e6bd00c8a9881ba20711b6a81`, [PR7 integrado](https://github.com/Estebangmz666/grownupsvet-backend/pull/7) |
| Fuente operativa del helper | `239d96f0281143c5c8f62665e2701a6c424c1c97`, [PR8 integrado](https://github.com/Estebangmz666/grownupsvet-backend/pull/8); árbol de aplicación sin cambio |
| Build regional existente | `c1b5b40b-bb70-4f1e-8a0d-c656d54b1156`, SUCCESS |
| Imagen inmutable | `sha256:f3456068f8fffb297f53ac7f67d75d20c1e7028150f77d403647272aad608f28` |
| Revisión activa | `grownupsvet-backend-00008-824` |
| Contrato y esquema | OpenAPI 0.11.0, 47 rutas, migración V23; contrato completo coincidente |
| Corte de activación | 28/10/2026 16:50:38.8366056 UTC, generación 14, tráfico 100%, sin tags, mínimo 0/máximo 1 |

La [CI del merge de aplicación](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40719636534) y la [CI del merge operativo](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40725016637) terminaron con **762 pruebas/65 suites, cero fallos, errores u omisiones**, 51 ejemplos HTTP, snapshot coincidente y gates negativos correctos. El check requerido fue `Backend verification`, app 15368, para cada SHA exacto. La CI operativa verificó además **31 escenarios PowerShell en Ubuntu**, 18 de activación, con SDK/HTTP simulados y sin acciones cloud. La [regresión local](verificacion-rendimiento-regresion-2026-10-28.md) sigue siendo evidencia distinta.

## Resultado del helper y continuación manual

La ejecución real de ImageOnly cambió la imagen y creó la revisión 008, pero conservó el 100% en la revisión anterior. En el caso real **mínimo 0 + tráfico fijado + revisión Retired**, la revisión del servicio no superó `latestReady == latestCreated`. Se preserva el resultado del helper: `Verified=false`, `ActivationAttempted=false`, `ConfigurationPreserved=true`, `ImageVerified=true`, `RevisionReady=false`, `TrafficVerified=false`, etapa `ImageUpdate`. **No fue una activación verificada del helper.** La opción de [activación explícita](plan-activacion-revision-cloud-run-2026-10-28.md) no se amplió para omitir esa guarda.

El agente principal siguió un plan manual separado: comprobó nuevamente la revisión/digest/configuración, añadió un tag temporal propio al candidato con 0% del tráfico principal y mantuvo el 100% anterior. Validó readiness, contrato y etiqueta de revisión de métricas contra la URL devuelta por el proveedor. La comprobación del contrato completo verificó primero el único server canónico y normalizó solo esa diferencia y el orden admitido, sin ignorar rutas o esquemas. Esas solicitudes de calentamiento no se suman a Window A.

Después asignó una vez el 100% a la revisión exacta 008 y retiró el tag propio. La lectura posterior confirmó generación 14, imagen/digest, configuración conservada, mínimo 0/máximo 1 y ausencia de tags. No se repitió la actualización de imagen para superar el fallo ni se interpretó su resultado falso como éxito. El resultado fallido, los checkpoints del candidato y la activación son cortes independientes.

## Window A: muestra y latencias completas

Ventana HTTP: **16:53:37.312644–16:56:31.744099 UTC** del 28/10 (11:53–11:56 Bogotá). Hubo **65 intentos con respuestas esperadas**, cero resultados inciertos, respuestas inesperadas, 5xx o 429. El colector terminó a 16:59:18.263481 UTC y correlacionó **24/24 intentos elegibles**, sin faltantes, duplicados o incompatibilidades, siempre en la revisión 008. Los otros 41 HTTP no se incorporan a la población de latencia.

P95 usa nearest rank `techo(0,95×N)` y umbral estricto `<500 ms`. **N es 1 o 3 por operación**; con N=1 el percentil es la única observación. Cloud Run mide latencia de solicitud de plataforma; cliente E2E incluye Internet y se conserva en otra columna. No se interpreta como tiempo CPU exclusivo de Java ni como un SLO general.

| Operación | N | P95 Cloud Run (ms) | P95 cliente E2E (ms) | <500 ms en esta muestra |
| --- | ---: | ---: | ---: | --- |
| M3 · crear mascota | 1 | 234.455866 | 446.833 | Sí |
| M6 · solicitar cita | 1 | 379.345797 | 585.866 | Sí |
| M6 · replay de solicitud | 1 | 234.966288 | 515.264 | Sí |
| M6 · consultar disponibilidad | 3 | 274.924645 | 497.817 | Sí |
| M6 · confirmar cita | 1 | 545.292614 | 738.333 | **No** |
| M6 · publicar disponibilidad | 1 | 406.571461 | 616.05 | Sí |
| M9 · leer atención/borrador | 3 | 201.214696 | 849.937 | Sí |
| M9 · crear borrador | 1 | 615.718719 | 825.66 | **No** |
| M9 · replay de borrador | 1 | 387.183394 | 590.927 | Sí |
| M9 · replay de textos | 1 | 354.667103 | 571.297 | Sí |
| M9 · guardar textos | 3 | 546.875291 | 752.718 | **No** |
| M9 · leer tratamiento | 3 | 595.488405 | 1036.92 | **No** |
| M9 · replay de tratamiento | 1 | 455.759423 | 683.702 | Sí |
| M9 · guardar tratamiento | 3 | 693.694336 | 942.49 | **No** |

Se informan las 14 operaciones completas. Los cinco incumplimientos permanecen abiertos. El corte anterior tenía otro conjunto de operaciones, incluida finalización, y seis incumplimientos: **6→5 no es una tasa comparable de mejora**. No se atribuye causalidad exclusiva a red, SQL o componentes sin medirlos.

El checkpoint registra mascota 1/1, solicitud de cita 1/1, creación clínica 1/1, guardados de textos 3/3 y tratamiento 3/3, cada unidad por separado. La publicación de disponibilidad fue 1/1. M8 informa 2/2 registros clínicos distintos; no equivale a dos atenciones nuevas ni permite sumar lecturas/replays como escrituras. Estas tasas pequeñas no acreditan un 99% poblacional.

La confirmación de cita estaba **SENDING**, un intento y `sentAt=null` en ese checkpoint. `mailboxReceiptVerified=false`; no se acredita SENT, recepción de correo ni encoding humano del aviso nuevo. Los tres mensajes recibidos del ensayo anterior se conservan exclusivamente en su corte histórico.

## Monitoreo y limpieza posteriores

La lectura de **17:01:16 UTC (12:01 Bogotá)** verificó target fresco/UP de la revisión 008, siete familias de métricas, siete operaciones de commits, 24 series de bucket de 500 ms, diez paneles y nueve consultas PromQL correctas. El panel P95 devolvió 16 series no finitas/NaN: consulta exitosa no significa P95 finito ni cumplimiento de latencia. Los valores exactos anteriores proceden del colector Cloud Run.

La limpieza posterior quedó comprobada a **17:04:29.5005629 UTC (12:04 Bogotá)**: generación 16, misma revisión 008 al 100%, sin tags, mínimo 0/máximo 1 y configuración conservada, huella `f5c224e1d6e86b5d94b6870b17e54911d5a7f130865282ae6c45c111f209cd49`. Prometheus/Grafana quedaron apagados y sus datos conservados. El scrape fresco previo y el apagado posterior no son vigilancia sostenida ni prueban cero instancias inmediato o gasto total cero. El [ahorro previo](verificacion-ahorro-post-pruebas-2026-10-28.md) permanece como otro corte, generación 11 y revisión anterior.

## Pendientes explícitos

Window B no se ejecutó: solo podrá finalizarse la cita después de su inicio real el **29/10 a 06:00 Bogotá/11:00 UTC**. Quedan el guardado B, finalización/replay, detalle y tratamiento publicados, historia y los recordatorios de la revisión nueva, con sus ventanas reales y recepción separada de SMTP. No se adelanta la cita, cambia el reloj o reutiliza la finalización histórica para acreditar esa fase. También siguen abiertos los cinco incumplimientos de Window A y la evaluación final del objetivo completo.

## Procedencia sin datos privados

SHA256 del resumen público nuevo: `aa526199cee8e744d4eaa6acdec415ee5cb08d4e9e9923f5f2c7db03481f141c`. El resumen histórico previo mantiene SHA256 `afecef673037921b278cc806c847bc590ee318c48ee7824d529055963abb8382` sin cambios.

| Fuente lógica fuera de Git | SHA256 |
| --- | --- |
| performance-active-verification.json | `eb10b3cf3d23215e6204c2c88be6aa54932410a363f40b7e6d4b5bb8509105b9` |
| performance-deployment-manifest.json | `2e1c4ed6ca4a339a102e4581abd65b37d5f477dfd172a2bdbad0743b91595844` |
| performance-deployment-execution-2.json.result.json | `05060459044540c7f5f5ff265bd879842bea5445fa8f280ce81eb5c98b5d660f` |
| candidate-http-verification.json | `cb989a79b89181dd715f6032b028bf19c0c8b5ab199aff6d278ce1d7a9619189` |
| candidate-full-contract-verification.json | `d2a543f840d5cdce33c25ec3106d09a85136610fc8685285bdfb726b42f8d0b9` |
| monitoring-new-revision-verification.json | `36fd5bae45758feab346c54adc524bdb96968a333e12378fddb27a54d490e26a` |
| postpatch-measurement-window-a-source.json | `50a0c1c6f11ef87d908d9453b44dedb7ee10cdd9a47386369a66ae22e06eff9b` |
| cloud-run-latencies-postpatch-window-a.json | `4d3d84d8fefcf435c766f12acaed0f58a61e6f01dba968a163a35481fb9400a6` |
| performance-cleanup-verification.json | `da4be68d000623d5e0ca2a840a84382b932ebe31d5c956b2a53e55468aa68976` |

El SHA del reporte Window A coincide con `sourceReportSha256` del colector. La publicación agrega únicamente este corte agregado y sus punteros; no sustituye poblaciones, timestamps o hashes históricos ni publica checkpoints operativos privados.
