# Aceptación del backend desplegado — 28/10/2026

El recorrido funcional desplegado completó **50/50 comprobaciones y 156 intentos HTTP con respuestas esperadas**, sin timeout, 429 ni respuesta inesperada. La muestra conserva **seis operaciones que incumplen P95 <500 ms en Cloud Run**, dos de M6 y cuatro de M9. Este corte acredita los casos definidos y sus oráculos; **no cierra el objetivo completo ni certifica un SLO global**.

Se siguieron el [plan de reanudación](plan-reanudacion-cierre-2026-10-28.md), el [plan de aceptación reanudada](plan-aceptacion-reanudada-2026-10-28.md) y el [plan de latencias](plan-latencias-despliegue-2026-10-28.md). Los datos del ensayo son ficticios. El [resumen sanitizado](evidencias/aceptacion-2026-10-28/resumen-sanitizado.json) conserva cantidades, medidas exactas y hashes sin identidades, direcciones, datos clínicos o configuración privada.

## Identidad del despliegue y ventana

| Elemento | Valor verificado |
| --- | --- |
| Base examinada de auditoría/main | `70b772a962f4036f0e6bc79e8cdfd0067154e493` |
| Fuente de aplicación desplegada | `960c8898acbb22a227eac9424260a12936556227` |
| Revisión Cloud Run | `grownupsvet-backend-admission-960c8898` |
| Imagen | `sha256:23e561476bfed1bba13bb34dc87644444b294700dae55b159f9cf53ab93d275d` |
| Contrato | OpenAPI 0.11.0, 47 rutas |
| Migraciones | V1–V23 exitosas, lista exacta |
| Perfiles operativos | prod, external-postgres, monitoring |
| Tráfico durante el ensayo | 100% a esa revisión, sin tags |

La revisión de aplicación se mantuvo durante la muestra. Los cambios posteriores de documentación y fixtures privados no representan otra imagen medida. El corte de reconciliación fue 14:23:02Z (09:23 Bogotá); el posterior al fin de la receta fue **14:48:05Z (09:48 Bogotá)**. El histórico del 25/10 pertenece a otra revisión: conserva su incidente 406 del cliente y su SHA, fuera de las tasas/latencias nuevas.

## Qué verificó el recorrido

- Reconciliación de la cita y el borrador originales por IDs, versiones, propietario/mascota, veterinario/creador, contenido, participantes activos y destinatario previamente autorizado. SQL exclusivamente read-only con TLS verify-full, sin hashes de contraseñas ni otros secretos.
- Dos propietarios ficticios y tres mascotas nuevas; lectura propia, privacidad cruzada y rechazo de nacimiento futuro sin persistencia. El registro no generó correo.
- Una cita nueva para el 29/10 a las 06:00 de Bogotá, con otra mascota y veterinario distinto: solicitud, confirmación, replay y conflicto ocupado exacto sin escritura parcial. Quedó **CONFIRMED y DRAFT v1**, marcada como ensayo no asistencial, sin recetas ni controles.
- La atención original pasó de DRAFT v2 a **FINALIZED v4**, y su cita de CONFIRMED v1 a **COMPLETED v2**. Oráculo coherente de ambos estados, autoría, cinco eventos/cinco operaciones clínicas y tres eventos de cita. Replay e intento de modificar el finalizado no cambiaron el snapshot.
- Detalle, tratamiento e historia por propietario, veterinario finalizador y actores ajenos, con los códigos de privacidad previstos. No se cambió staff, reloj o estado mediante SQL, ni se inyectaron fallos en producción.

## M1–M9: unidades, denominadores y alcance

