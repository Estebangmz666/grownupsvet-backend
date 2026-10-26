# Despliegue académico: Cloud Run y PostgreSQL gratuito de Render

Decisión del 15/10/2026: mantener la API aproximadamente 30 días, con PostgreSQL gratuito de Render y el backend en Google Cloud. El límite autorizado es **US$300 de créditos gratuitos en total, sin presupuesto adicional**. El despliegue inicial del backend se verificó el 15/10/2026; consultar el [registro de resultados](verificacion-despliegue-cloud-run-render-2026-10-15.md). El **16/10/2026** se verificó la transición a Gmail con recepción real de un correo de recuperación; consultar la [verificación de Gmail](verificacion-gmail-2026-10-16.md). Los frontends requieren integración posterior.

API: <https://grownupsvet-backend-w5glrcsyda-uc.a.run.app>. [Swagger UI](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html). La base gratuita vence el **14/11/2026**.

## Cuenta y costo

Antes de crear recursos facturables, comprobar en Facturación que la cuenta continúa en **Free Trial**, el saldo real y la fecha de vencimiento. No convertirla a cuenta de pago. La oferta general es de US$300 durante 90 días; no prueba el estado de una cuenta particular. En Free Trial sin actualización manual, al vencer los créditos o el plazo se detienen los recursos. Una cuenta actualizada a pago puede seguir cobrando después de los créditos.

La API necesita CPU fuera de las solicitudes para procesar correos, recordatorios y tareas programadas. Se conserva Cloud Run con 1 vCPU, 2 GiB, mínimo 1, máximo 1 y CPU siempre asignada. Poco tráfico no elimina ese costo. Con precios publicados de `us-central1`, la estimación de cómputo para 30 días continuos, antes de la cuota gratuita, es:

```text
segundos = 30 × 24 × 60 × 60 = 2 592 000
CPU = 2 592 000 × US$0,000018 = US$46,656
RAM = 2 592 000 × 2 × US$0,000002 = US$10,368
cómputo estimado = US$57,024
```

Construcciones, Artifact Registry, secretos, logs, almacenamiento y tráfico entre proveedores se contabilizan aparte. El máximo de una instancia limita escalamiento; no constituye un límite financiero. El parámetro `ApprovedMonthlyBudgetUsd` del script tampoco impone un corte. Las alertas de presupuesto no detienen servicios. Los spend caps de Google para servicios compatibles son mensuales, específicos de proyecto/servicio y pueden aplicarse con retraso; no equivalen a un límite global exacto de US$300. Revisar consumo antes y después del despliegue.

