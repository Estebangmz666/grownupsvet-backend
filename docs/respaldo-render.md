# Respaldo de PostgreSQL en Render

El helper `scripts/backup-render-database.ps1` crea un respaldo completo custom con PostgreSQL 18, comprueba la identidad del origen, exige TLS `verify-full` y publica el resultado solamente cuando el dump, catálogo y SHA-256 son válidos. El plan previo está en [plan-respaldo-render-2026-11-13.md](plan-respaldo-render-2026-11-13.md).

## Operación del 13 de noviembre de 2026

Existe una automatización de Codex de una sola ejecución para el **13/11/2026, 09:00 America/Bogota**, antes del vencimiento previsto para el 14/11. No se añadió una tarea de Windows. La ejecución necesita esta cuenta de Windows, PostgreSQL 18 instalado, acceso al archivo DPAPI existente y conectividad al host externo de Render.

La fuente versionada permanece en el repositorio. Para que cambiar de rama no quite el helper, se instaló una copia autónoma privada:

- Script: `%USERPROFILE%\.grownupsvet\backups\scripts\backup-render-database.ps1`.
- Manifiesto de instalación: `%USERPROFILE%\.grownupsvet\backups\scripts\backup-render-database.manifest.json`.
- SHA-256 instalado: `1ba654532575edacf4f876e0caee3717c2c6d99cd94cc87ef67ba18b156166fa`.

Antes de usar esa copia hay que comprobar que `file` sea exactamente `backup-render-database.ps1` y que su SHA-256 coincida con `sha256` del manifiesto. Ante discrepancia no ejecutar una copia alterada. El repositorio es la alternativa, después de verificar su versión. Al actualizar el helper se debe reinstalar deliberadamente la copia estable y renovar su manifiesto/hash.

Comando previsto, una vez comprobada la copia:

```powershell
& "$env:USERPROFILE\.grownupsvet\backups\scripts\backup-render-database.ps1" `
  -ExpectedDatabaseName grownupsvet `
  -ExpectedSchemaName public `
  -OutputDirectory 'C:\Users\ASUS\Documents\GrownupsVetBackups\2026-11-13'
```

`-ValidateOnly` comprueba herramientas, argumentos, origen declarado y destino **sin conectarse**. No sustituye la comprobación online de identidad ni produce un respaldo.

## Conexión y privacidad

Por omisión se importa exclusivamente `%USERPROFILE%\.grownupsvet\cloud-runtime\render-connection.clixml`, ya existente y protegido por DPAPI. Se esperan Host, Port, Database, Username y Password como SecureString. No se crea un secreto nuevo ni se consultan otros almacenes.

Como alternativa explícita, `-UseEnvironmentConnection` lee `RENDER_DATABASE_URL` del entorno del proceso. Nunca se pasa una contraseña como argumento. No se acepta un host interno o distinto del origen externo Ohio de Render para esta operación.

Los hijos reciben un entorno reducido y un `PGPASSFILE` temporal con ACL de usuario/SYSTEM. No reciben las demás variables privadas del backend. El archivo de contraseña se elimina antes de promocionar el respaldo. Los errores nativos se sustituyen por etapas/códigos saneados; no se publican stderr de PostgreSQL, URI, contraseñas, usuarios o filas.

Se usa `sslmode=verify-full`, comprobando cadena y hostname. El helper genera un bundle PEM temporal desde las raíces públicas ya confiadas por Windows. `-SslRootCertificatePath` permite proporcionar un bundle CA público explícito; rechaza claves privadas y certificados hoja. No se incorpora como raíz un certificado descargado del propio servidor. Si no existe confianza válida, la operación falla sin reducir TLS. Se deshabilita GSS para exigir TLS.

## Contenido y resultado

Cada ejecución publica un directorio `grownupsvet-render-YYYYMMDD-HHMMSS` con cuatro archivos:

1. `grownupsvet-render-YYYYMMDD-HHMMSS.dump`: formato custom de `pg_dump`.
2. Archivo `.sha256`: integridad del dump.
3. Archivo `.catalog.txt`: listado de `pg_restore --list`.
4. Archivo `.manifest.json`: fecha/zona, versión de herramientas/servidor, alcance, bytes, SHA y resultado de las verificaciones.

La fecha del nombre usa America/Bogota. La salida permanece fuera de Git, con ACL privada; se rechazan destinos dentro de repositorios, reparse points y sobrescrituras. Los archivos se escriben en un directorio temporal y se promociona el conjunto mediante renombrado al terminar correctamente.

El alcance normal es toda la base: tablas, datos, fotos bytea, historia Flyway y colas durables. Se omiten propietarios y privilegios del proveedor para facilitar restauración. No incluye roles globales del clúster, secretos externos, configuración de Render ni archivos ajenos a PostgreSQL.

`BACKUP_VERIFIED` significa que el origen fue comprobado y el archivo tiene catálogo/integridad válidos. El manifiesto mantiene `restoreVerified=false` porque el helper no restaura automáticamente. Una restauración efectuada tiene su reporte independiente; no se modifica retroactivamente el manifiesto original.

## Verificación y restauración

La suite local se ejecuta sin Maven ni acceso a Render:

```powershell
.\scripts\tests\BackupRenderDatabase.Tests.ps1
```

Requiere `DB_USERNAME`/`DB_PASSWORD` locales y PostgreSQL 18. Crea únicamente un esquema UUID sintético en `grownupsvet_test`, ensaya dump/restauración con filas y bytea, y elimina su propio esquema. No es una prueba del contenido real de Render. El resultado del 25/10 fue de 31 comprobaciones satisfactorias.

Para un ensayo real se verifica primero el SHA, se crea una **base local nueva** `grownupsvet_render_restore_<UUID32>` y se restaura con `pg_restore --single-transaction --exit-on-error --no-owner --no-privileges`. Nunca se usa una base de desarrollo/pruebas compartida como destino. El reporte compara únicamente conteos y versiones/checksums de migraciones. Después de cerrar conexiones se elimina solo esa base, comprobando loopback, nombre exacto y propietario. No se ejecutan `Flyway repair`, `Flyway clean` o SQL destructivo sobre Render.

Los ensayos y el respaldo real del 25/10 están registrados en [verificacion-respaldo-render-2026-10-25.md](verificacion-respaldo-render-2026-10-25.md). El dump original se conserva fuera del repositorio.

## Referencias oficiales

- [pg_dump 18](https://www.postgresql.org/docs/18/app-pgdump.html).
- [pg_restore 18](https://www.postgresql.org/docs/18/app-pgrestore.html).
- [TLS de libpq 18](https://www.postgresql.org/docs/18/libpq-ssl.html).
- [Archivo de contraseñas de libpq 18](https://www.postgresql.org/docs/18/libpq-pgpass.html).
- [Conexión a Render Postgres](https://render.com/docs/postgresql-creating-connecting).