| Indicador y meta | Resultado de este corte | Alcance/valoración |
| --- | --- | --- |
| M1 · ≥99% de altas válidas | Propietarios **2/2**, mascotas **3/3**, cada grupo 100% | Persistencia/rol/propiedad verificados. Muestras pequeñas, sin inferir 99% poblacional. |
| M2 · 100% del conjunto inválido | **1/1**, nacimiento futuro rechazado y sin fila | El conjunto formal desplegado es ese caso; las comprobaciones de privacidad no aumentan su denominador. |
| M3 · P95 <500 ms | Tres operaciones medidas; todas bajo 500 ms en Cloud Run | N=2 o 3 por operación. Cliente y plataforma se separan en la tabla. |
| M4 · ≥99% de solicitudes elegibles | **1/1**, 100% | Una cita nueva en la revisión actual; replay no es otra cita. |
| M5 · 100% de conflictos conocidos | **1/1**, 100% | Turno ocupado rechazado sin fila/reserva parcial. Ensayo secuencial; no acredita una carrera concurrente en producción. |
| M6 · P95 <500 ms | Consulta/publicación de disponibilidad cumplen; **solicitud y confirmación no cumplen** | Cuatro operaciones, N=1 cada una. |
| M7 · ≥99% de atenciones válidas | Creación **1/1**; tratamiento **1/1** y finalización **1/1** se informan por separado | La atención antigua no cuenta como una nueva creación. No sumar las tres operaciones como tres atenciones. |
| M8 · 100% de registros con relaciones válidas | **2/2 registros distintos**, 100%; **3/3 observaciones** conservadas | Original finalizado y futuro borrador. La observación prefinal del original permanece enlazada, sin inflar el denominador. |
| M9 · P95 <500 ms | **Crear borrador, guardar textos, guardar tratamiento y finalizar no cumplen** | Las otras cinco operaciones medidas cumplen en esta muestra; ver N y tiempos por operación. |

Los errores y timeouts se conservan por intento; un replay posterior no convierte un primer resultado incierto en un éxito observado. En este run no hubo resultados inciertos. El límite de login de cinco intentos por identificador/15 minutos y Retry-After se respetó; no se intentó agotarlo.

## Latencias exactas por operación

El colector correlacionó **28/28 intentos elegibles**, sin faltantes/duplicados/incompatibilidades, mediante trace aleatorio, revisión, método y estado de los request logs existentes. Su corte de 14:30:43Z tenía 138 HTTP totales. Los 18 HTTP posteriores corresponden a preflight, login y notificaciones sin elegibilidad de latencia: se comprobó igualdad exacta de las 28 observaciones elegibles con el reporte final de 156 HTTP.

P95 se calcula por operación como el elemento `techo(0,95 × N)` de las duraciones ordenadas, sin redondear antes de ordenar. Con **N=1, P95 es esa única observación**, sin estabilidad estadística. La meta se interpreta estrictamente `<500 ms`.

**Cloud Run** mide latencia de solicitud observada por la plataforma, no tiempo exclusivo de Java/CPU. **Cliente** mide extremo a extremo desde Windows e incluye Internet. El histograma Micrometer/Prometheus es otra medida aproximada y no sustituye estos valores. Las dos columnas no se promedian ni se usa la de cliente para declarar fallo de backend.

| Operación | N | P95 Cloud Run (ms) | P95 cliente E2E (ms) | Meta de plataforma <500 ms |
| --- | ---: | ---: | ---: | --- |
| M3 · crear mascota | 3 | 315.115406 | 604.018 | Cumple en la muestra |
| M3 · leer mascota | 3 | 266.716495 | 582.276 | Cumple en la muestra |
| M3 · registrar propietario | 2 | 490.701248 | 1110.339 | Cumple en la muestra |
| M6 · confirmar cita | 1 | 749.171162 | 1037.572 | **No cumple en la muestra** |
| M6 · solicitar cita | 1 | 549.604222 | 826.282 | **No cumple en la muestra** |
| M6 · consultar disponibilidad | 1 | 366.702492 | 870.104 | Cumple en la muestra |
| M6 · publicar disponibilidad | 1 | 450.070766 | 670.045 | Cumple en la muestra |
| M9 · leer atención/borrador | 5 | 349.855919 | 605.995 | Cumple en la muestra |
| M9 · crear borrador | 1 | 583.704644 | 998.944 | **No cumple en la muestra** |
| M9 · finalizar atención | 1 | 881.63757 | 1122.994 | **No cumple en la muestra** |
| M9 · replay de finalización | 1 | 464.91732 | 703.612 | Cumple en la muestra |
| M9 · consultar historia | 2 | 396.146724 | 619.091 | Cumple en la muestra |
| M9 · leer atención publicada | 2 | 235.312874 | 771.203 | Cumple en la muestra |
| M9 · leer tratamiento publicado | 2 | 470.605942 | 915.072 | Cumple en la muestra |
| M9 · guardar textos | 1 | 608.833454 | 913.447 | **No cumple en la muestra** |
| M9 · guardar tratamiento | 1 | 793.140491 | 1008.042 | **No cumple en la muestra** |

Los seis incumplimientos de plataforma permanecen abiertos como resultado de la medición. El documento no atribuye su causa a red, SQL, hashing u otro componente sin una investigación específica, ni presenta esta muestra secuencial como prueba de capacidad.

## Avisos, fin de receta y recepción

