# Verificación de personal y disponibilidad — 23/09/2026

Alcance: backend de personal, invitaciones, perfiles y disponibilidad veterinaria, junto con el contrato generado OpenAPI 0.6.0. Esta verificación corrige los hallazgos de la revisión previa y completa sus pruebas pendientes. La base de publicación anterior era `b45931bd62968211131a5ed85c447ecf23b789ad`; el incremento incorpora personal V7–V9 y disponibilidad V10–V11.

## Correcciones y evidencia

| Hallazgo | Corrección | Comprobación |
|---|---|---|
| Decimales/texto convertidos a enteros y timestamps numéricos admitidos como fechas | Configuración Jackson estricta para enteros y deserializador de `OffsetDateTime` que exige texto ISO con offset | 16 casos unitarios y 21 casos HTTP nuevos; entradas inválidas producen 400 y conservan turnos, versiones e historial. |
| Respuestas OpenAPI de disponibilidad admitían `{}` | Siete DTOs con campos obligatorios, objetos cerrados, tipos, formatos, límites y nulabilidad explícitos | Documento real servido por Spring y validación independiente; rechazo de objetos vacíos y cada campo obligatorio omitido. |
| Anotaciones podían generar enteros como texto y perder enums | Uso de `types` para tipos explícitos e inferencia de enums, consistente con los DTOs existentes | Aserciones de tipos y enums exactos; JSON Schema rechaza cadenas/fracciones como enteros y valores fuera de los enums. |
| Faltaba evidencia de carreras reales y atomicidad | Cinco pruebas HTTP con conexiones independientes, commits y espera comprobada de bloqueos PostgreSQL | Creación duplicada: un 201 y un 409; dos ediciones de la misma versión: un éxito y un conflicto; publicación/deshabilitación en ambos órdenes; rollback de slots/eventos ante conflicto tardío del lote. |
| Faltaba cubrir PUT, republicación y extremos de ventana | Ocho casos HTTP con reloj controlado y lectura del estado confirmado | Identidad y auditoría de cambios; destino publicado/bloqueado ocupado; turno iniciado; versiones antiguas; operaciones sin cambios; bloqueo/republicación; límites exactos de 2 horas y 60 días y ambos lados del límite. |

Las clases nuevas son `StrictOffsetDateTimeDeserializerTests`, `VeterinarianAvailabilityJsonHttpIntegrationTests`, `VeterinarianAvailabilityConcurrencyIntegrationTests` y `VeterinarianAvailabilityLifecycleHttpIntegrationTests`. Las dos últimas usan esquemas de pruebas exclusivos y no tienen una transacción exterior que pueda ocultar el resultado real del servicio. Los bloqueos se observan mediante `pg_blocking_pids`, sin pausas arbitrarias. La limpieza elimina únicamente el esquema generado para esa clase.

## Resultado reproducible

Entorno: JDK 25.0.4.1, PostgreSQL 18.2 y `grownupsvet_test`. Requiere credenciales externas y permiso para crear los esquemas de pruebas. Ejecutado desde la raíz del backend:

```powershell
.\mvnw.cmd verify
& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py
```

- Maven: **396 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**. Generación del JAR completada. La revisión anterior tenía 346 pruebas; se añadieron 50 casos efectivos.
- OpenAPI: **3.1.0**, contrato **0.6.0**, **50 operaciones en 32 rutas**.
- Validador independiente: **25 respuestas HTTP reales con identidades ficticias**, más comprobaciones negativas sobre los siete esquemas de disponibilidad.
- Snapshot `docs/openapi/openapi.json` copiado de `target/generated-openapi/openapi.json` únicamente después de pasar las verificaciones; igualdad de bytes comprobada.
- SHA-256 del snapshot versionado con saltos LF: `e0496f7576326c46a576ba7701ea27b7ddf88a8e06557c57a518ddb2aed4293d`. Git normaliza los saltos CRLF del archivo generado en Windows; también se comprobó la igualdad semántica del JSON versionado.
- Log y reportes locales: `target/release-verify-2026-09-23.log` y `target/surefire-reports/`, excluidos de Git. El procedimiento y las dependencias del validador están en [la guía OpenAPI](openapi/README.md).

## Límites de cierre

La contribución backend de SCRUM-33 puede cerrarse tras publicar esta evidencia. SCRUM-36 conserva pendiente la protección contra horarios asociados a solicitudes/citas reales, que requiere el futuro módulo de citas. No se agregó una comprobación ficticia de reservas.

SCRUM-78 puede pasar a revisión con el contrato publicado, pero su cierre requiere revisión registrada por los tres integrantes y concordancia con el proceso y la matriz de permisos. SCRUM-79 sigue reservado para solicitud, confirmación y agenda. Esta verificación no acredita frontend, accesibilidad visual, entrega SMTP a destinatarios reales, despliegue ni aceptación por compañeros.
