# OpenAPI generado — incremento 0.2.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. Describe dos operaciones reales: registro en `POST /api/v1/auth/registrations` y login en `POST /api/v1/auth/sessions`. También publica el esquema Bearer JWT; perfil, fotos, mascotas, refresh y revocación no forman parte de este incremento.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles:

```powershell
.\mvnw.cmd verify
```

Las pruebas HTTP consultan el documento real con los filtros de seguridad activos y escriben `target/generated-openapi/openapi.json`. También conservan respuestas ficticias de registro, login y errores para comprobar sus esquemas. Verifican tipos, campos obligatorios, contraseña de escritura únicamente, JWT Bearer y rechazo de propiedades adicionales.

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

El 9 de septiembre de 2026 pasaron **166 pruebas Maven**, sin fallos ni omisiones. El documento OpenAPI 3.1.0 y cinco respuestas HTTP pasaron la validación independiente. Esto verifica registro/login y su contrato; la revisión compartida y las operaciones posteriores de SCRUM-67 continúan pendientes.
