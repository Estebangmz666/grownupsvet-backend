# Plan previo R5-A: snapshot de autenticación por solicitud

28/10/2026. Este plan precede a la implementación. La muestra de Cloud Run registra escrituras de citas y clínica por encima de 500 ms. Se conservará ese corte; reducir sentencias no acredita por sí solo la meta.

## Cambio e invariantes

1. `CurrentAccountJwtValidator` hoy carga `User` por JPA y después consulta la revocación. Introducir un repositorio JDBC de solo lectura que obtenga id, email, rol, estado, versión de autenticación y `EXISTS` de revocación por issuer+jti en una sola sentencia parametrizada. No cargar password_hash, no agregar caché, migraciones ni dependencias.
2. Representar esa proyección con un record interno tipado. Mantener validación estructural previa de subject/issuer/jti, comparación exacta de email/rol, versión numérica integral y conjunto/cantidad de permisos. Conservar `INVALID_TOKEN`, la transacción de lectura y todos los consumidores del repositorio JPA de revocación para logout y recuperación.
3. Leer un snapshot de base de datos nuevo en cada validación. Una revocación o modificación ya confirmada antes de la siguiente consulta debe rechazar el JWT anterior. Esto no sustituye los locks ni las revalidaciones de autorización dentro de las mutaciones de negocio.
4. La consulta conjunta tiene un snapshot consistente para cuenta y denylist; no se promete invalidar una petición que ya fue autorizada antes de un cambio concurrente. No cambiar aislamiento, configuración del pool, limitador de login, emisor de JWT, contratos ni configuración cloud.

## Verificación

- Añadir pruebas con PostgreSQL real para token válido, cuenta inexistente/inactiva, cambio confirmado de email/rol/versión, versión no integral, permisos faltantes/sobrantes/duplicados y revocación confirmada entre dos validaciones. Verificar issuer+jti exactos y que una validación posterior no conserva un resultado en caché.
- Reutilizar logout, recuperación y autenticación HTTP existentes para validar integración del decoder y respuesta 401. No enviar SMTP ni hacer llamadas de producción en estas pruebas.
- Revisar el SQL para comprobar una sola sentencia de lectura sin hash de contraseña. Medir la latencia desplegada mediante el colector de traces después de la regresión; no convertir el conteo estático en milisegundos.
- Coordinar un único Maven una vez congelados los cambios de citas y pruebas de MIME. Después ejecutar gates de reportes, OpenAPI, snapshot y detección de fallos; publicar únicamente con CI requerido verde.

## Propiedad y rollback

El agente principal posee exclusivamente el repositorio/proyección/validador de autenticación y su suite nueva. El agente de citas posee `AppointmentService`; el agente de correo solo modifica fixtures privados y pruebas MIME, salvo defecto real demostrado. El cambio no modifica datos ni esquema: revertir el commit restaura el camino anterior de dos consultas. No se considera finalizado el objetivo mientras falten las demás operaciones y sus verificaciones.
