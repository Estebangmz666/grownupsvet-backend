# Aceptación del backend R7 desplegado — 31/10/2026

La muestra R7 completó el recorrido clínico y su corte posterior al fin de medicación: **120 HTTP registrados con respuestas esperadas, 42 comprobaciones registradas sin fallos y ninguna entrada en failureHistory**. Se verificaron tres escrituras efectivas, tres replays y dos avisos clínicos SENT. La recepción **2/2 y encoding correcto** se conserva por separado como USER_REPORTED.

La aceptación es funcional y acotada. **Guardar tratamiento y finalizar mantienen incumplimientos de la meta estricta P95 <500 ms**. La decisión del 31/10 prioriza documentar causas a investigar y plan de mejora, sin implementar más optimizaciones para forzar el umbral. No se cambia la meta, no se convierte la desviación en PASS y no se declara SLO general.

## Identidad y población

- Commit de aplicación: `222131c5704652aa85cf79f2548d8c225f590e54`; revisión `grownupsvet-backend-00009-vmd`.
- Imagen: `sha256:3f1ed36f60bdaa245d529cc1fb56e59d29255f9d3c94446d1c46a88f7e06ca11`.
- API 0.11.0, contrato de 47 rutas y migraciones hasta V23. El preflight contrastó contrato y revisión; no se atribuyen a R7 las muestras de fa2.
- Ventana HTTP registrada: **22:03:14.339121–22:25:07.669325 UTC** del 31/10, equivalente a17:03–17:25 en Bogotá.
- [Resumen sanitizado R7](evidencias/aceptacion-2026-10-31/resumen-r7.json), SHA256 `c18b5ff19d351d88d93701d8aad86c50d06246b81b461edff0b4943637ea9169`.

Los 120 intentos contienen 72 respuestas 200, 20 respuestas 401, 16 respuestas 404 y 12 respuestas 403. Los rechazos corresponden a comprobaciones previstas de autenticación y privacidad; no hubo estado inesperado, transporte incierto, 5xx o 429 dentro de esta población. Los tipos de intento son 114 INITIAL, 3 REPLAY y 3 RECONCILIATION; INITIAL incluye comprobaciones auxiliares y no significa 114 escrituras. Esta evidencia no elimina fallos o interrupciones de cortes anteriores.

## Recurso heredado, persistencia y autorización

Se reutilizó el segundo borrador ficticio del [corte fa2](aceptacion-funcional-fa2-2026-10-31.md), con otra mascota y veterinario responsable respecto de la atención finalizada allí. El nuevo run tiene identidad, claves y muestras propias. Solo heredó los recursos/actores y sus versiones iniciales; no importó operaciones ni tasas del padre. Los hashes del padre sellado y del adaptador permiten comprobar esa procedencia.

| Unidad R7 | Éxitos verificados / intenciones elegibles | Alcance |
| --- | ---: | --- |
| Crear atención | 0/0 | Sin nueva creación; no evaluada como alta R7 |
| Guardar los cinco textos | 1/1 | DRAFT v0→v1 |
| Guardar tratamiento | 1/1 | Una receta y un control; v1→v2 |
| Finalizar atención | 1/1 | FINALIZED v3 y cita COMPLETED v2 |
| Replays | 3/3 | Texto, tratamiento y finalización; sin otra transición |
| Integridad M8 | 1/1 registro distinto heredado | No son tres registros por las tres escrituras |

**El registro M8 de R7 es el mismo que ya figura en los 2/2 registros distintos de fa2. No se suman 2+1 para declarar tres registros.** La creación sigue respaldada por su evidencia histórica y pruebas locales; no se acredita una creación desplegada nueva mediante replay o actualización. M1–M6 no se recalculan en esta muestra clínica.

El oráculo coherente comprobó relaciones exactas de cita/mascota/propietario/profesional, creador preservado, contenido clínico e hijos persistidos, cuatro eventos/cuatro operaciones clínicas y dos programaciones. La cita del 29/10 se finalizó el31/10 después de su inicio permitido; no hubo backdating, reapertura ni reloj artificial. Las escrituras se hicieron mediante REST y los oráculos mediante SELECT READ ONLY con TLS verify-full.

