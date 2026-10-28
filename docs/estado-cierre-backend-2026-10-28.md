# Estado del cierre del backend — 28/10/2026

El objetivo completo **sigue abierto**. La aceptación desplegada es satisfactoria en los casos funcionales definidos y conserva seis incumplimientos de latencia. Hay cambios de rendimiento en desarrollo; las pruebas de un incremento no certifican todavía toda la entrega ni una imagen que aún no se ha publicado.

Se conserva el alcance de nueve incrementos del plan del 23/10. El usuario excluyó adendas y Jenkins, eligió Prometheus/Grafana OSS locales, aprobó mínimo cero al terminar ensayos y fijó parada de este chat al detectar 20% o menos de cupo semanal restante. Los trabajos usan ramas/worktrees separados; los despliegues y la base compartida requieren coordinación.

| Bloque | Evidencia disponible | Trabajo que impide declararlo cerrado en la entrega nueva |
| --- | --- | --- |
| I0 · Base de entrega y CI | Main protegido y pruebas de rechazo controlado en el expediente de auditoría del 26; baseline 722 pruebas; controles/colección de latencias versionados localmente | Publicar los commits nuevos y comprobar su CI requerido; enlazar commit, imagen y revisión realmente desplegada |
| I1 · Acuerdos y contrato | Reglas clínicas aprobadas; contrato 0.11.0 y frontend compatible con COMPLETED | Conservar el contrato al terminar los refactores y verificar el snapshot generado de la entrega |
| I2 · Borrador | API/roles/auditoría existentes; creación y lectura verificadas en el ensayo desplegado | Validar regresión de las nuevas escrituras RETURNING y del contexto de locks |
| I3 · Fórmula y controles | Posología estructurada, límites, reemplazo y replay existentes; tratamiento persistido en el ensayo | Probar escritura agrupada con 0/1/50 hijos, precisión, orden y rollback tardío |
| I4 · Finalización | FINALIZED/COMPLETED, replay e inmutabilidad verificados; constraints y pruebas transaccionales existentes | Revalidar atomicidad y carreras tras el refactor, y medir la revisión nueva |
| I5 · Historia | Lectura de propietario/finalizador y rechazos ajenos verificados; alcance veterinario aprobado | Regresión de permisos y contrato en el conjunto final |
| I6 · Recordatorios | Tres avisos recibidos por el usuario; una dosis, fin de receta y ausencia de acumulación comprobados | Regresión del scheduling por conjunto y pruebas de reinicio/leases; conservar el límite SMTP at-least-once |
| I7 · Observabilidad | Scrape autenticado, revisión, diez paneles y nueve PromQL verificados; 28/28 traces correlacionados | Mostrar y verificar datos de la revisión nueva tras desplegarla; conservar métricas de negocio AFTER_COMMIT |
| I8 · Cierre integrado | 50 comprobaciones, 156 HTTP y M1–M9 con denominadores; restauración V23 en auditoría | Resolver los seis incumplimientos medidos y reunir evidencia final de código, CI, despliegue y correo |

## Evidencias de esta reanudación

- [Aceptación desplegada y medidas exactas](aceptacion-backend-desplegado-2026-10-28.md): tasas pequeñas y secuenciales, sin inferencia poblacional ni prueba de capacidad. El documento conserva errores, unidades y corte de cada muestra.
- [Monitoreo desplegado](verificacion-monitoreo-desplegado-2026-10-28.md) y [ahorro posterior](verificacion-ahorro-post-pruebas-2026-10-28.md): estados históricos distintos, sin vigilancia permanente ni promesa de gasto total cero.
- [Causa y corrección de fixtures de correo](plan-codificacion-correos-2026-10-28.md): datos ficticios recodificados antes de JSON; no se reescribe la atención inmutable ni se recodifican datos de usuarios mediante heurísticas.
- Primer focal de rendimiento/autenticación/MIME: **149 pruebas, ocho suites, cero fallos/errores/omitidas**. Se archivaron XML y hashes fuera de Git antes de la siguiente modificación. Incluye cinco pruebas MIME y once de snapshot/revocación con PostgreSQL; no es la regresión completa del código posterior.
- [Regresión completa posterior](verificacion-rendimiento-regresion-2026-10-28.md): **762 pruebas, 65 suites, cero fallos/errores/omitidas**, contrato/snapshot y gates negativos correctos. Incluye las escrituras agrupadas y el cierre del replay de DRAFT inelegible. La validación desplegada de ese código continúa pendiente.
- Checker de monitoreo: 18 fixtures aislados; colector: 24 pruebas offline; workflow verificado con actionlint. Los mensajes de rechazo de los fixtures negativos son resultados previstos.

## Alcance y precauciones para cerrar

La evidencia previa de auditoría y los ensayos históricos permanecen separados. El próximo corte deberá identificar su commit/digest/revisión y medir cada operación con el mismo criterio nearest rank, manteniendo los intentos no verificados. Contar menos llamadas SQL no demuestra por sí solo P95 menor de 500 ms. Tampoco se puede reabrir una atención finalizada, alterar la hora de una cita o cambiar el reloj para obtener una muestra favorable.

El backup estable y la automatización del 13 de noviembre se conservan. El pendiente del diagrama C4 está registrado en el frontend: Cloudflare Pages, Cloud Run y Render Free Tier. Corregir ese diagrama y construir pantallas clínicas no forman parte de la implementación del backend solicitada aquí.
