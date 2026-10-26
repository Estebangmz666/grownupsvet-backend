# Plan previo: respaldo de Render antes de su vencimiento

Preparado el 25/10/2026 para el respaldo solicitado el **13/11/2026 a las 09:00, America/Bogota**, antes del vencimiento informado del 14/11. La automatización de una sola ejecución la administra la raíz. Este plan se escribe antes de implementar el helper.

## Origen y alcance

Se reutilizarán `pg_dump`, `pg_restore` y `psql` oficiales de PostgreSQL 18 ya instalados. El origen esperado es la base `grownupsvet` de Render. La conexión predeterminada procede exclusivamente del objeto DPAPI existente `%USERPROFILE%\.grownupsvet\cloud-runtime\render-connection.clixml` (Host, Port, Database, Username y Password SecureString). No se consultan otros almacenes privados ni se crean credenciales nuevas. La URL `RENDER_DATABASE_URL` queda como alternativa explícita de proceso, sin aceptar contraseñas en parámetros del script.

El respaldo normal cubre toda la base, sin propietarios ni ACL dependientes del proveedor. Incluye datos, fotos bytea, migraciones y colas persistidas. No respalda roles globales del clúster, cuentas del proveedor, secretos externos, imágenes de contenedor ni archivos fuera de PostgreSQL.

## Pasos del helper previstos antes de implementar

1. Resolver herramientas 18 y validar argumentos/rutas. El directorio final será fuera de Git: `C:\Users\ASUS\Documents\GrownupsVetBackups\2026-11-13`. Rechazar repositorios, junctions/reparse points y sobrescrituras; limitar escrituras y promoción al directorio comprobado.
2. Leer conexión privada sin mostrar valores. Validar base esperada, identificadores y host externo de Render; la selección local de pruebas exige loopback, base terminada en `_test` y esquema UUID propio. No hacer una conexión de pruebas hacia Render por omisión.
3. Crear un `PGPASSFILE` temporal con ACL limitada al usuario/SYSTEM y escapar separadores según libpq. No poner contraseña en argv, logs, manifiesto o entorno `PGPASSWORD`. Eliminar variables de conexión PG heredadas en los procesos hijos para impedir que cambien el destino.
4. En producción forzar **sslmode=verify-full** y deshabilitar GSS para exigir TLS. Usar un PEM de CA explícito verificado o exportar únicamente las raíces públicas ya confiadas por Windows a un bundle temporal; no aceptar certificados del servidor como nuevas raíces ni omitir validación de hostname. La excepción `sslmode=disable` se limita a un test local loopback con esquema UUID y selección de esquema obligatoria.
5. Antes del dump, consultar `current_database()`, versión, existencia del esquema esperado y tablas marcadoras (`flyway_schema_history`, `users`, `pets`, `appointments`), más TLS negociado. Abortar ante discrepancia. No publicar filas ni diagnósticos PG crudos.
6. Escribir `grownupsvet-render-YYYYMMDD-HHMMSS.dump` custom en un directorio temporal del destino. Comprobar cabecera PGDMP, `pg_restore --list`, presencia de objetos esperados, tamaño y SHA-256. Crear lista, archivo `.sha256` y manifiesto saneado que distinga catálogo válido de restauración efectuada.
7. Promover el directorio completo únicamente cuando todos esos pasos hayan terminado bien. El directorio temporal de credenciales es independiente y nunca se promociona junto al respaldo. Ante fallo no presentar un dump parcial como respaldo; informar etapa/código saneados y limpiar solo temporales propios comprobados.
8. No ejecutar SQL destructivo en Render. Probar el circuito en un esquema UUID exclusivo de `grownupsvet_test`: crear datos ficticios, dump limitado a ese esquema, listar, quitar únicamente ese esquema y restaurarlo en una transacción. Comparar datos/bytes/objetos y limpiar el esquema propio. Un dump remoto posterior, autorizado por la raíz antes de migrar P3, se documentará como respaldo real; su catálogo no prueba por sí solo una restauración completa.

## Pruebas previstas

