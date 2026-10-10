# Verificación local de respaldo y restauración

Fecha: **10 de octubre de 2026**. Backend **0.9.0**, PostgreSQL **18.2** y JDK **25.0.4.1**. La prueba pasó sobre `grownupsvet_test`, en un esquema temporal UUID propio: **no acredita Cloud SQL, una restauración de producción ni el respaldo de toda la base**.

Revisión de fuentes: `9659dd2e348b7ff81e0c73546b5b77ee56744ad6`; este ensayo no modificó código productivo ni migraciones.

## Resultado

Se creó un propietario y una mascota ficticios por HTTP. Se cerró el contexto Spring, se respaldó todo el esquema propio con `pg_dump` en formato custom, se eliminó únicamente ese esquema y se restauró desde el archivo original con `pg_restore`. Al volver a abrir el backend, **el login y la consulta de la misma mascota devolvieron `200`**.

La ejecución cerró y reabrió **dos contextos Spring dentro de la misma JVM**; no fue un reinicio entre dos procesos Java. El proceso del harness terminó después con código **0**. No se cambió código productivo, migraciones ni variables del usuario de Windows.

| Comprobación | Evidencia |
|---|---|
| Antes del respaldo | Registro `201`, login `200`, creación de mascota `201`, `GET /api/v1/pets/{id}` `200`; OpenAPI 0.9.0 `200` |
| Después de restaurar | Login `200` con la contraseña ficticia original y rol/UUID de propietario iguales; GET de la misma mascota `200`, con nombre/especie e identidad conservados |
| Flyway | V1–V18 conservadas, con versiones/checksums/éxito iguales; el segundo arranque validó el historial sin añadir migraciones |
| Datos | Conteos iguales en las **23 tablas**, antes, después de restaurar y después de reabrir el backend: 1 usuario, 1 perfil de propietario y 1 mascota |
| Objetos | Conteos iguales: **87 relaciones, 330 restricciones, 159 triggers, 5 funciones y 46 tipos** |
| Contraseña | El hash almacenado coincidió antes/después; solo se publica el resultado booleano, no el hash ni la contraseña |
| Validación del modelo | El contexto restaurado arrancó con Hibernate `ddl-auto=validate` |
| Archivo | Respaldo custom de **111278 bytes**, limitado al esquema propio; `pg_dump` y `pg_restore` oficiales locales, ambos 18.2, terminaron sin error |

`flyway_schema_history` conserva 19 filas: las **18 migraciones versionadas** y una entrada de creación del esquema. No hay una migración V19 en esta prueba.

## Alcance y controles

- `RunId`: `c054653b-c419-4c46-84d1-db328b9de606`. Ejecución entre `2026-10-10T15:21:15Z` y `15:21:48Z` —aproximadamente 10:21 de Colombia—, con reloj real.
- La conexión se fijó a PostgreSQL local y `grownupsvet_test`. Cada eliminación verificó `current_database()` y el nombre exclusivo `backup_smoke_<UUID32>` mediante una expresión regular; nunca apuntó a `public` u otro esquema. El esquema propio estaba ausente al comenzar.
- Se cerró el primer contexto antes del dump. El listado del archivo se verificó para admitir solo objetos del esquema propio, además de metadatos del formato; se comprobó su ausencia tras eliminarlo y antes de restaurar.
- La restauración se hizo con `--exit-on-error`, `--single-transaction`, `--no-owner` y `--no-acl`. SMTP, recuperación, invitaciones y bootstrap estuvieron deshabilitados. Los JWT usaron una clave RSA efímera del harness; no se leyó la clave privada local.
- Las credenciales de PostgreSQL se recibieron del entorno existente y se pasaron a las herramientas por el entorno de sus procesos hijos, sin incluirlas en argumentos ni publicar diagnósticos del proveedor. No se publican tokens, claves o hashes de contraseña.

SHA-256 del respaldo: `931f354bdcf97a5fe1c37f56b3c7ccbb6ebd62397b6395bbe63c4685d46ac5f7`. El archivo binario, el código temporal y el log permanecen en `target/backup-smoke/`, ignorado por Git; **el respaldo binario no forma parte de esta evidencia publicable**.

## Fuentes y limpieza

La [copia saneada del reporte](evidencia-respaldo-restauracion-2026-10-10/reporte-harness.json) conserva los HTTP, UUID ficticios, conteos, integridad y resultado de limpieza. Sus fuentes locales son `target/backup-smoke/report-c054653b-c419-4c46-84d1-db328b9de606.json` y `target/backup-smoke/cleanup-verification.json`.

El reporte terminó `success=true`, `stage=RESTORATION_VERIFIED`, sin código de error. Tras cerrar el contexto restaurado se eliminó el esquema temporal propio. Una consulta independiente de solo lectura con `psql` confirmó su ausencia en `grownupsvet_test`; el proceso Java ya había terminado. No quedaron los datos ficticios de este ensayo en la base.

Esta evidencia cubre restauración local del esquema completo con sus objetos y un recorrido propietario/mascota. Quedan pendientes el respaldo/restauración del entorno que se despliegue, sus permisos, almacenamiento/retención y la recuperación operativa de citas o tareas de correo pobladas. El despliegue cloud permanece aplazado.
