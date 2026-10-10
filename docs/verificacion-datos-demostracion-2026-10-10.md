# Verificación: datos reproducibles para demostrar los procesos 1 y 2

Fecha: **10 de octubre de 2026**, `America/Bogota`. Este avance prepara la demostración del backend 0.9.0 mediante la API existente; no modifica sus reglas, endpoints ni migraciones.

## Resultado

Se añadió [create-demo-data.ps1](../scripts/create-demo-data.ps1), documentado en [Datos de demostración](datos-demostracion.md). Usa un administrador y veterinario activos existentes para crear dos propietarios y mascotas ficticios, dos turnos y dos citas. A termina cancelada después de confirmación/reprogramación y conserva su historia; B queda confirmada sobre el turno liberado para consultar la agenda.

El cliente verifica falta de autenticación (`401`), propiedad (`404`), permisos (`403`), creación/replay y conflicto de versión (`409`), antes y durante el recorrido. Utiliza UUID/versiones devueltos por el servidor; el acuerdo de reprogramación está etiquetado como ficción. Solo admite una API de loopback, sin redirecciones/proxy, y recibe tokens/contraseña como `SecureString`.

Guarda un reporte JSON completo o parcial en `target/demo-data/`, con identificadores y comprobaciones, sin cuerpos HTTP, contraseñas o tokens. No hace borrado automático. La ejecución puede crear registros y provocar avisos P12 si el backend tiene SMTP configurado; el cliente no inspecciona la bandeja ni promete captura Mailtrap.

## Evidencia ejecutada

| Comprobación | Resultado y alcance |
|---|---|
| Parser PowerShell | Sin errores de sintaxis |
| Prueba focal después del control 401 | **2 pruebas**, 0 fallos/errores/omisiones, `BUILD SUCCESS`; terminó el 10/10 a las **01:21:34**. Log local `target/demo-script-http-tests-current.log` |
| Suite completa | `mvnw.cmd verify`: **510 pruebas**, 0 fallos/errores/omisiones, `BUILD SUCCESS`; terminó el 10/10 a las **01:28:04**. Log local `target/demo-data-verify.log` |
| Contrato | OpenAPI 3.1.0, aplicación 0.9.0; **59 operaciones / 40 rutas**. **41 respuestas HTTP** validadas independientemente en `target/demo-data-openapi-validation.log` |
| Snapshot | Coincide con la salida generada: SHA-256 `AE2997F6CEDC9B5A48E290489C6E61EC1C6B0C3F2451C38DA15B3FB039B731B1`. No cambió el contrato por añadir estos helpers |
| Privacidad y roles | Token JWT real del administrador en la positiva y del veterinario en la negativa; esta última rechazó el recorrido sin crear sus datos. Salida, errores y archivo JSON sin los secretos ficticios usados |
| Datos | Servidor Spring Boot con HTTP real y PostgreSQL en esquema propio UUID; registro/login/mascotas/citas, versiones, historia, notificaciones y liberación comprobados. El esquema propio se eliminó al terminar |

La [prueba de integración](../src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/demo/DemoDataScriptHttpIntegrationTests.java) ejecuta el PowerShell real como proceso hijo. Los secretos ficticios solo se pasan por su entorno, sin valores en argv o logs. Las precondiciones administrativas del test se crean mediante repositorios; esta prueba **no acredita creación o activación de personal por sus flujos de invitación**. En entornos sin PowerShell, únicamente estas dos pruebas se omiten explícitamente; en esta ejecución ambas corrieron y no hubo omisiones.

El correo y el corte periódico se aislaron en esta prueba para evitar envíos externos. Las intenciones P12 se comprobaron como registros y respuestas HTTP, no como mensajes capturados. El resultado mantiene `MailtrapCaptureVerified=false`. No se ejecutó el helper contra `grownupsvet_dev` ni se llenó la base del usuario con estos datos.

La validación temporal del helper conserva la restricción efectiva del alta: mañana en Colombia y máximo **ahora + 60 días transcurridos**, para ambos turnos. La reprogramación general usa una fecha local hasta el día 60 inclusive; los destinos del recorrido ya están dentro de la ventana de publicación/alta. No se cambió el backend para extender una de esas ventanas.

## Pendientes de la entrega

- Ejecutar el generador en la instancia de demostración cuando disponga de configuración JWT/DB/corte y personal activo, usando credenciales locales del operador. Seguir el [diagnóstico local](ejecucion-local.md).
- Completar contenido visual, recuperación actual y recordatorio de 24 horas reales en Mailtrap. Una [ejecución SMTP separada del 10/10](verificacion-mailtrap-2026-10-10.md) registró seis envíos y seis entradas con encabezados; usó reloj privado simulado para recordatorio/corte y no cambia `MailtrapCaptureVerified=false` de este helper.
- Confirmar el frontend vigente del equipo antes de cambiar o integrar portales. Se preservó el [candidato público revisado](revision-frontend-candidato-2026-10-10.md) solo para lectura, conforme a la respuesta de Esteban.
- Revisar los contratos y recorridos con los integrantes y registrar aceptación. El despliegue Google Cloud permanece aplazado; clínica sigue fuera del alcance.

La publicación de este avance en Git/Jira registra herramientas y evidencia automatizada. La evidencia SMTP separada tiene sus propios límites; integración frontend, ejecución manual del generador, contenido de los mensajes y despliegue conservan verificaciones pendientes.
