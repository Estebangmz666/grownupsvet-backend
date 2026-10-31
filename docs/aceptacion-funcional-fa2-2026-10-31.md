# Aceptación funcional de fa2 — 31/10/2026

La continuación de Window B verificó tratamiento, finalización consistente, replay, lectura publicada, historia y los dos avisos clínicos de la muestra **fa2**. El reporte final conserva **138 HTTP registrados con respuestas esperadas y ninguna comprobación fallida**. Existe además **una lectura externa de preflight interrumpida**, separada del reporte: no se declara que el recorrido completo tuvo cero errores.

La aceptación es funcional y acotada. **Seis grupos de operación/perfil conservan incumplimientos de P95 <500 ms** en sus muestras: cinco de Window A y uno de B. Guardar tratamiento aparece en ambos perfiles; no son seis operaciones de negocio distintas. La decisión actual prioriza la entrega funcional con diagnóstico y plan futuro, sin implementar más optimizaciones para forzar esa meta. El umbral y los resultados históricos no se cambian; no se declara SLO PASS ni cierre general mediante esta muestra aislada.

## Identidad y alcance

- Aplicación `fa2a9955c7217c9e6bd00c8a9881ba20711b6a81`; revisión `grownupsvet-backend-00008-824`.
- Imagen `sha256:f3456068f8fffb297f53ac7f67d75d20c1e7028150f77d403647272aad608f28`.
- Identidad capturada: tráfico 100%, revisión ready, API 0.11.0 y migraciones hasta V23. No es una consulta nueva del plano de control.
- Window A corresponde al 28/10; Window B al 31/10. Este documento no atribuye sus resultados a R6/R7 ni acredita el despliegue o aceptación productiva de R7.
- El [resumen público independiente](evidencias/aceptacion-2026-10-31/resumen-fa2.json) tiene SHA-256 `15d45b7d894b42b03424da665d0037b04fb56a8c1fd00ddf1c08feade64a0f00`. Los [resultados históricos de Window A](verificacion-despliegue-rendimiento-2026-10-28.md) permanecen intactos.

## Recorrido y unidades verificadas

| Unidad acumulada de la muestra fa2 | Éxitos verificados / intenciones elegibles | Alcance |
| --- | ---: | --- |
| Crear mascota | 1/1 | A; no incluye altas de propietarios |
| Solicitar cita | 1/1 | A; el replay no crea otra cita |
| Publicar disponibilidad | 1/1 | A |
| Crear borrador | 1/1 | A; otro registro distinto del finalizado en B |
| Guardar textos clínicos | 3/3 | A |
| Guardar tratamiento | 4/4 | Tres en A y uno en B |
| Finalizar atención | 1/1 | B; borrador preexistente |
| Integridad M8 | 2/2 registros distintos | No son dos nuevas atenciones ni se suman lecturas/replays |

**B añadió dos escrituras efectivas:** un guardado de tratamiento y una finalización. No añadió una creación clínica. El replay de finalización no incrementó versión ni duplicó auditoría o tareas. El oráculo conservado verificó atención FINALIZED y cita COMPLETED de forma consistente; detalle y tratamiento publicados e historia se comprobaron después.

No se mezcla este corte con los denominadores históricos de altas de propietarios, rechazo de datos inválidos o prevención de conflictos. Esas poblaciones M1/M2/M5 no se recalculan aquí. Tampoco se suman unidades heterogéneas para fabricar una tasa global. El tratamiento de B contiene una prescripción y un control; las pruebas locales de 50+50 elementos son otra evidencia.

Los 138 intentos registrados tienen estados esperados —incluidos rechazos de autenticación y privacidad—, sin transporte incierto, 5xx o 429 dentro de esa población. `failedChecks` y `failureHistory` contienen cero entradas. Esto no elimina la interrupción externa siguiente.

## Lectura externa interrumpida

