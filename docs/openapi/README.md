# OpenAPI generado — incremento 0.3.0

`openapi.json` se genera desde los controladores, DTOs y anotaciones del backend con springdoc. La fuente editable es Java; no editar manualmente este JSON. Describe las nueve operaciones reales de registro, login, perfil propio, foto, desactivación y cierre de sesión. También publica el esquema Bearer JWT. Mascotas, recuperación, administración de personal y refresh tokens no forman parte de este incremento.

Con el backend ejecutándose, consultar `/v3/api-docs` o `/swagger-ui/index.html`. El servidor de desarrollo del snapshot es `http://localhost:8080`; configurar la URL correspondiente al generar o utilizar un cliente para otro entorno.

## Regenerar y comprobar

Desde `grownupsvet-backend`, con Java 25, `JAVA_HOME`, PostgreSQL local, la base `grownupsvet_test` y las variables `DB_USERNAME` / `DB_PASSWORD` disponibles:

```powershell
.\mvnw.cmd verify
```

Las pruebas HTTP consultan el documento real con los filtros de seguridad activos y escriben `target/generated-openapi/openapi.json`. También conservan respuestas ficticias de registro, login, perfil y errores para comprobar sus esquemas. Verifican tipos, campos obligatorios, contraseña de escritura únicamente, JWT Bearer, permisos, revocación y rechazo de propiedades adicionales.

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

El 10 de septiembre de 2026 pasaron **183 pruebas Maven**, sin fallos ni omisiones. El documento OpenAPI 3.1.0 y siete respuestas HTTP pasaron la validación independiente. Esto verifica el contrato completo del incremento de acceso, perfil y sesión correspondiente a SCRUM-67.
