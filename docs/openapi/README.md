# OpenAPI generado — incremento 0.1.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. Actualmente describe una operación real: `POST /api/v1/auth/registrations`. Login, JWT, sesión, perfil, fotos y mascotas requieren sus siguientes incrementos.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles:

```powershell
.\mvnw.cmd verify
```

`UserSignupHttpIntegrationTests` consulta el documento real con los filtros de seguridad activos y escribe `target/generated-openapi/openapi.json`. También conserva en esa carpeta tres respuestas ficticias para comprobar sus esquemas. Las pruebas verifican tipos, campos obligatorios, contraseña de escritura únicamente y rechazo de propiedades adicionales.

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

El 7 de septiembre de 2026 pasaron 156 pruebas Maven, incluidas 24 de integración HTTP de registro/seguridad/contrato con PostgreSQL. El documento y tres respuestas HTTP pasaron la validación independiente. Esto verifica este incremento; la revisión conjunta y la publicación del contrato completo de SCRUM-67 siguen pendientes.
