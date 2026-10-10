# Diagnóstico y ejecución local del backend

El backend usa Java/JDK 25, PostgreSQL y perfil `dev` por defecto. El diagnóstico siguiente revisa **el proceso de PowerShell actual** antes de arrancar; no inicia la aplicación, cambia variables, genera claves o detiene procesos.

## 1. Revisar sin modificar configuración

Desde `grownupsvet-backend`:

```powershell
.\scripts\check-local-configuration.ps1
# Si 8080 ya está ocupado, comprobar otra opción sin detener su proceso:
.\scripts\check-local-configuration.ps1 -Port 8081
# Salida estructurada, sin valores de secretos:
.\scripts\check-local-configuration.ps1 -Port 8081 -AsJson
```

`Port` selecciona el puerto **que se comprobará**, no configura el puerto del backend. Si se elige 8081, debe usarse también en el comando de arranque posterior. La ausencia de listener es una observación del momento; otro proceso puede ocupar el puerto después.

El helper comprueba Java por PATH y, cuando está definida, la instalación de `JAVA_HOME`; busca `javac` para distinguir JDK de JRE. Captura la salida del lanzador sin imprimirla, porque `JAVA_TOOL_OPTIONS` puede contener datos privados. Muestra únicamente la versión mayor identificada y la conclusión.

El destino PostgreSQL procede del default local (`localhost:5432`), de `SPRING_DATASOURCE_URL` si contiene un destino JDBC PostgreSQL TCP, o de parámetros explícitos:

```powershell
.\scripts\check-local-configuration.ps1 -Port 8081 -DatabaseHost localhost -DatabasePort 5432
```

Si se proporcionan parámetros y existe un override, comprobar que representan el mismo destino. El helper no muestra la URL, usuario o contraseña. Un socket TCP aceptado **no demuestra** que sea PostgreSQL, que exista `grownupsvet_dev`, que las credenciales funcionen o que el usuario pueda aplicar Flyway. No se envían credenciales ni consultas SQL durante el diagnóstico.

## 2. Interpretar faltantes

| Comprobación | Acción posterior si falta |
|---|---|
| Java/JDK 25 | Seleccionar la instalación existente del JDK25 en PATH/JAVA_HOME; no continuar con otra versión |
| Puerto ocupado | Elegir otro puerto y usarlo al arrancar; no detener un proceso ajeno automáticamente |
| TCP PostgreSQL | Comprobar el servicio y destino por los medios habituales. El script no los arranca |
| `DB_USERNAME`, `DB_PASSWORD` | Suministrar configuración local existente. La comprobación de presencia no valida autenticación |
| `JWT_KEYSTORE_PATH`, `JWT_KEYSTORE_PASSWORD` | Usar el PKCS12 existente fuera del repositorio y su contraseña; no generar otra clave silenciosamente |
| Archivo PKCS12 | Verificar la ruta y permisos locales. El helper comprueba existencia, sin abrirlo o validar alias/contraseña |
| `JWT_KEY_ALIAS`, `JWT_KEY_PASSWORD` | Alias puede usar el default del proyecto; contraseña de clave puede heredar la del almacén. Se reportan como opcionales |
| `APPOINTMENT_WORKDAY_START_TIME` | En `dev` es obligatoria. Para la entrega acordada usar 06:00 en `America/Bogota`; el fallback de `prod` no se aplica al perfil local |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Configuración existente del Sandbox necesaria para demostrar P12. No se imprime ni se prueba SMTP |
| `MAIL_HOST`, `MAIL_PORT` | `dev` dispone de defaults de Mailtrap Sandbox/2525; el helper informa si no existen overrides |
| Recuperación habilitada | Exige presencia de `PASSWORD_RECOVERY_HMAC_SECRET` y `PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY`; Java valida sus formatos, tamaños e independencia |
| Invitaciones habilitadas | Exige `STAFF_INVITATION_ENCRYPTION_KEY` y `STAFF_ACTIVATION_URL` configuradas; el recorrido debe conducir al formulario real cuando esté disponible |

`ERROR` identifica un faltante o incompatibilidad para la preparación solicitada. No todos impiden arrancar: Java25, DB/JWT y jornada afectan el arranque; SMTP es necesario para ensayar entrega de P12, aunque `dev` pueda arrancar sin él y el correo no se envíe. `WARNING` señala un default, una función deshabilitada o una comprobación incompleta. Si una variable falta en el proceso pero existe para el usuario de Windows, abrir una terminal nueva; el helper no importa ni sobrescribe sus valores.

Un `PreliminaryConfigurationChecksPassed=true` **no acredita arranque ni P1/P2 completos**: puede haber funciones deshabilitadas, formatos incorrectos o problemas de autenticación/SMTP que solo el arranque y recorrido comprueban. No duplicamos aquí las validaciones Java de secretos, JWT, SMTP, estados o reglas de citas.

`scripts/configure-mailtrap.ps1` es un configurador distinto: pide credenciales, puede crear claves faltantes y modifica variables del usuario/proceso. No se invoca desde este diagnóstico. Para revisar la configuración existente no hace falta ejecutarlo ni crear nuevos secretos.

## 3. Arrancar después del diagnóstico

Solo cuando se decida iniciar la aplicación y la configuración esté completa:

```powershell
.\mvnw.cmd spring-boot:run
# Ejemplo alternativo si se eligió 8081:
.\mvnw.cmd '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run
```

Estos comandos **sí arrancan** el backend y Flyway puede aplicar migraciones pendientes sobre la base seleccionada. Confirmar el destino antes de ejecutarlos; no usar la base de pruebas para una demostración persistente. No mantener dos versiones incompatibles de workers compartiendo las mismas colas.

Una vez arrancado, revisar el log de Flyway/`Started`, `GET /actuator/health/liveness`, `GET /actuator/health/readiness` y Swagger en el puerto elegido. Readiness comprueba DB; su estado no demuestra entrega de correo. Validar registro/login/mascota y citas mediante el contrato publicado, usando datos ficticios y sin capturar JWT/contraseñas reales.

## 4. Límites de esta preparación

El helper no valida contraseña/alias del PKCS12, conexión autenticada PostgreSQL, existencia/privilegios de la base, formato de claves de recuperación/invitaciones, entrega SMTP, integración de portales o despliegue Google Cloud. Cada uno requiere su evidencia propia.

Los datos ficticios persistentes se prepararán por separado. El [guion de demostración](guion-demostracion-procesos-1-2.md) ya propone los recorridos y datos ficticios que deben sustituirse por respuestas reales del ensayo. El smoke aislado registrado acredita su ejecución, pero no llena `grownupsvet_dev` con cuentas de demostración.
