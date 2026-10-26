# Verificación local de admisión de acceso y conservación de evidencia

**Corte: 26/10/2026, America/Bogota. Alcance parcial: P1/P2 web y P3 API.** La auditoría final del proyecto completo queda para su finalización. Este informe acredita implementación y verificación local del incremento I4; la publicación Git, CI remoto, nueva imagen desplegada y activación productiva requieren sus evidencias propias.

## Resultado observado

La ejecución final `mvnw.cmd -B -ntp clean verify` terminó el **26/10/2026 a las 11:45:51 de Bogotá**, con JDK 25 y PostgreSQL 18 local: **722 pruebas, 63 suites, cero fallos, errores u omisiones** y JAR empaquetado. Incluye quince pruebas nuevas de admisión/configuración. Las pruebas nuevas usan un esquema UUID propio; la regresión conserva las verificaciones de roles, sesiones, recuperación, citas, clínica, migraciones y monitoreo.

El contrato **OpenAPI 3.1.0 / aplicación 0.11.0** se generó desde Java: **47 rutas, 68 operaciones, 69 esquemas y 51 ejemplos HTTP reales validados**. El snapshot se copió de `target/generated-openapi/openapi.json` después de la validación independiente; no se editó manualmente. SHA-256 de ambos archivos:

`2109adcd1c915f6c9b714d9a8aa1a15a591064eafe7a20c89ddf3056eda89412`

Los gates comprobaron reportes completos sin pruebas omitidas, igualdad semántica del snapshot generado y rechazo deliberado de drift, OpenAPI inválido, suites omitidas y reportes ausentes. `git diff --check` terminó correctamente.

## Cambios comprobados

- El login existente añade admisión activable mediante configuración: 10 intentos por correo normalizado/peer en 15 minutos y 60 por peer/minuto. PostgreSQL comparte contadores HMAC entre servicios y confirma la admisión en una transacción corta antes de buscar/verificar credenciales.
- El rechazo es `429 LOGIN_RATE_LIMITED`, `Retry-After` entero positivo y respuesta privada/no almacenable. No cambia el estado de la cuenta ni distingue cuentas conocidas, desconocidas o inactivas.
- El modo `PEER` ignora encabezados de reenvío en la aplicación y exige una configuración de servidor que tampoco reescriba el peer. Puede agrupar usuarios bajo un proxy/NAT; no acredita IP humana ni protección completa frente a ataques distribuidos.
- V23 incorpora los contadores y su índice/restricciones. V1–V22 permanecen sin cambios; la prueba de arranque valida las 23 migraciones y la coherencia Hibernate.
- El helper de datos ficticios reconoce el contrato compatible 0.11.0. Su recorrido HTTP volvió a comprobar persistencia, autorización y ausencia de correo externo.
- El workflow backend solicita retención de artefactos de **30 días** para los runs nuevos. Esta edición local no amplía retroactivamente la retención de artefactos existentes ni recupera archivos vencidos.

La [guía de admisión](login-admission.md) especifica configuración, garantía transaccional, expiración, limpieza, límites y operación del cliente. El valor predeterminado sigue siendo deshabilitado hasta validar el entorno/impacto y configurar deliberadamente una clave HMAC estable.

## Evidencia conservada

En el workspace de trabajo, fuera de Git, `output/audit-hardening-2026-10-26/backend/` contiene:

| Archivo | Qué acredita |
| --- | --- |
| `backend-clean-verify-final.log` | Regresión final completa y empaquetado |
| `ci-report-gate.log` | 63 suites / 722 casos completos sin fallos u omisiones |
| `openapi-validation.log` | Contrato válido y 51 ejemplos compatibles |
| `ci-contract-gate.log` | Snapshot generado igual al versionado |
| `ci-controlled-failure-gate.log` | Detección de fallos controlados de los gates |
| `login-verification-sanitized.json` | Resumen, hash, política y límites de esta verificación local |

La primera regresión completa también se conserva como `backend-clean-verify.log`: ejecutó 722 pruebas y encontró tres fallos. Dos provenían de una variable de entorno de la invocación que imponía la URL de pruebas a las comprobaciones de configuración productiva; la ejecución final retiró esos overrides. El tercero detectó que el helper aún rechazaba la versión 0.11.0; se actualizó su lista explícita de contratos compatibles. La regresión completa posterior es la evidencia final; no se ocultaron omisiones ni se cambiaron las expectativas de seguridad para obtener el resultado.

## Pendientes que este informe no cierra

Publicar y comprobar el PR/CI de este incremento; desplegar la imagen aprobada; inspeccionar la configuración real de proxy y evaluar el impacto del peer compartido; configurar y verificar la activación con un ensayo acotado. La prueba local de encabezados falsificados y el contador compartido no sustituyen esa aceptación desplegada. No se utilizó PostgreSQL de Render ni SMTP externo para estas verificaciones, y estos resultados no certifican M1–M9 ni la aceptación final del proyecto.
