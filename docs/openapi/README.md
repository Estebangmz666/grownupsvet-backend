# OpenAPI generado — incremento 0.4.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. Describe dieciséis operaciones: las nueve de acceso/perfil/sesión, cuatro de mascotas propias y tres de recuperación. Publica Bearer JWT para operaciones protegidas y un permiso opaco restringido para restablecer contraseñas. Administración de personal y refresh tokens no forman parte de este incremento.

La [guía del incremento](../mascotas-y-recuperacion.md) concreta propiedad, datos, archivo, cuotas y configuración SMTP. La recuperación está deshabilitada por defecto hasta configurar el canal. El Sandbox de Mailtrap se configuró en desarrollo y se reportó un restablecimiento manual satisfactorio; el alcance de esa evidencia se detalla al final. El cierre de SCRUM-76 exige publicar este contrato junto al código y registrar la revisión con los dos frontends, que sigue pendiente.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles:

```powershell
.\mvnw.cmd verify
```

Las pruebas HTTP consultan el documento real con los filtros de seguridad activos y escriben `target/generated-openapi/openapi.json`. Conservan trece respuestas de registro, login, perfil, mascotas, recuperación y errores, obtenidas de cuentas ficticias. El permiso opaco en el ejemplo de verificación se sustituye por un valor ficticio de la misma forma. Verifican tipos, nulabilidad, campos obligatorios, credenciales de escritura únicamente, permisos, propiedad, revocación, uso único y rechazo de propiedades adicionales. Los controladores exclusivos de prueba se excluyen del documento.

La comprobación independiente del documento OpenAPI 3.1 y los ejemplos necesita Python y estas dependencias de desarrollo:

```powershell
python -m venv target/openapi-validation-env
& target/openapi-validation-env/Scripts/python.exe -m pip install -r scripts/requirements-openapi.txt
& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py
```

Después de que ambos comandos de verificación terminen correctamente, actualizar el snapshot:

```powershell
Copy-Item -LiteralPath target/generated-openapi/openapi.json -Destination docs/openapi/openapi.json
```

El incremento anterior 0.3.0 se verificó el 10 de septiembre de 2026 con 183 pruebas Maven y siete respuestas HTTP. El incremento actual añade mascotas y recuperación dentro del alcance técnico de SCRUM-27/SCRUM-30 y aporta el contrato de SCRUM-76; su cierre requiere además canal y revisión compartida.

## Verificación del incremento 0.4.0

El 10 de septiembre de 2026, `mvnw.cmd verify` completó **264 pruebas**, sin fallos, errores ni omisiones. Incluye 53 casos HTTP de mascotas, 17 de recuperación, la recuperación deshabilitada, configuración/secretos y cinco casos de correo SMTP real exclusivamente en loopback. Las pruebas ejercitan PostgreSQL local, aislamiento entre propietarios, PATCH parcial/concurrente, cuotas, vencimiento, uso único y rechazo de JWT anteriores al restablecimiento.

El OpenAPI 3.1.0 generado, versión 0.4.0, contiene **16 operaciones** y pasó `openapi-spec-validator`; **13 respuestas HTTP** pasaron la comprobación independiente de JSON Schema. Se comprobó además que no contiene rutas exclusivas de pruebas y que sus booleanos, enteros y enums anulables coinciden con el contrato. El snapshot se copió al repositorio solo después de estas verificaciones.

Las pruebas automatizadas no incluyen Mailtrap remoto, pantallas frontend, despliegue ni revisión de los otros integrantes. Las migraciones V5/V6 se verificaron automáticamente en `grownupsvet_test`.

Posteriormente, el 10 de septiembre de 2026, se comprobó conexión STARTTLS y autenticación SMTP con el Sandbox de Mailtrap. El desarrollador reportó el arranque local y Swagger funcionando, un `204` de `POST /api/v1/auth/password-resets` (captura compartida) y `authentication_version = 1` mediante consulta SQL. Esto documenta una comprobación manual en desarrollo; no sustituye la revisión del contrato por los tres integrantes. Aún no se ha reportado el resultado manual de iniciar sesión con la contraseña nueva y rechazar la anterior. No se incorporan credenciales, códigos ni tokens reales a esta evidencia.
