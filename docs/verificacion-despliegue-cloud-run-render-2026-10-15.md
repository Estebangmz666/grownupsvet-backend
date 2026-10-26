# Verificación del despliegue académico — 15/10/2026

Backend desplegado en Cloud Run con PostgreSQL gratuito de Render. Este registro acredita la API y las cuentas de demostración; no acredita un frontend desplegado, una prueba de carga en nube ni la ejecución completa de la suite RNF de TestRail.

## Acceso y alcance

- API: <https://grownupsvet-backend-w5glrcsyda-uc.a.run.app>.
- [Swagger UI](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html).
- Contrato público `/v3/api-docs`: HTTP 200, OpenAPI 3.1.0, versión funcional 0.9.0, 40 rutas y servidor HTTPS real.
- Durante la verificación inicial se observaron respuestas 500 transitorias de la documentación. Las consultas posteriores devolvieron 200 y Swagger cargó en el navegador. No se atribuye una causa sin evidencia adicional.
- Disponibles los módulos de usuarios, mascotas, personal, disponibilidad y gestión administrativa de citas del incremento actual. Atención clínica permanece fuera del incremento.

## Infraestructura comprobada

| Elemento | Resultado |
|---|---|
| Proyecto | `project-4f1c67ce-5621-40e0-96c` |
| Región API/registro/construcción | `us-central1` |
| Servicio | `grownupsvet-backend` |
| Revisión final inicial | `grownupsvet-backend-00002-jdz`, lista y con 100 % del tráfico |
| Configuración | 1 vCPU, 2 GiB, concurrencia 8; mínimo 1 y máximo 1 a nivel de servicio; CPU fuera de peticiones habilitada |
| Perfiles | `prod,external-postgres` |
| Runtime | Java 25.0.4.1, usuario del contenedor 10001 |
| Base | Render Free, PostgreSQL 18.6, Ohio, `grownupsvet`, creada exclusivamente para este entorno |
| Vencimiento DB | **14/11/2026**, mostrado por Render |
| Esquema | Flyway valida 18 migraciones; versión 18 y validación de Hibernate al arrancar |
| TLS | pgJDBC exige `verify-full` con la confianza de Java; conexión de comprobación TLS 1.3 con hostname verificado; la imagen productiva arrancó con esa configuración |
| Salud pública | Liveness y readiness HTTP 200, cuerpo limitado a `status: UP` |
| Acceso protegido sin JWT | `GET /api/v1/pets` devuelve 401 |

El máximo de una instancia es de servicio. La anotación de máximo de revisión conserva su valor predeterminado; no debe confundirse con el límite de servicio comprobado. Reinicios y transiciones entre revisiones son posibles.

## Construcción y trazabilidad

- Base de código local: `28b2267`, con adaptaciones locales para PostgreSQL externo, script de despliegue y validación. No se afirma que esas adaptaciones se hayan publicado en Git.
- Cloud Build: `8f89df93-822c-439d-ad97-6f85f9ce720d`, resultado **SUCCESS**.
- Imagen: `us-central1-docker.pkg.dev/project-4f1c67ce-5621-40e0-96c/grownupsvet/backend`.
- Digest desplegado: `sha256:0439145fe801e2c115fd335dff8932ed854aa26bcd7cb21c3d5ef9407ff24408`.
- Contexto: 235 archivos, 745,6 KiB antes de comprimir; manifiesto SHA-256 `512fddd810838ef33608647ea73ad05afbd35481ff8f743a71eda3a4e7262f4a`.
- `.gcloudignore` usa una allowlist de fuentes principales y archivos necesarios para Docker/Maven; no se subieron tests, docs, scripts, target ni llaves.
- Las credenciales no entraron en la imagen. La cuenta de construcción solo recibe escritura sobre el repositorio Docker, lectura del bucket privado de fuentes y escritura de logs. No tiene acceso Secret Manager.
- Prueba local de configuración: **15 aprobadas**, sin fallos, errores ni omisiones. Construcción local del paquete y construcción Linux de Cloud Build correctas. No se volvió a ejecutar toda la suite histórica para este despliegue.