Se verificaron detalle, tratamiento, descubrimiento por cita e historia publicada, comparando contenido y no solo 200/ID. Propietario propio, ajeno, administración, superadministración y veterinario ajeno conservaron sus accesos/rechazos previstos tanto para detalle como para tratamiento. Los borradores permanecieron ausentes de la historia del propietario. No se cambió el personal ni se inyectaron fallos SQL en producción.

## Latencia: 96 HTTP de medición, 120 de cierre

El colector consumió el archivo congelado `r7-measurement-source-initial.json`, que contiene **96 HTTP**, no 108. Sus **18/18 solicitudes elegibles** se correlacionaron, sin faltantes. Se compararon todas las propiedades de esas 18 muestras con el reporte funcional final de 120 HTTP: la población elegible es idéntica, incluidos identidad técnica del intento, método, estado, revisión, tiempos, operación y clasificación. Las lecturas posteriores no modificaron ni ampliaron ese denominador.

La población medida contiene 12 INITIAL, 3 REPLAY y 3 RECONCILIATION; cada grupo tiene N=1. Su representación canónica tiene SHA256 `8315701f5371655c66b7bcf66ddc8828aede12d236016abc66b8b717c87e6053` —JSON UTF8, claves ordenadas, separadores compactos y orden original de las muestras—. No se publican los traces.

Se conserva nearest rank `techo(0,95×N)`, sin redondear antes de ordenar. Con N=1, el percentil de muestra coincide con la única observación; no estima un 95% poblacional. Cloud Run mide latencia de plataforma, no CPU Java exclusiva. El cliente incluye Internet y se informa en otra columna.

| Grupo medido | N | P95 Cloud Run ms | P95 cliente E2E ms | Meta <500 ms de plataforma |
| --- | ---: | ---: | ---: | --- |
| M9_detail_draft_R7 | 1 | 180.695574 | 481.755 | Cumple en la muestra |
| M9_detail_draft_R7_reconciliation | 1 | 204.033384 | 431.014 | Cumple en la muestra |
| M9_detail_published_R7 | 1 | 204.271513 | 532.102 | Cumple en la muestra |
| M9_discovery_draft_R7 | 1 | 191.027109 | 473.511 | Cumple en la muestra |
| M9_discovery_draft_R7_reconciliation | 1 | 172.663439 | 384.787 | Cumple en la muestra |
| M9_discovery_published_R7 | 1 | 168.111671 | 444.963 | Cumple en la muestra |
| M9_finalize_R7 | 1 | 630.391351 | 1017.874 | **Incumple** |
| M9_finalize_replay_R7 | 1 | 399.529748 | 602.501 | Cumple en la muestra |
| M9_history_owner_R7 | 1 | 323.421003 | 650.829 | Cumple en la muestra |
| M9_history_responsible_R7 | 1 | 327.496468 | 555.149 | Cumple en la muestra |
| M9_text_replay_R7 | 1 | 486.429977 | 742.109 | Cumple en la muestra |
| M9_text_save_R7 | 1 | 479.21848 | 687.464 | Cumple en la muestra |
| M9_treatment_draft_R7 | 1 | 176.966756 | 530.209 | Cumple en la muestra |
| M9_treatment_empty_R7 | 1 | 220.239234 | 546.642 | Cumple en la muestra |
| M9_treatment_empty_R7_reconciliation | 1 | 200.220715 | 424.274 | Cumple en la muestra |
| M9_treatment_published_R7 | 1 | 168.180881 | 543.632 | Cumple en la muestra |
| M9_treatment_replay_R7 | 1 | 413.255035 | 688.887 | Cumple en la muestra |
| M9_treatment_save_R7 | 1 | 722.465284 | 1030.839 | **Incumple** |

No se mezclan reconciliaciones/replays con una escritura nueva ni se infiere una mejora causal por comparar muestras de revisiones o perfiles distintos. El [diagnóstico y plan futuro](diagnostico-latencia-y-plan-futuro-2026-10-31.md) conserva hechos e hipótesis; una mejora posterior requiere medición comparable y alcance propio.

## Recordatorios y recepción

