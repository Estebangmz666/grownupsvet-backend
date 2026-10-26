# Verificación del perfil para monitoreo local

25/10/2026, America/Bogota. Implementación posterior al CORE `4bdaf2b`, con [plan previo](plan-monitoreo-local-2026-10-25.md). Esta evidencia corresponde al código y a servidores locales de pruebas; el scrape de Cloud Run se acredita por separado después del despliegue.

## Resultado local

`mvnw.cmd -B -ntp clean verify` terminó correctamente a las 23:04:56: **706 pruebas, 61 suites, cero fallos, errores u omisiones**. Incluye el perfil nuevo `monitoring`, regresión del perfil `observability`, clínica, citas, permisos, migraciones y contrato.

La revisión independiente detectó después que Spring Boot permite ampliar detalles y rutas de salud mediante propiedades por grupo. Antes de corregir se amplió el plan. Se añadió la validación de `liveness` y `readiness`, con diez configuraciones negativas y dos positivas en una prueba adicional. `mvnw.cmd -B -ntp -Dtest=MonitoringConfigurationTests,MonitoringHttpIntegrationTests verify` terminó a las 23:08:23: **12 pruebas, cero fallos, errores u omisiones**. El inventario local reúne **707 casos distintos**, sin presentar estas dos ejecuciones como una única regresión completa de 707.

Los gates independientes comprobaron las 61 suites esperadas, OpenAPI 3.1/contrato 0.10.0, 50 ejemplos HTTP, coincidencia del contrato generado con el snapshot y detección de drift, OpenAPI inválido, omisiones y reportes faltantes. No se añadieron rutas al contrato de negocio ni dependencias Maven. `git diff --check` no encontró errores.

Logs completos fuera de Git: `output/backend-cierre-2026-10-25/monitoring-final-verify.log` y `monitoring-health-groups-focused.log`, relativos al directorio padre del repositorio.

## Garantías comprobadas

- HTTP real y PostgreSQL: un token máquina válido obtiene métricas, etiquetas acotadas y el bucket de 500 ms; el filtro no se registra globalmente.
- Sin token, valor incorrecto, hash usado como token y JWT de cualquiera de los cuatro roles: 401, sin entrar al validador de cuentas humanas.
- La clave máquina no autoriza otras rutas API; métodos distintos de GET y subrutas no entregan métricas.
- Los rechazos y respuestas de scrape usan `no-store`; tokens, hash, correo, IDs y query privados no aparecen en las series verificadas.
- Probes públicas originales mínimas conservadas. Arranque rechazado ante hash inválido, perfiles incompatibles, gestión separada, exposición/rutas ampliadas y detalles de salud globales o por grupo.
- La revisión corrigió también el remapeo individual de endpoints Actuator: las métricas no pueden trasladarse fuera de la cadena protegida por una sobrescritura de configuración.

## Límites pendientes

El runtime Windows y sus procesos tienen verificación independiente. Falta reconstruir la imagen del commit final, pasar CI remoto, comprobar la revisión nueva de Cloud Run y su scrape desde Prometheus local. Ningún resultado de este informe acredita M1–M9, recepción SMTP de esta revisión o disponibilidad mientras el computador esté apagado.
