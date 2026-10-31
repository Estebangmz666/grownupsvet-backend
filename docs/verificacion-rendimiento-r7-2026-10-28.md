# Verificación local de R7

28/10/2026. El [plan previo R7](plan-rendimiento-r7-2026-10-28.md) quedó implementado y la regresión completa terminó con **769 pruebas, 65 suites y cero fallos, errores u omisiones**. `clean verify` duró 4 min 10 s y terminó a las 13:56:58 de Bogotá (18:56:58 UTC). R7 todavía no está desplegado; no se acredita una reducción de latencia con estos resultados locales.

La confirmación reutiliza las filas ya bloqueadas y valida sus identidades antes del replay; mantiene el predicado de elegibilidad y los avisos. Las pruebas fijan un máximo de 11 sentencias de servicio y verifican dos carreras PostgreSQL: cambio del veterinario del mismo turno y archivo de mascota mientras la confirmación espera un lock. Ambas rechazan sin efectos adicionales.

GET tratamiento usa dos consultas de servicio: validación inicial del lector y una sentencia que revalida cuenta/rol y obtiene encuentro, versión, recetas y controles. Mantiene los códigos 403/404 y listas vacías, y usa el aislamiento predeterminado readOnly porque los datos y permisos pertenecen al mismo snapshot de sentencia. Historia conserva REPEATABLE_READ; los replays de escritura mantienen su contexto bloqueado. Las pruebas reales verifican respuesta anterior íntegra durante un guardado concurrente, lectura posterior nueva íntegra y rechazo tras desactivación o cambio de rol entre consultas. Se conservaron los casos de listas vacías, asimétricas y 50+50, precisión, orden, permisos, rollback y métricas de commit.

La primera compilación detectó una ambigüedad entre sobrecargas JDBC en un callback. Se corrigió con un bloque lambda sin retorno, sin cambiar la operación. Su log `r7-complete-clean-verify.log` se conserva como fallo; la ejecución completa posterior tiene su propio log `r7-complete-clean-verify-after-lambda-fix.log`. No se convierte ese fallo en PASS retrospectivamente.

Los cuatro gates pasaron: reportes completos sin omisiones; OpenAPI 3.1.0/API 0.11.0 y 51 ejemplos HTTP válidos; snapshot semánticamente coincidente; controles negativos que rechazan deriva, contrato inválido, reportes faltantes y pruebas omitidas. `git diff --check` pasó. No se alteraron endpoints, DTOs públicos, migraciones V1–V23, configuración productiva ni datos de usuarios.

Los archivos de aplicación y pruebas permanecieron congelados durante la ejecución. El archivo privado `output/backend-cierre-2026-10-28/r7-final-reports` conserva 65 XML, contrato y manifiestos:

| Manifiesto/artefacto | SHA256 |
| --- | --- |
| Reportes | `64e8450a1499e25eb9c56b3cd2c872ef1008f65a3082d70cb7f20edfb4fdd136` |
| Aplicación/build, 293 archivos incluido mvnw.cmd | `8029db805c9e00a9a81b3d87ae48d4e96296dabb85c67909d6d8bdbc50041074` |
| Fuentes de pruebas | `e3dfc563e7202b7e4a623544930ad98c2344cf4e0d801264a521f7a9bbd6cd6b` |
| OpenAPI generado | `2109adcd1c915f6c9b714d9a8aa1a15a591064eafe7a20c89ddf3056eda89412` |

La [evidencia R6](verificacion-rendimiento-r6-2026-10-28.md) y el corte desplegado fa2/008 permanecen separados. Siguen pendientes CI remoto, publicación de la imagen nueva y medición desplegada; Window B de fa2 no se adelanta al inicio real del 29/10/2026 a las 06:00 Bogotá. Los cinco incumplimientos de Window A siguen abiertos. Cloud Run y el monitoreo no se modificaron durante R7.