Se preserva `window-b-read-interruption.json`: un preflight de solo lectura se detuvo sin completar su GET y sin crear nuevas intenciones clínicas. Los GET diagnósticos posteriores del contrato respondieron 200 en ambos clientes; no se convierten por ello en éxito retroactivo de la lectura original. Esa interrupción no está incluida entre los 138 resultados registrados y queda expresamente fuera de cualquier afirmación de cero fallos globales.

## Recordatorios, fin de ventana y recepción

El estado original del harness sigue siendo **REMINDERS_VERIFIED_SMTP_ONLY** y `mailboxReceiptVerified=false`. El corte final fue el **31/10 a las 16:55:34.028555 de Bogotá (21:55:34.028555 UTC)**, posterior al fin de la prescripción de **16:54:41.159305 de Bogotá (21:54:41.159305 UTC)**.

| Origen clínico | Estado de tarea | Intentos | Aceptación SMTP UTC |
| --- | --- | ---: | --- |
| Control | SENT | 1 | 21:34:48.349434 |
| Prescripción | SENT | 1 | 21:44:50.998790 |

Se observaron **dos fuentes FINISHED y dos tareas SENT**, con orígenes coincidentes. El corte posterior al fin no mostró nuevas tareas posteriores ni programación futura pendiente para esas fuentes; es una observación terminal concreta, no vigilancia indefinida ni garantía exactly-once.

La recepción y el encoding se acreditan **por separado**: `fa2-human-mail-receipt.json` registra **USER_REPORTED, 2/2 avisos recibidos y encoding correcto**, sin inspección automatizada del buzón. No se publica el destinatario ni se cambia el flag automático del harness.

La confirmación administrativa de cita ya constaba SENT con un intento desde el **28/10 a las 11:56:18.417041 de Bogotá (16:56:18.417041 UTC)**. No es otro aviso clínico nuevo y no se afirma recepción humana de esa confirmación.

## Latencia: población conservada y desviaciones informadas

El colector congelado usó un reporte de **114 HTTP**, con **36/36 intentos elegibles correlacionados y cero no verificados**. El reporte funcional final contiene 138 HTTP: se compararon en memoria las identidades, métodos, estados, tiempos y operaciones de sus 36 elegibles, y la población es idéntica a la congelada y a la correlacionada. Las lecturas adicionales no se usan para inflar o cambiar el denominador de latencia.

Se conserva nearest rank `techo(0,95 × N)`. Cada operación tiene N=1 o N=3; estas cifras son P95 de muestra pequeña. Cloud Run mide latencia de plataforma y el cliente mide duración extremo a extremo: son columnas separadas, sin sumarlas ni promediarlas.

| Grupo de operación/perfil | N | P95 Cloud Run, ms | P95 cliente E2E, ms | Meta estricta <500 ms de plataforma |
| --- | ---: | ---: | ---: | --- |
| M3_pet_create_A | 1 | 234.455866 | 446.833 | Cumple en la muestra |
| M6_appointment_create_A | 1 | 379.345797 | 585.866 | Cumple en la muestra |
| M6_appointment_replay_A | 1 | 234.966288 | 515.264 | Cumple en la muestra |
| M6_availability_A | 3 | 274.924645 | 497.817 | Cumple en la muestra |
| M6_confirmation_A | 1 | 545.292614 | 738.333 | **Incumple** |
| M6_publication_A | 1 | 406.571461 | 616.05 | Cumple en la muestra |
| M9_detail_A | 3 | 201.214696 | 849.937 | Cumple en la muestra |
| M9_draft_create_A | 1 | 615.718719 | 825.66 | **Incumple** |
| M9_draft_replay_A | 1 | 387.183394 | 590.927 | Cumple en la muestra |
| M9_finalize_B | 1 | 478.072082 | 776.695 | Cumple en la muestra |
| M9_finalize_replay_B | 1 | 263.37191 | 557.988 | Cumple en la muestra |
| M9_history_B | 3 | 255.594505 | 684.459 | Cumple en la muestra |
| M9_published_detail_B | 3 | 140.529599 | 583.739 | Cumple en la muestra |
| M9_published_treatment_B | 3 | 242.244234 | 582.384 | Cumple en la muestra |
| M9_text_replay_A | 1 | 354.667103 | 571.297 | Cumple en la muestra |
| M9_text_save_A | 3 | 546.875291 | 752.718 | **Incumple** |
| M9_treatment_read_A | 3 | 595.488405 | 1036.92 | **Incumple** |
| M9_treatment_replay_A | 1 | 455.759423 | 683.702 | Cumple en la muestra |
| M9_treatment_save_A | 3 | 693.694336 | 942.49 | **Incumple** |
| M9_treatment_save_B | 1 | 619.959843 | 946.893 | **Incumple** |