Fuentes consultadas el 15/10/2026: [Free Trial](https://cloud.google.com/signup-faqs), [precios Cloud Run](https://cloud.google.com/run/pricing), [CPU y facturación](https://docs.cloud.google.com/run/docs/configuring/billing-settings), [presupuestos con spend caps](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps).

## Base de datos

Crear una base **nueva y exclusiva** de GrownupsVet en Render, con el plan Free de US$0, 1 GB y PostgreSQL 18, consistente con la versión local instalada. No reutilizar ni modificar la base de otro proyecto. Comprobar el total de US$0 antes de crearla. Render permite una base gratuita activa por workspace; si no hay cupo, resolverlo antes de continuar, sin contratar un plan ni eliminar otra base.

Cloud Run utilizará el **hostname externo** de Render, puerto y base reales. El hostname interno de Render solo funciona en su propia red. Conservar usuario y contraseña separados de la URL JDBC. Guardar la contraseña como versión de Secret Manager y otorgar acceso únicamente a la cuenta de servicio del backend. No hacen falta una instancia Cloud SQL ni el rol `roles/cloudsql.client` para esta alternativa.

Activar `prod,external-postgres`. El perfil `prod` por sí solo conserva Cloud SQL para quienes usen la guía original. El perfil externo exige TLS con `sslmode=verify-full` y `org.postgresql.ssl.DefaultJavaSSLFactory`, valida hostname y usa la confianza de Java. Probar la conexión con el JRE de la imagen real. Si falta una CA, incorporar la CA verificada al truststore; no desactivar la comprobación del certificado ni del hostname.

Fuentes: [limitaciones del plan gratuito](https://render.com/docs/free), [conexiones a PostgreSQL](https://render.com/docs/postgresql-creating-connecting), [TLS en pgJDBC](https://jdbc.postgresql.org/documentation/ssl/).

## Configuración del despliegue

Usar `scripts/deploy-google-cloud.ps1` con `DatabaseProvider = 'ExternalPostgres'`, `ExternalDatabaseHost`, `ExternalDatabasePort`, `DatabaseName` y `DatabaseUsername` reales. Omitir `CloudSqlInstanceConnectionName`. El script construye `jdbc:postgresql://HOST:PUERTO/BASE` sin credenciales ni parámetros y activa ambos perfiles. Mantener los demás parámetros y secretos descritos en [la guía de Google Cloud](google-cloud.md).

```powershell
# deploymentParameters se prepara localmente con identificadores reales
# y referencias nombre-secreto:version; nunca con valores de secretos.
.\scripts\deploy-google-cloud.ps1 @deploymentParameters -ValidateOnly
# Ejecutar después de comprobar cuenta, créditos, imagen y secretos:
.\scripts\deploy-google-cloud.ps1 @deploymentParameters -Execute
```

Mantener JWT RS256, claves HMAC y claves de cifrado estables entre revisiones y restauraciones. Crear credenciales exclusivas de este entorno fuera del repositorio. El correo del despliegue inicial del 15/10/2026 usaba **Mailtrap Sandbox**; aquella evidencia acredita captura/aceptación SMTP. Desde el 16/10/2026, la revisión `grownupsvet-backend-00004-mzr` utiliza **Gmail SMTP** y tiene recepción real comprobada. El bootstrap de SUPER_ADMIN requiere una identidad acordada y debe deshabilitarse tras crear la cuenta inicial.

### Opción Gmail SMTP desde el 16/10/2026

Para enviar a destinatarios reales, preparar `MailProvider = 'Gmail'` en la misma hashtable del despliegue. El script conserva `DatabaseProvider = 'ExternalPostgres'` y selecciona `smtp.gmail.com:587` con autenticación y STARTTLS obligatorio. No se requiere un dominio propio: `MailFrom` debe ser la misma dirección Gmail completa usada para autenticar.

Guardar la dirección Gmail y su **contraseña de aplicación** como secretos de Secret Manager, con acceso para la cuenta de servicio del backend. `SecretBindings['MAIL_USERNAME']` y `SecretBindings['MAIL_PASSWORD']` deben contener exclusivamente referencias `nombre-secreto:version-numérica` a esas nuevas versiones, nunca los valores ni `latest`. La contraseña normal de Google no sustituye la contraseña de aplicación. Conservar los secretos anteriores de Sandbox y las claves JWT/HMAC/cifrado.

Usar una imagen nueva que incluya la compatibilidad Gmail del validador, validar con `-ValidateOnly` y desplegar la revisión autorizada. La validación offline no lee los secretos ni prueba autenticación/entrega SMTP. Después, comprobar salud, un correo de prueba acordado y su recepción en el buzón real, incluido spam; registrar revisión, imagen, proveedor y fecha sin códigos, tokens ni secretos.

**Verificado el 16/10/2026:** revisión `grownupsvet-backend-00004-mzr` lista con el 100 % del tráfico; versiones `2` de los secretos SMTP existentes, sin IAM nuevo, demás variables preservadas y recursos sin cambios (1 vCPU, 2 GiB, mínimo 1, máximo 1, concurrencia 8). Readiness/liveness `200 UP`, OpenAPI `200`/`0.9.0`/41 rutas y recurso de mascotas sin JWT `401`. El registro de un propietario de prueba devolvió `201`; recuperación devolvió `202`, su tarea quedó `SENT` al primer intento y el usuario confirmó llegada a Recibidos. El [registro canónico](verificacion-gmail-2026-10-16.md) conserva build, digest, metadatos y límites.

Para volver a las pruebas capturadas, seleccionar `MailProvider = 'Mailtrap'` (valor predeterminado), usar sus propias referencias de credenciales y un remitente válido: el endpoint seguirá siendo `sandbox.smtp.mailtrap.io:2525`. No mezclar secretos Gmail y Sandbox. Consultar el [procedimiento completo de Gmail](google-cloud.md#gmail-para-destinatarios-reales), [SMTP de Google](https://developers.google.com/workspace/gmail/imap/imap-smtp) y [contraseñas de aplicación](https://support.google.com/accounts/answer/185833?hl=es).

Las URLs reales de los frontends determinan CORS y el enlace de activación. Una primera revisión con frontend vacío e invitaciones deshabilitadas puede verificar la API, pero no acredita integración completa con los portales. No inventar rutas frontend ni abrir CORS con comodines.

## Vencimiento y recuperación

La base gratuita expira **30 días después de su creación** y queda inaccesible al expirar. La ventana posterior de 14 días permite actualizar el plan; no son días adicionales de servicio gratuito. Render no proporciona backups para este plan. Crear otra base no recupera los datos de la anterior.

Registrar la fecha exacta de creación y vencimiento al provisionar. Antes del vencimiento, exportar un backup de PostgreSQL a un archivo privado y comprobar su restauración en un destino aislado compatible. El backup contiene datos y requiere almacenamiento protegido fuera de Git. Al recrear, restaurar mientras el origen siga accesible, actualizar hostname/usuario/contraseña y verificar migraciones, conteos y acceso de cuentas. No borrar la fuente antes de verificar la copia.

Si el plan Free impide tener dos bases activas en el mismo workspace, preparar y verificar el respaldo antes de que expire la primera y acordar el destino de restauración. No actualizar a pago ni borrar una base automáticamente. No se ha configurado una tarea periódica de respaldo ni un recordatorio.

## Evidencia histórica de preparación del 15/10/2026

- `ProductionConfigurationValidatorTests`: 15 pruebas aprobadas el 15/10/2026; incluyen carga de YAML con ambos órdenes de perfiles, TLS obligatorio y rechazo de parámetros/connector incompatibles.
- Script PowerShell: parser sin errores, validación offline de Cloud SQL y PostgreSQL externo; rechazo de localhost, credenciales en hostname, puerto inválido y mezcla de proveedores.
- Paquete local Java 25 construido correctamente; esta construcción omite tests y no sustituye las 15 pruebas anteriores ni una prueba del contenedor Linux.
- `.gcloudignore` contiene una allowlist del contexto de construcción. `gcloud meta list-files-for-upload` verificó 235 archivos, sin rutas fuera de esa lista; incluye Dockerfile, wrapper, Maven y fuentes principales, y excluye tests, docs, scripts, target y llaves. No se ha subido el código.
- Estas comprobaciones no prueban conexión TLS real, funcionamiento en Cloud Run, saldo de créditos, envío SMTP ni disponibilidad pública.

Antes de declarar entregado: construir la imagen sin secretos, verificar DB/Flyway con TLS desde el contenedor, comprobar probes públicos sin detalles, rechazo de recursos protegidos sin JWT, operación del correo y tareas de fondo, y registrar URL/revisión/imagen/fecha junto con la evidencia. La lista completa de verificación permanece en la guía original.