- Guardas offline: origen incorrecto, nombre esperado incompatible, URI con configuración insegura/desconocida, test dirigido a host remoto o esquema ajeno, destino dentro de Git, path con reparse point y colisiones. Ninguna guarda debe contactar una base o imprimir credenciales.
- Ensayo local con herramientas reales 18: custom dump, SHA, catálogo, restauración aislada y equivalencia de contenido bytea/fila. Sin Maven ni cambios de aplicación/migraciones.
- Revisar stdout/stderr/manifiesto/archivos promovidos contra marcadores privados ficticios; PGPASSFILE temporal eliminado tras éxito/fallo.
- Distinguir `catalogVerified` de `restoreVerified`. Registrar fallos sin convertir el simple archivo existente en un éxito.

## Fuentes técnicas primarias

- [pg_dump 18](https://www.postgresql.org/docs/18/app-pgdump.html): exportación consistente de una base y formato custom.
- [pg_restore 18](https://www.postgresql.org/docs/18/app-pgrestore.html): listado, restauración transaccional y origen confiable.
- [TLS de libpq 18](https://www.postgresql.org/docs/18/libpq-ssl.html): CA confiable y validación de hostname con verify-full.
- [PGPASSFILE 18](https://www.postgresql.org/docs/18/libpq-pgpass.html): formato, escape y protección del archivo.
- [Conexión a Render Postgres](https://render.com/docs/postgresql-creating-connecting): host externo y TLS del proveedor.

La verificación local histórica del 10/10 se conserva como antecedente y no sustituye este nuevo ensayo ni un respaldo real de Render.

## Resultado

Helper implementado; validación local final de 31 comprobaciones y respaldo real Render del 25/10 completados. Los resultados detallados se documentan por separado.

## Addendum previo: restauración del dump real y ensayo de migración P3

La raíz autorizó explícitamente este ensayo después del respaldo real. Antes de ejecutarlo:

1. Confirmar SHA/catalogación del dump original sin modificarlo. Crear únicamente una base **local nueva** `grownupsvet_render_restore_<UUID32>` en `127.0.0.1:5432`, con el usuario local existente. Comprobar ausencia previa y guardar que fue creada por este ensayo. No restaurar en `grownupsvet_test`, `grownupsvet_dev` o Render.
2. Restaurar el dump real con `pg_restore --single-transaction --exit-on-error --no-owner --no-privileges`. Conexión y contraseña solo mediante archivo privado/entorno del proceso, sin mostrarlas. Capturar únicamente conteos de usuarios/mascotas/citas y versiones/checksums de Flyway; no consultar PII o payloads.
3. Crear un harness Java temporal **fuera de Git**, con dependencias del artefacto ya verificado. Ejecutar `Flyway.validate()` y migración de 19 a 22 exclusivamente contra la copia local. No ejecutar repair/clean ni SQL en Render. Calcular SHA de las migraciones fuente antes/después y validar de nuevo historial/éxito de las 22 migraciones.
4. Si el classpath lo permite, abrir Spring/JPA contra esa misma copia en loopback/puerto efímero, perfil test, RSA efímero y todos los procesadores programados deshabilitados. Proveer un sender que impida SMTP y comprobar que no fue invocado. No usar secretos de nube ni claves JWT reales. Deshabilitar logs del harness para impedir que errores SQL expongan filas privadas.
5. Comparar conteos antes/después de migrar y de arrancar JPA. Cerrar conexiones/contextos. Antes de DROP comprobar host/base de mantenimiento local, prefijo UUID exacto y propiedad de la base creada por el usuario local; eliminar únicamente esa base sin FORCE ni terminar sesiones ajenas. Mantener el dump original intacto y verificar de nuevo su SHA.
6. Guardar reporte saneado privado con conteos, versiones, hashes de migraciones y resultados/limpieza, diferenciando este ensayo real de la prueba sintética anterior y del entorno remoto.

### Precisión del preflight Flyway

La primera validación estricta de la copia encontró exclusivamente V19–V22 resueltas y pendientes, sin divergencias de V1–V18. El harness comprobará que esos son exactamente los cuatro pendientes y usará `ignoreMigrationPatterns=versioned:pending` únicamente para validar el historial ya aplicado antes de `migrate`. Al terminar exigirá validación estricta sin esa excepción y las 22 versiones aplicadas. [Flyway documenta esa semántica](https://documentation.red-gate.com/fd/flyway-ignore-migration-patterns-setting-277579002.html); no implica reparación ni modificación del historial previo.
