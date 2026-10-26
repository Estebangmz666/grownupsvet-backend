# Verificación del respaldo Render — 25 de octubre de 2026

Resultado: **respaldo real obtenido, restaurado en una base local nueva y migrado de V18 a V22 con validación JPA satisfactoria**. El dump original permanece intacto fuera de Git. No se ejecutaron migraciones ni SQL destructivo en Render durante este ensayo.

## Ensayo sintético del helper

`scripts/tests/BackupRenderDatabase.Tests.ps1` completó **31 comprobaciones** con PostgreSQL 18 real, sin Maven ni acceso remoto. Cubrió guardas offline de origen/TLS/rutas, rechazo de certificados hoja como CA, separación de credenciales, errores saneados, dump custom, catálogo, hash, promoción y restauración transaccional de un esquema UUID propio. Comparó filas ficticias, Unicode y bytea, y eliminó exclusivamente ese esquema.

Reporte privado:

`C:\Users\ASUS\Documents\GrownupsVetBackups\verification-2026-10-25\24a50aa0770a4decbde8240d944d27b5\verification.json`

Ese reporte conserva `productionRestoreVerified=false`: sus datos eran sintéticos. La prueba real de las secciones siguientes es independiente. Una revisión estática adicional del helper/tests no encontró defectos materiales de origen, credenciales, TLS, promoción o limpieza.

## Respaldo real de Render

La ejecución fue autorizada antes del despliegue clínico. Se usaron la conexión DPAPI existente y el host externo de Render; no se imprimieron sus valores privados.

| Comprobación | Resultado |
| --- | --- |
| Creación | 25/10/2026 22:30:32 America/Bogota |
| Base/esquema esperados | `grownupsvet` / `public` |
| Alcance | Base completa; sin propietarios/ACL del proveedor |
| Cliente/servidor | PostgreSQL 18 / `180006` |
| TLS | `verify-full`; conexión efectiva TLSv1.3 |
| Tamaño dump | 94.885 bytes |
| Entradas de catálogo | 161 |
| SHA-256 | `ab847fb4fea5ab591d24bbcf860f1038a5a30a8a0a5e8d9c4caf2d95e5c12843` |

Directorio privado:

`C:\Users\ASUS\Documents\GrownupsVetBackups\2026-10-25\grownupsvet-render-20261025-223032`

Contiene `.dump`, `.sha256`, `.catalog.txt` y `.manifest.json`, promovidos juntos tras las comprobaciones. El manifiesto original conserva `restoreVerified=false`, coherente con lo que hizo el helper en ese momento. El reporte separado siguiente acredita la restauración real posterior sin modificar evidencia original.

## Restauración real y ensayo previo a P3

Se utilizó el mismo dump, volviendo a verificar SHA y sus 161 entradas. El destino fue una base **nueva**, de nombre `grownupsvet_render_restore_<UUID32>`, en `127.0.0.1:5432`. No se restauró en `grownupsvet_test`, en desarrollo o en Render. `pg_restore` terminó con `--single-transaction --exit-on-error --no-owner --no-privileges`.

Un harness privado externo al repositorio reutilizó las dependencias del artefacto ya verificado y las clases compiladas del backend. No se añadió un test dependiente de datos privados a CI ni se volvió a ejecutar Maven.

| Fase | Usuarios | Mascotas | Citas | Flyway |
| --- | ---: | ---: | ---: | --- |
| Dump restaurado | 10 | 6 | 0 | V1–V18 aplicadas |
| Después de migrar | 10 | 6 | 0 | V1–V22 SUCCESS |
| Después de arrancar JPA | 10 | 6 | 0 | Validación satisfactoria |

Se comprobaron exactamente cuatro pendientes iniciales, V19–V22. La validación previa del historial aplicado usó la excepción documentada `versioned:pending`; se mantuvo la comprobación de checksums/nombres de las migraciones ya aplicadas. `migrate()` ejecutó **cuatro** migraciones y después `validate()` estricto terminó correctamente con **22 versiones SUCCESS**, sin excepción de pendientes. No se invocaron repair/clean ni se editaron filas de historial.

Se calculó el SHA-256 de las 22 migraciones fuente, se comparó con su copia compilada y se verificó que no cambiara durante el ensayo. Los hashes y checksums Flyway completos están en el reporte privado. Para P3:

| Migración | SHA-256 fuente |
| --- | --- |
| V19 | `494ca0977f398f4c7488dd0b92d736eadb8a6535e727b2a5a3ed4015e3f188fc` |
| V20 | `1b197607857575f382b33fc0fcf6ade1f95dc13b15d17062e7f6b3d5cc9e5532` |
| V21 | `149bfe1e8d340b36cb41a71a3d0bac8340a415939c89b09b975bf21b7d0b8d66` |
| V22 | `9939c85f2f269121859667ff74bacce3c9c9b60314b7d626565711b240f71068` |

El contexto Spring arrancó con `ddl-auto=validate`, perfil test, HTTP en loopback/puerto efímero y RSA efímero en memoria. Se retiró el procesador de `@Scheduled` solamente en el harness y se deshabilitó el worker clínico. Un sender aislado impidió SMTP; el conteo de intentos fue **0**. El `EntityManagerFactory` quedó abierto y válido antes de cerrar el contexto. No se atendieron peticiones ni se utilizaron claves JWT reales o secretos cloud.

Los primeros intentos del harness se detuvieron ante los cuatro pendientes esperados de `validate` estricto y ante la propiedad local de horario ausente del classpath privado. Se corrigió exclusivamente el harness; no hubo fallos de migración, modificaciones de aplicación o reparación de esquema. Cada base temporal se eliminó después del intento correspondiente.

El ensayo final terminó aproximadamente a las **22:46 America/Bogota**. El cierre confirmó conexiones terminadas, servidor local, propietario y nombre exacto antes de eliminar únicamente su base UUID, sin FORCE ni terminación de sesiones ajenas. Se eliminaron las credenciales temporales. El SHA del dump original siguió idéntico.

Reporte saneado final:

`C:\Users\ASUS\Documents\GrownupsVetBackups\real-restore-verification-2026-10-25\6252fc2a33ca4f2c91fadab8a5e5f5e8\verification.json`

Campos finales: `status=PASSED`, `restoreVerified=true`, `migrationsVerified=true`, `jpaValidated=true`, `databaseRemoved=true`, `credentialFileRemoved=true`, `migrationSourcesUnchanged=true`, `sourceDumpUnchanged=true`.

La conservación de esos tres conteos y la validación de esquema acreditan este ensayo concreto. No equivalen a comparar todas las filas/bytes del origen remoto ni sustituyen el smoke test del despliegue.

## Copia estable para el 13/11

Se instaló el helper autónomo y su manifiesto en `%USERPROFILE%\.grownupsvet\backups\scripts`, con ACL usuario/SYSTEM. Repositorio, copia estable y manifiesto coincidieron en SHA-256:

`1ba654532575edacf4f876e0caee3717c2c6d99cd94cc87ef67ba18b156166fa`

La copia estable superó `-ValidateOnly` para el destino del 13/11, sin conexión a Render. Procedimiento de operación: [respaldo-render.md](respaldo-render.md).
