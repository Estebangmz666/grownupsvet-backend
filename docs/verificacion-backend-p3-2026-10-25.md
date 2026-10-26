# Verificación local de P3 y preparación de entrega

25 de octubre de 2026, America/Bogota. Este informe distingue funcionalidad local, publicación Git y aceptación del despliegue.

## Resultado comprobado

`./mvnw.cmd -B -ntp clean verify`: **695 pruebas, 59 suites, cero fallos, errores u omisiones**, con PostgreSQL 18 real y Java 25. La compilación produjo el JAR ejecutable. No se usaron SMTP externo ni datos clínicos de personas reales.

El control de CI verificó que no faltara ninguna clase de pruebas. El contrato OpenAPI **3.1.0 / versión 0.10.0** contiene **47 rutas, 68 operaciones y 69 esquemas**. Se validaron **50 respuestas HTTP reales** contra sus esquemas, además de obligatoriedad, nulabilidad, enums, tipos y errores. El snapshot versionado se regeneró desde HTTP y coincide con la fuente generada.

Las pruebas deliberadas de los gates rechazaron contrato inválido, deriva del snapshot, suites omitidas y reportes ausentes. El workflow pasó actionlint con acciones oficiales fijadas a SHA.

## Alcance funcional verificado

| Bloque | Garantías comprobadas |
| --- | --- |
| Borrador | Una atención por cita confirmada; autorización por recurso; idempotencia; versiones y continuidad tras reasignación con creador/auditoría conservados. |
| Fórmula y controles | Datos estructurados, dosis sin redondeo, fechas explícitas, listas acotadas, reemplazo transaccional y versión común del agregado. |
| Finalización | Desde el inicio del turno; cinco textos obligatorios; FINALIZED y COMPLETED en el mismo commit; replay y rollback tardío; constraints diferidos e inmutabilidad SQL. |
| Historia | Solo publicados; propietario propio con mascota archivada; veterinario con asignación vigente o responsable de la atención finalizada; totales filtrados y snapshot por respuesta. |
| Recordatorios | Programación dentro del commit, cursores compactos, controles 24 h antes/inmediatos, ventana acotada, omisión de vencidos, recuperación tras reinicio, leases, reintentos, doble revalidación y SMTP fuera de transacción. |
| Observabilidad | Puerto de gestión loopback separado; métricas públicas denegadas; probes mínimas; histogramas de 500 ms y siete contadores posteriores al commit, sin PII ni éxitos por rollback/replay. |
| Regresión | Usuarios, mascotas, autenticación, disponibilidad, citas, correos, migraciones, restauración y generador de datos de demostración. |

La suite incluye diez casos de historia y dieciocho de recordatorios. Las pruebas transaccionales clínicas usan conexiones independientes, esquemas UUID y fallos SQL inducidos. La paginación se documenta como vista viva entre solicitudes; cada respuesta conserva count/filas coherentes. SMTP mantiene garantía de al menos una vez y puede tener duplicados inciertos tras una aceptación sin confirmación durable.

## Compatibilidad y preparación operativa

- V1–V18 se conservaron intactas. V19–V22 agregan clínica y recordatorios; la prueba de arranque comprueba las 22 migraciones y sus huellas.
- Se respaldaron los 33 archivos anteriores fuera del repositorio antes de trabajar. Los archivos extendidos conservan copia íntegra y manifiesto SHA256 de su estado inicial.
- El generador P1/P2 admite explícitamente los contratos conocidos 0.9.0 y 0.10.0. Su ensayo HTTP completo volvió a pasar después del ajuste.
- La compatibilidad frontend de `COMPLETED` tiene 203 pruebas focalizadas, tipos, lint y exportación web correctos. Su CI remoto aprobó 818 pruebas. Está propuesta en [PR frontend 1](https://github.com/Estebangmz666/grownupsvet-frontend/pull/1); debe integrarse antes de usar finalizaciones P3 contra el frontend publicado.
- OWNER y VETERINARIAN deben iniciar sesión nuevamente tras publicar los permisos nuevos. Evitar tráfico dividido entre revisiones que resuelvan permisos distintos.
- Una vez guardado COMPLETED, una imagen anterior sin ese enum puede fallar al leer citas. El retorno a una revisión anterior debe considerar los datos evolucionados; priorizar una corrección compatible hacia delante y conservar respaldo previo al despliegue.
- promtool 3.15.1 y Alloy 1.21.2 validaron las configuraciones originales con salida 0 y binarios oficiales/SHA256 verificados. Véase [evidencia de CLI](verificacion-configuracion-observabilidad-2026-10-25.md).

## Pendientes para declarar terminado el backend desplegado

El CORE ya se publicó en PR 2 con CI remoto verde y la protección de main impidió efectivamente una integración con contrato divergente; consultar la [verificación de protección](verificacion-proteccion-integracion-2026-10-25.md). El frontend compatible con COMPLETED se integró y está publicado. El [perfil de monitoreo local](verificacion-monitoreo-backend-2026-10-25.md) es un incremento posterior, que exige un nuevo commit, CI e imagen.

Faltan publicar la revisión final en Cloud Run; habilitar la recolección desplegada con Prometheus/Grafana OSS locales; ejecutar escenarios controlados que acrediten M1–M9 con denominador y oráculo de persistencia; verificar el recorrido de correo de la nueva revisión y la aceptación P1–P3. Los contadores operativos y las pruebas locales no sustituyen estos resultados de producción.

Se conserva el límite autorizado de créditos gratuitos y no se contempla actualizar la cuenta a pago. La configuración real de Cloud Run se respaldó privadamente antes de preparar cambios; sus secretos y manifiesto operativo completo no se publican en este informe.

## Reproducción

Ejecutar los comandos de [integración continua](integracion-continua.md), con PostgreSQL local exclusivo `grownupsvet_test`, Java 25 y las credenciales locales de pruebas. Los planes previos y aprobaciones están en [ejecución del cierre](plan-cierre-ejecucion-2026-10-25.md), [historia](plan-historia-clinica-2026-10-25.md), [recordatorios](plan-recordatorios-clinicos-2026-10-25.md) y [métricas](plan-metricas-transaccionales-2026-10-25.md).