Se observaron exactamente **una programación y una tarea por fuente clínica**, CONTROL y PRESCRIPTION. Ambas terminaron FINISHED/SENT, missedCount=0, un intento por tarea, error nulo y destinatario autorizado. La única dosis fue programada para 14:37:12.928076Z, con expiry 14:42:12.928076Z y fin 14:47:12.928076Z. El corte 14:48:05Z, posterior al fin, conservó esas dos únicas tareas sin nuevas dosis.

| Aviso previsto | Estado observado | Intentos | sentAt UTC / Bogotá |
| --- | --- | ---: | --- |
| Confirmación de cita futura | SENT | 1 | 14:26:21.429949Z /09:26:21 |
| Control clínico inmediato por <24 h | SENT | 1 | 14:27:20.687862Z /09:27:20 |
| Dosis única | SENT | 1 | 14:37:16.689037Z /09:37:16 |

La confirmación futura se leyó mediante el endpoint propio de notificaciones, que excluye correo/payload; no se creó un lector directo nuevo de BD para ella. No se forzaron workers ni se reenviaron avisos. SENT acredita aceptación SMTP, mientras que **la recepción 3/3 fue confirmada por el usuario: USER_REPORTED**, no por inspección automática del buzón. No se afirma entrega exactamente una vez como garantía general de SMTP.

El usuario informó mojibake en datos clínicos ficticios; los textos fijos de plantilla, incluida la etiqueta de vía, se reportaron correctos. La [investigación de codificación](plan-codificacion-correos-2026-10-28.md) comprobó literales recodificados en fixtures privados del 28 **antes del JSON** y fuentes de plantilla/dispatchers UTF-8 correctos. Se corrigieron solo futuros fixtures y pasaron 28 pruebas Python offline; las intenciones persistidas, fingerprints, datos finalizados y correos emitidos se conservaron. La evidencia MIME en memoria se informa en su incremento separado y queda pendiente en este corte; **no se declara encoding PASS** ni se atribuye esta entrada de ensayo a un defecto acreditado del transporte del producto.

## Conservación, ahorro y pendientes

El [cierre operativo de ahorro](verificacion-ahorro-post-pruebas-2026-10-28.md) dejó Prometheus/Grafana apagados y Cloud Run con mínimo 0/máximo 1, generación 11 observada, misma revisión/imagen/tráfico, sin cambiar CPU, probes, perfiles o secretos. No hubo sondeos HTTP después de parar el monitoreo. La API conserva arranque por petición; con cero instancias no se ejecutan jobs de fondo y las dosis vencidas se omiten conforme a la decisión aprobada. No se acredita cero instancias inmediato ni gasto total cero.

Quedan abiertos los incumplimientos de latencia M6/M9, la evidencia MIME/codificación del incremento correspondiente y la revisión/publicación de la entrega. No se modificaron automatizaciones ni el backup para este recorrido.

**Evidencia posterior a este corte:** la [verificación de correo UTF-8](verificacion-correos-utf8-2026-10-28.md) acredita las cinco pruebas MIME y la corrección offline de los futuros fixtures. El resumen sanitizado y los estados de los mensajes del ensayo se conservan sin modificación; los incumplimientos de latencia siguen abiertos.

## Procedencia verificable sin datos privados

El resumen público tiene SHA256 `afecef673037921b278cc806c847bc590ee318c48ee7824d529055963abb8382`. Se publican hashes de las fuentes privadas y del colector, no sus filas, IDs, direcciones, contenidos clínicos ni configuración. Los snapshots previos permanecen como cortes independientes.

| Fuente lógica conservada fuera de Git | SHA256 |
| --- | --- |
| report-sanitized.json | `7f984684ac7b21b3ed5510c6233c8e47cdce79a11d2a55e055779db17cebe179` |
| final-after-prescription-end-cut.json | `bd6863e27ad597f6c227d3af0529afeb8e109e552a2476f8554ef3471a1eb76e` |
| cloud-run-latencies-after-finalization.json | `e1ac1f8a6d058c1fa8c569bf6827cf8fe9af3814770dd35056e59a3302c27c88` |
| measurement-source-after-finalize.json | `dd7c15c627ffc75e20cf846986e5d241860e7e32c05577f13c88432874cdfb78` |
| cloud-savings-r4-verification.json | `757ed46d64ad958c58673a7c75d5eb0cc6a20c61711f7a3c344ce81dafbf09ad` |

El SHA del reporte medido coincide con `sourceReportSha256` del colector. El resumen también guarda la huella canónica de la población elegible de 28 intentos y el SHA histórico del 25, para verificar continuidad sin mezclar poblaciones.