La prescripción tuvo inicio **22:14:40.848738Z** y fin **22:24:40.848738Z**, intervalo de una hora y una sola ocurrencia posible. El control clínico quedó para 22:49:40.848738Z; al faltar menos de 24 h, su aviso fue inmediato.

| Aviso | Programación de tarea UTC | Aceptación SMTP UTC | Intentos |
| --- | --- | --- | ---: |
| Control inmediato | 22:04:49.868174 | 22:04:53.903179 | 1 |
| Prescripción | 22:14:40.848738 | 22:14:49.736325 | 1 |

El corte de **22:25:05.949375Z**, posterior a fin+15s, comprobó dos fuentes FINISHED y dos tareas SENT, orígenes coincidentes y destinatario autorizado mediante booleano. No observó dosis adicionales ni programación futura pendiente para esas fuentes. Es un corte terminal finito, no vigilancia indefinida ni garantía exactly-once de SMTP.

El harness permanece `REMINDERS_VERIFIED_SMTP_ONLY` y `mailboxReceiptVerified=false`. La evidencia separada `r7-human-mail-receipt.json` registra **USER_REPORTED, 2/2 recibidos y encoding correcto**, sin inspección automática del buzón. No se heredan como R7 las recepciones previas. La confirmación administrativa ya enviada el28/10 sigue siendo un aviso histórico, no un tercer correo nuevo ni una recepción humana nueva acreditada aquí.

## Monitoreo y cierre operativo

La comprobación de 22:11:07Z verificó métricas frescas de la revisión 009 en Prometheus/Grafana locales: **siete familias, siete series de commits,18 series del bucket 500 ms y nueve consultas exitosas en diez paneles**. Los contadores observados de guardar texto, tratamiento y finalizar fueron 1 cada uno; son commits de ese ciclo de proceso, no porcentajes de aceptación ni prueba global de otros contadores. El panel aproximado P95 tenía dos series finitas y ocho NaN; NaN no acredita percentil ni SLO. La tabla anterior procede de correlación de solicitudes Cloud Run.

La prueba final de ahorro, leída antes de redactar este documento, verificó a **22:27:07.9736732Z** generación 21, misma revisión/commit/imagen, tráfico 100% sin tags, **mínimo 0/máximo 1**, configuración preservada, monitoreo detenido, puertos locales cerrados y datos de monitoreo retenidos. Registró cero peticiones HTTP públicas posteriores al apagado. La API queda configurada bajo demanda: puede presentar cold start y sin instancia no se garantiza ejecución continua del worker. No se promete coste total cero ni desaparición inmediata de instancias.

## Procedencia sin datos privados

Los siguientes archivos privados se conservan por hash; este documento y el resumen no publican sus IDs de recursos/run/traces, correos, notas clínicas, tokens o configuración secreta. El resumen conserva los hashes del padre sellado y aclara el recurso heredado.

| Artefacto fuente | SHA256 |
| --- | --- |
| r7-final-report-source.json | `dcf0f0d9387e9f9560f92a98aba4583cabbc65ce7ad89616213c8b9c841f1f92` |
| r7-final-checkpoint-private.json | `67a0ee63c4f875cb56a1ad749f7ad7bc08b93b8c57304b2c9c5ed53ec5a7bc90` |
| r7-measurement-source-initial.json | `488ab0bd218e18fdd3660fe949f93c6ac22ed6352b925352b10eef9d79ce75b7` |
| r7-cloud-run-latencies.json | `eb7eda1c9eea33bb5bf361d9a293f7c83c02566808c3e232eb1d32e718fb82c3` |
| r7-human-mail-receipt.json | `8f3f760db1129acddd4372757029e4176463593b3a5e46f2b0b9381d20e2daa4` |
| monitoring-r7-verification.json | `ab3c6dbd02f291a6250de0cfd5d30fa07bba3ed7579ec704221efea499513d35` |
| final-operational-cleanup-verification.json | `749a59fa182f9bce917246d0f7b1bdb19700b2d048ea0e15f0218b23b87fb029` |

Este corte aporta aceptación funcional desplegada de R7 con sus límites explícitos. No recalcula M1–M6, no demuestra fiabilidad poblacional del 99% y conserva los incumplimientos cuantitativos y las evidencias históricas.