El guardado de tratamiento B mantiene **619.959843 ms**. No se modifica a PASS por la aceptación de la desviación. La topología remota y las operaciones JDBC seriales sustentan factores a investigar, pero no prueban cuánto aporta cada componente ni una causa exclusiva. El [diagnóstico y plan futuro](diagnostico-latencia-y-plan-futuro-2026-10-31.md) separa hechos e hipótesis y propone medición comparable y análisis aislado antes de cualquier mejora opcional; no se implementa ese plan en esta aceptación.

## Monitoreo de la revisión observada

El corte local de Prometheus/Grafana verificó la revisión 008 fresca, siete familias, siete series de operaciones de negocio, 14 series del bucket de 500 ms y **nueve consultas correctas** de un dashboard de diez paneles. El P95 aproximado devolvió **una serie finita y siete NaN**, sin infinitos. La ventana de rate de cinco minutos y la muestra corta no certifican un SLO; NaN no es una latencia nula ni una operación cumplida.

La prueba del runtime validó loopback y scrape UP/fresco, sin cambiar procesos o nube. Sus datos describen ese instante; este documento no verifica la limpieza final, el escalado posterior ni monitoreo permanente.

## Procedencia y límites

Los hashes siguientes identifican evidencias privadas conservadas fuera de Git, bajo `output/backend-cierre-2026-10-31`. Solo se publican agregados y hashes, sin IDs de negocio, trazas, correos, payloads clínicos, credenciales o filas privadas.

| Evidencia | SHA-256 |
| --- | --- |
| `fa2-final-report-source.json` | `4676d13abed7f92f4fc552a7853e0f31fb2d32300f811d05b1da07ab1344dcd6` |
| `fa2-final-checkpoint-private.json` | `45480d4b7b447f56b5f37793d6d7a9d260921509c2f57281d2445d6c75f6008d` |
| `fa2-window-ab-latencies.json` | `d213409e2119dec7019d0008bc29ddb990cb85fd79b21b8ecd9ee0f472353d92` |
| `fa2-window-ab-source-initial.json` | `4a7b00e09a626fe428ae5a62c74f8eca1692dd2b41cc652bd1a549d1fab3488b` |
| `fa2-human-mail-receipt.json` | `4f6d878e3f3d59ec4368450290e445557e37e7b32ceee719af17b88971c82d0f` |
| `window-b-read-interruption.json` | `1253bd2ada9a76f1a7eb1ba2bbfa49cc8fad4676efabc4c9ef0df8814b7dd541` |
| `monitoring-fa2-window-b-verification.json` | `38bd7f614dc7f98d1ed3ad72a9094bf5ef4af502d4cfce3d633f0cf492509b98` |

La preparación de estos dos documentos solo leyó archivos y comprobó agregados. No ejecutó aplicación, SQL, correo, pruebas, nube o comandos Git. Los históricos y las fuentes privadas permanecen sin cambios. La integración y eventual activación de R7, el ahorro final y la matriz completa I0–I8 requieren sus propios cortes de evidencia.