## Cuentas ficticias para evaluación

Se prepararon exactamente **7 cuentas activas**: 1 SUPER_ADMIN, 2 ADMINISTRATOR, 2 VETERINARIAN y 2 OWNER. Tienen contraseñas aleatorias diferentes y correos reservados de ejemplo. Los accesos se entregan en un archivo local privado fuera de Git; no se publican contraseñas en este informe, logs ni capturas.

El SUPER_ADMIN se creó mediante el bootstrap existente. Los propietarios se registraron mediante la API. Los cuatro usuarios de personal se cargaron mediante un utilitario académico externo a la aplicación, con transacción, TLS verificado, el encoder Argon2id del backend y perfiles/auditoría/títulos base coherentes. No se añadieron endpoints, migraciones ni un seed al arranque. No se simularon invitaciones consumidas y no se adjuntaron diplomas.

Comprobación final:

| Comprobación | Resultado |
|---|---|
| Inicio de sesión y rol esperado | **7/7** |
| Consulta permitida para el rol | **7/7**, HTTP 200 |
| Consulta prohibida para otro rol | **7/7**, HTTP 403 |
| Cierre de las sesiones de comprobación | **7/7**, HTTP 204 |

Se retiraron los dos bindings de bootstrap del servicio, se deshabilitó el bootstrap, se revocó al runtime el acceso a esos dos secretos y se deshabilitaron sus versiones iniciales. Las claves JWT/HMAC/cifrado del entorno son nuevas, se conservan entre revisiones y permanecen en Secret Manager y respaldo local protegido.

## Presupuesto, correo y pendientes

El usuario confirmó en la consola, antes de crear recursos, **prueba gratuita con US$300 y 90 días**. La cuenta no se actualizó a modalidad de pago. No se presenta ese saldo inicial como saldo actual después del despliegue. La estimación y limitaciones de costo están en [la guía del entorno](google-cloud-render.md); el parámetro mensual de US$75 es una asignación de planificación dentro del límite total de US$300, no un corte técnico ni autorización de gasto recurrente fuera de los créditos.

El correo utiliza Mailtrap Sandbox. La solicitud de recuperación de una cuenta ficticia devolvió HTTP 202. La tarea nueva `RECOVERY_CODE`, creada el 15/10/2026 a las 15:44:51,889 de Bogotá, quedó `SENT` a las 15:44:54,343, con **1 intento**. La consulta se limitó a metadatos de la cola, sin leer códigos ni payloads cifrados. Acredita aceptación SMTP por el trabajador; no acredita inspección del buzón ni entrega a correos reales. `database-and-smtp-verification.json` también confirmó siete cuentas activas y Flyway 18 en una transacción de solo lectura.

Los frontends y sus URLs de activación no estaban confirmados durante este despliegue. CORS para portales externos permanece sin orígenes; Swagger conserva el origen de la propia API. Invitaciones de personal deshabilitadas implica que **crear nuevo personal o reenviar invitaciones no está disponible** hasta integrar el formulario real. Las siete cuentas precargadas sí permiten evaluar sus roles. No confundir esta limitación con una prueba aprobada de invitaciones.

Render Free no incluye backups y la base deja de estar accesible al vencer. Antes del 14/11/2026 se debe exportar y comprobar la restauración si se necesita conservar datos. Recrear una base no los conserva. No se configuraron respaldos ni recordatorios automáticos.

Evidencia local, sin contraseñas ni JWT: carpeta de trabajo `output/deployment-2026-10-15/`, con manifiesto de Cloud Build, checks HTTP, verificación TLS, metadatos de OpenAPI y resultados de las siete cuentas. La copia privada temporal `render-connection.pending.json` quedó pendiente de limpieza manual: la revisión automática bloqueó su eliminación incluso después de la autorización expresa del usuario. Está exclusivamente en la carpeta privada local, fuera de Git y del contexto de construcción. La copia DPAPI y los secretos necesarios de Secret Manager se conservan.
