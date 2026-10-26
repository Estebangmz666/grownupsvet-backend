# Integración continua del backend

El plan previo de I0a está en [plan de ejecución](plan-cierre-ejecucion-2026-10-25.md). El flujo `.github/workflows/ci.yml` ejecuta compilación, pruebas y empaquetado con Java 25 y PostgreSQL 18 efímero. Después comprueba que todas las clases de prueba tengan reportes sin fallos ni omisiones, valida el OpenAPI generado y sus ejemplos HTTP y compara el snapshot versionado.

## Entorno y comandos

El runner es `ubuntu-24.04`. PowerShell 7 debe existir para ejecutar las pruebas reales del generador de datos de demostración; su ausencia falla antes de Maven. `application-test.yaml` selecciona exclusivamente `grownupsvet_test` en `localhost:5432`, con credenciales ficticias del servicio efímero. No establecer globalmente `SPRING_DATASOURCE_URL` ni `SPRING_PROFILES_ACTIVE`: las pruebas de configuración necesitan comprobar otros perfiles y destinos ficticios sin un override de mayor prioridad. Las claves JWT se generan en memoria por los fixtures. No se requieren credenciales de PostgreSQL de entrega, Gmail, Google Cloud ni claves de producción.

Con la base de pruebas preparada y `DB_USERNAME` / `DB_PASSWORD` disponibles, reproducir en PowerShell:

```powershell
# En una terminal de pruebas sin overrides de perfiles/datasource heredados:
./mvnw.cmd -B -ntp clean verify
python -m pip install -r scripts/requirements-openapi.txt
python scripts/check_ci_outputs.py reports
python scripts/validate_generated_openapi.py
python scripts/check_ci_outputs.py contract
python scripts/verify_ci_failure_detection.py
```

En Linux se usa `bash ./mvnw -B -ntp clean verify`. No ejecutar dos suites simultáneas sobre esta base: varios casos comparten el esquema público y otros usan esquemas exclusivos.

`check_ci_outputs.py contract` compara JSON por estructura, ignorando orden de claves y orden de conjuntos `required`, `enum` y `type` en esquemas. Conserva el orden de arrays de ejemplos y demás contenido; no elimina rutas, descripciones, servidores ni validaciones de la comparación. El documento procede de `target/generated-openapi/openapi.json`. Si cambia intencionalmente, revisar el contrato, ejecutar la suite y el validador, y regenerar `docs/openapi/openapi.json`; no editarlo manualmente.

`verify_ci_failure_detection.py` crea copias temporales. Comprueba que un cambio de versión no pase inadvertido, que un OpenAPI inválido falle y que pruebas omitidas o reportes ausentes no den una aprobación. También verifica que reordenar conjuntos y objetos no produzca una diferencia falsa. Los documentos de entrega permanecen intactos.

## Evidencia y protección de rama

El workflow se llama **Backend CI** y el job requerido tiene el nombre estable **Backend verification**. Se dispara con push, pull request, merge queue y ejecución manual; no usa filtros de rutas que puedan dejar el check pendiente. Ejecuta los comandos con propagación de fallos. Las ejecuciones obsoletas de una misma referencia se cancelan.

Para bloquear la integración en `main`, configurar la regla de protección o ruleset para exigir pull request y el check **Backend verification** de GitHub Actions, con la rama actualizada o merge queue según la política del repositorio. Revisar también excepciones de administradores y usuarios con bypass. Esta configuración remota y un PR con fallo controlado deben comprobarse antes de afirmar que la integración está bloqueada. Tener el YAML o pruebas locales verdes no demuestra ese bloqueo.

Se conservan por siete días el contrato generado y reportes JUnit sin propiedades del proceso ni salidas capturadas. No se suben los ejemplos con tokens ficticios, logs SMTP, bases de datos ni almacenes de claves. Los artefactos son diagnósticos: no se despliega ni publica una colección Postman desde un PR.

## Referencias verificadas

Las acciones oficiales están fijadas a SHA completo, resuelto contra sus tags el 25/10/2026: [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1), [setup-java v6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0), [setup-python v7.1.0](https://github.com/actions/setup-python/releases/tag/v7.1.0) y [upload-artifact v7.0.2](https://github.com/actions/upload-artifact/releases/tag/v7.0.2). Consultar la [documentación oficial de servicios PostgreSQL](https://docs.github.com/en/actions/tutorials/use-containerized-services/create-postgresql-service-containers) y el [inventario oficial del runner Ubuntu 24.04](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md) al actualizar el entorno.

Estado de esta implementación: pendiente de ejecución remota y comprobación de la regla de protección. La publicación, despliegue, recepción de correo y métricas de producción se acreditan aparte.
