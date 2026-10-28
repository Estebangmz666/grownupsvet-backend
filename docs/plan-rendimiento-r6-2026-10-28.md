# Plan previo R6: reducir viajes JDBC conservando las reglas actuales

28/10/2026. Plan escrito antes de modificar fuentes. WindowA de la revisión `grownupsvet-backend-00008-824` correlacionó 24/24 solicitudes. Cinco operaciones siguen por encima de 500 ms: confirmar cita (545,293), crear borrador (615,719), guardar textos (546,875), consultar tratamiento (595,488) y guardar tratamiento (693,694). Cada operación tiene N=1 o N=3; estos resultados no acreditan un SLO general ni eliminan el corte anterior.

## Alcance acotado

1. **Auditoría clínica.** Mantener la escritura del padre en su sentencia actual. Sustituir los dos INSERT de evento y operación por una sentencia con CTE: insertar el evento con `RETURNING`, e insertar la operación consumiendo esa fila. UUID distintos, misma versión, actor, autor anterior, fingerprint e instante. Reutilizarla en creación y en auditoría de actualización/tratamiento/finalización. No agrupar el padre con auditoría: los triggers de elegibilidad e inmutabilidad necesitan observar el estado persistido en el punto actual de la transacción.
2. **Lectura del tratamiento.** Mantener `encounters.get` y su autorización, así como `REPEATABLE_READ`. Leer recetas y controles mediante un `UNION ALL` con discriminador fijo y NULL tipados, ordenado por tipo y posición; separar las filas en listas inmutables de los DTO existentes. Conservar NUMERIC(18,6), timestamps, IDs, instrucciones nulas y orden. Evitar un JOIN que multiplique recetas por controles. También se utiliza en replays.
3. **Confirmación diferida.** La revisión independiente recomienda conservar en este incremento su SELECT de elegibilidad actual. Retener snapshots de filas bloqueadas puede ahorrar otra lectura, pero requiere comprobar por separado todas las identidades y carreras con cambios de agenda. No se modifica `AppointmentService` en R6.

El objetivo verificable local es eliminar una sentencia preparada por auditoría clínica y otra por lectura de hijos. Se cuentan llamadas JDBC de servicio, sin atribuir ese conteo a paquetes de red, triggers, autenticación o commit. No se promete que esa reducción alcance 500 ms en Cloud Run.

## Invariantes y pruebas

- Mantener permisos y replays tras cancelación, inactivación y reasignación; no devolver un DRAFT a quien perdió acceso.
- Mantener locks, transacciones, restricciones diferidas y métricas AFTER_COMMIT. Un fallo de cualquiera de los INSERT de auditoría debe revertir padre, hijos, ambos registros de auditoría y contadores.
- Ejecutar las suites PostgreSQL de transacciones clínicas y citas, HTTP clínicas/historia y recordatorios. Añadir solo casos que acrediten el nuevo riesgo: fallo de operación después del evento agrupado, listas vacías/asimétricas/50 elementos, snapshot y conteos acotados; reutilizar los casos de concurrencia, idempotencia y fallo tardío existentes.
- Revisar diferencias y ejecutar `clean verify`, contrato OpenAPI y gates del proyecto. Conservar también cualquier ejecución fallida; no debilitar pruebas para obtener un resultado favorable.

## Entrega y límite de esta iteración

No cambiar endpoints, DTOs públicos, migraciones V1–V23, fingerprints, configuración ni datos productivos. No activar nueva imagen durante la ventana pendiente de aceptación de `fa2a9955`: la finalización real de esa revisión solo podrá medirse desde 29/10/2026 06:00 America/Bogota. Este incremento se entrega separado, con identidad de commit y validación propia; sus resultados locales no se atribuyen al despliegue actual. El monitoreo permanece apagado y Cloud Run conserva mínimo 0/máximo 1. Al llegar al 20% de cuota semanal restante se detiene el trabajo y se conserva un estado revisable.
