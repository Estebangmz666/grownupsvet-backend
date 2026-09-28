# Personal, invitaciones y disponibilidad — contrato 0.7.0

Alcance: cuentas de administradores y veterinarios, invitación para establecer contraseña, perfil profesional y títulos con diploma opcional, más disponibilidad veterinaria de turnos concretos de 30 minutos. El contrato se genera desde controladores y DTOs. Solicitudes/reservas/citas, integración COMVEZCOL y plantillas Thymeleaf quedan aplazadas.

## Identidad, permisos y estados

Una cuenta conserva el mismo UUID desde la invitación. `users` centraliza credenciales y un solo rol; `administrator_profiles` y `veterinarian_profiles` guardan los datos propios del personal. No hay herencia entre usuarios ni copias temporales que se deban trasladar al activar.

| Rol | Operaciones de este incremento |
|---|---|
| `SUPER_ADMIN` | Crear, consultar, modificar, habilitar/deshabilitar e invitar administradores. Existe como máximo una cuenta por instalación. |
| `ADMINISTRATOR` | Gestionar e invitar veterinarios, sus títulos y diplomas. Cualquier administrador activo puede gestionar a un veterinario creado por otro administrador. |
| `VETERINARIAN` | Iniciar/cerrar sesión, recuperar contraseña, gestionar su propia foto y consultar únicamente su disponibilidad. No puede modificar turnos. |
| `OWNER` | Consultar el directorio de veterinarios activos, su teléfono profesional, foto y diplomas publicados, además de turnos solicitables. Mantiene sus operaciones de perfil, mascotas y recuperación. |

Los permisos son independientes: el superadministrador no hereda funciones clínicas ni la gestión de veterinarios. El servidor asigna el rol según la operación; los DTOs de creación no admiten `role`, `status`, contraseña ni identificadores de otra identidad. El correo y el rol no se editan mediante el contrato de personal.

| Estado persistido | Credencial y comportamiento |
|---|---|
| `PENDING_ACTIVATION` | Sin hash; no permite login, JWT ni recuperación. Solo el enlace de invitación válido permite establecer contraseña y activar. |
| `ACTIVE` | Requiere hash. Permite las acciones vigentes de su rol. |
| `DISABLED` | Login y JWT rechazados. Si nunca estableció contraseña, puede recibir una nueva invitación del actor autorizado. Si ya tenía credenciales, la habilitación conserva su contraseña. |

Deshabilitar incrementa `authentication_version`, de modo que volver a habilitar no revive sesiones antiguas. El campo `active` del perfil de propietario sigue siendo un booleano derivado; en la tabla `users` se reemplaza por `status`. El superadministrador no puede deshabilitarse mediante estas operaciones.

La disponibilidad usa permisos separados `VETERINARIAN_AVAILABILITY_MANAGE`, `VETERINARIAN_AVAILABILITY_READ_SELF` y `VETERINARIAN_AVAILABILITY_READ_AVAILABLE` para administrador, veterinario y propietario, respectivamente. El superadministrador no hereda ninguno. Los JWT de las cuentas existentes dejan de coincidir con la lista vigente tras añadir estos permisos; el usuario debe iniciar sesión de nuevo. Al deshabilitar un veterinario, los turnos publicados dejan de aparecer a propietarios y se muestran otra vez automáticamente al rehabilitarlo, si siguen publicados y dentro de la ventana.

## Primer superadministrador

El bootstrap está deshabilitado por defecto. Se habilita explícitamente con `SUPER_ADMIN_BOOTSTRAP_ENABLED=true`, `SUPER_ADMIN_EMAIL` y `SUPER_ADMIN_PASSWORD`, suministrados fuera del repositorio. La contraseña sigue la misma política del registro. No hay credenciales predeterminadas ni endpoint público para crear superadministradores.

El arranque usa un bloqueo transaccional de PostgreSQL y un índice único parcial. Si la cuenta ya existe con el mismo correo, no vuelve a crearla ni sobrescribe su contraseña. Un correo ocupado por otro rol o una identidad distinta a la previamente aprovisionada impiden ese bootstrap. Después de aprovisionarla, se recomienda deshabilitar el bootstrap y retirar su contraseña inicial del entorno. El restablecimiento posterior utiliza el flujo de recuperación normal.

La cuenta compartida inicial responde a la decisión académica del equipo; sus acciones no permiten distinguir qué compañero la utilizó. Las cuentas administrativas invitadas son individuales.

## Creación, activación y correo

1. El actor autorizado crea el perfil completo. La misma transacción persiste la cuenta pendiente, su perfil, una invitación y la tarea de correo.
2. `StaffInvitationCreatedEvent` es un `record` independiente que lleva únicamente el ID de la invitación. El procesamiento posterior al commit despierta el envío; un proceso periódico recupera tareas pendientes incluso después de reiniciar la aplicación.
3. El correo contiene un enlace para establecer y confirmar contraseña. Abrir el enlace no activa ni consume la invitación. La pantalla frontend debe enviar `POST /api/v1/auth/account-activations` con `token`, `password` y `confirmPassword`.
4. Un token válido se consume y la cuenta se activa en una transacción. La respuesta es `204`; no entrega JWT. La persona inicia sesión mediante el login existente.

El token contiene 32 bytes aleatorios, codificados en 43 caracteres Base64 URL sin relleno; vence a las 48 horas y tiene un único uso. Se guarda su SHA-256 para validación. Solo mientras el correo está pendiente se conserva además una copia cifrada con AES-GCM, asociada al ID de invitación, para permitir reintentos sin guardar el enlace en texto claro. Esa copia se elimina al finalizar el envío, cancelar, consumir o terminar los intentos. No se registra el token, la contraseña ni el contenido SMTP en diagnósticos.

Reenviar invalida el enlace anterior y genera uno nuevo. Se permite como máximo una invitación por minuto y cinco por cuenta en una ventana móvil de 24 horas, incluida la inicial. Cancelar una invitación deshabilita la cuenta pendiente. Una cuenta que ya estableció contraseña utiliza recuperación; no se le reemplaza la credencial mediante invitaciones.

El envío tiene hasta cinco intentos por defecto y timeouts SMTP de cinco segundos. La persistencia permite reintentar una falla temporal; no garantiza entrega al buzón ni envío exactamente una vez: si el proceso cae después de que SMTP acepte el mensaje, puede llegar otra copia del mismo enlace. La activación conserva el uso único.

| Variable | Uso |
|---|---|
| `STAFF_INVITATIONS_ENABLED` | `false` por defecto. Sin habilitarlo, las altas y reenvíos que requieren invitación devuelven `503` sin dejar perfiles parciales. |
| `STAFF_INVITATION_ENCRYPTION_KEY` | Clave aleatoria de 32 bytes codificada en Base64, distinta del secreto de recuperación y de la contraseña JWT. Debe mantenerse estable entre reinicios y compartirse entre instancias que procesan las mismas tareas. |
| `STAFF_ACTIVATION_URL` | URL base del formulario frontend. Predeterminado: `https://portal.example.invalid/activate-account`. Incluye `TO-DO: replace with front portal production URL` en código y configuración. |
| `MAIL_FROM` | Dirección remitente. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Canal SMTP del perfil de desarrollo existente. Otros perfiles deben configurar las propiedades `spring.mail` correspondientes. |

La URL admite HTTPS; HTTP se limita a `localhost` y `127.0.0.1` para desarrollo. No acepta credenciales, query ni fragmento en su configuración. El enlace de ejemplo es deliberadamente un placeholder y no sirve como portal. Antes de usarlo con destinatarios reales, reemplazarlo y disponer del formulario de activación. El frontend debe evitar registrar o reenviar el token a terceros y enviarlo únicamente al endpoint de activación.

Las plantillas visuales siguen aplazadas hasta contar con estructura y colores del frontend. El incremento envía un mensaje de texto plano. Rotar la clave de cifrado sin terminar las tareas pendientes impide descifrarlas; en ese caso se debe emitir una nueva invitación con la nueva configuración.

## Perfil profesional y diplomas

Administrador: nombre completo y correo obligatorios; foto propia opcional. Veterinario: nombre completo, correo privado, teléfono profesional E.164 público, matrícula profesional única, nombre e institución del título base. Biografía, foto, año de graduación y títulos adicionales son opcionales. No se solicita fecha de nacimiento. El nombre completo no obliga a todas las personas a tener exactamente dos apellidos.

El título base se almacena como un título estructurado de pregrado, del que se derivan los campos del perfil; no se duplican sus datos en otra tabla. No se puede eliminar el título base. Se permiten hasta 20 títulos por veterinario, contando el base. Cada título tiene tipo, nombre, institución y año opcional entre 1900 y el año actual.

Cada título puede tener un PDF de hasta 5 MiB en PostgreSQL, separado de los DTOs. Un administrador carga el archivo, controla su publicación y lo elimina. El archivo se revisa por estructura real con PDFBox: de 1 a 50 páginas, sin cifrado, formularios, acciones, JavaScript ni adjuntos, con límites de objetos, complejidad, descompresión y concurrencia. Es validación estructural, no certificación del diploma ni análisis antivirus. La matrícula capturada tampoco implica una consulta o validación de COMVEZCOL.

PDFBox puede descomprimir antes de devolver el contenido al validador. Por eso todo su análisis se ejecuta en un proceso Java separado: máximo dos procesos simultáneos por instancia, heap de 128 MiB, memoria directa de 32 MiB, metaspace de 64 MiB y plazo de 15 segundos. El límite de 30 MiB de contenido descomprimido es una regla de aceptación posterior, no el techo de memoria del parser. Un documento que excede los recursos se rechaza sin agotar el heap del servidor web. El proceso no recibe las credenciales de base de datos, correo o JWT; sus salidas se descartan y el directorio temporal privado se elimina al finalizar.

El despliegue necesita permitir iniciar el ejecutable Java de la misma instalación y escribir en su directorio temporal. Se admiten tanto ejecución desde Maven/IDE como el JAR ejecutable de Spring Boot; el worker no arranca Spring ni se conecta a la base de datos. Una imposibilidad de iniciar el validador devuelve `503`. Estos límites de JVM no equivalen a un contenedor con una cuota total de memoria del sistema operativo.

El directorio requiere una sesión `OWNER`, solo devuelve veterinarios activos y excluye el correo y los datos de auditoría administrativa. Los diplomas no publicados no son descargables desde el directorio. La descarga publicada vuelve a comprobar la cuenta y el título, usa `attachment`, `nosniff`, política restrictiva de contenido y caché privada sin almacenamiento. Cambiar datos académicos retira la publicación del diploma para que se revise otra vez. Reemplazar un PDF inválido no destruye el documento previo.

## Disponibilidad veterinaria

Los turnos publicados duran 30 minutos, comienzan en `:00` o `:30` de `America/Bogota` y se guardan para fechas concretas. Los administradores crean, editan, bloquean, republican y consultan el historial; el veterinario solo lee su agenda. El propietario solo ve turnos publicados, libres y de cuentas activas, desde mañana hasta el horizonte máximo de 60 días. Bloquear conserva la identidad y el historial del turno. Los detalles completos de generación, ventana, ocupación y privacidad están en [Disponibilidad veterinaria](disponibilidad-veterinaria.md).

## Rutas del contrato

Todas parten de `/api/v1`; las operaciones de recursos exigen Bearer y el actor definido arriba. Los listados admiten `page` desde cero y `size` de 1 a 100. Los listados administrativos pueden filtrar por `status`.

| Recurso | Métodos |
|---|---|
| `/administrators`, `/veterinarians` | `POST` crea perfil pendiente (`201` y `Location`); `GET` lista. |
| `/administrators/{id}`, `/veterinarians/{id}` | `GET` consulta; `PUT` reemplaza todos los datos editables. |
| `/administrators/{id}/status`, `/veterinarians/{id}/status` | `PATCH` recibe `{"status":"ACTIVE"}` o `{"status":"DISABLED"}`. No omite la activación por contraseña. |
| `/staff/{userId}/invitations` | `POST` reenvía (`202`); `DELETE` cancela (`204`). |
| `/auth/account-activations` | `POST` público con token, contraseña y confirmación; consume la invitación (`204`). |
| `/veterinarians/{veterinarianId}/qualifications` | `POST` añade título; `GET` lista. |
| `/veterinarians/{veterinarianId}/qualifications/{qualificationId}` | `GET`, `PUT` y `DELETE` del título. |
| `/veterinarians/{veterinarianId}/qualifications/{qualificationId}/diploma` | `PUT` multipart con `file` y `published` opcional (por defecto `false`); `GET` descarga administrativa; `DELETE` elimina. |
| `/veterinarians/{veterinarianId}/qualifications/{qualificationId}/diploma/publication` | `PATCH` con `{"published":true}` o `false`; cambia la publicación sin volver a cargar el PDF. |
| `/veterinarians/{veterinarianId}/availability-slots` | `POST` publica un turno; `GET` lista turnos de agenda según rol. |
| `/veterinarians/{veterinarianId}/availability-slot-batches` | `POST` genera un lote acotado de fechas concretas. |
| `/veterinarians/{veterinarianId}/availability-slots/{slotId}` | `GET` consulta; `PUT` cambia la hora con control de versión. |
| `/veterinarians/{veterinarianId}/availability-slots/{slotId}/status` | `PATCH` bloquea o republica un turno, con motivo y control de versión. |
| `/veterinarians/{veterinarianId}/availability-slots/{slotId}/events` | `GET` consulta auditoría administrativa. |
| `/availability-slots` | `GET` para propietarios; solo ofrece horarios dentro de la ventana solicitables. |
| `/veterinarian-profiles`, `/veterinarian-profiles/{veterinarianId}` | `GET` del directorio y ficha para propietarios. |
| `/veterinarian-profiles/{veterinarianId}/photo` | `GET` de la foto profesional; `profilePhotoUrl` es `null` cuando no tiene foto. |
| `/veterinarian-profiles/{veterinarianId}/qualifications/{qualificationId}/diploma` | `GET` únicamente del diploma publicado de un veterinario activo. |

El documento OpenAPI generado detalla campos, errores y tipos exactos. Las altas devuelven un perfil pendiente: `201` confirma la persistencia de cuenta, perfil e invitación, no la entrega del correo.

## Persistencia y verificación

Se añaden V7 (estados y superadministrador), V8 (perfiles, títulos y diplomas), V9 (invitaciones y tareas de correo), V10–V11 (disponibilidad y auditoría) y V12–V14 (citas, ocupación, asignaciones, eventos y validaciones históricas). V7 convierte las cuentas existentes conservando sus datos y revoca sesiones históricas de las que ya estaban desactivadas. Los perfiles profesionales no se inventan para cuentas antiguas creadas manualmente.

Ejecutar `mvnw.cmd verify` contra `grownupsvet_test` y después `scripts/validate_generated_openapi.py` según la [guía OpenAPI](openapi/README.md). Las pruebas usan identidades ficticias y correo controlado; no constituyen revisión compartida, pruebas de pantallas ni entrega a destinatarios reales. La disponibilidad excluye las citas pendientes y confirmadas y protege su ocupación en PostgreSQL.

El 13 de septiembre de 2026 se completaron 334 pruebas, sin fallos, errores ni omisiones. La verificación incluye conversión V6→V7 con cuentas históricas, bootstrap concurrente, permisos vigentes, recuperación/login/JWT de cuentas pendientes, invitaciones con PostgreSQL, expiración exacta, consumo concurrente, cancelación, cuotas, reintentos de correo y privacidad del directorio. Los nueve casos del validador PDF incluyen un documento comprimido que expandiría 512 MiB, su rechazo en el proceso separado y la validación de otro documento correcto inmediatamente después.

También se comprobó el recorrido del validador desde el JAR ejecutable mediante un programa de prueba temporal cargado con `PropertiesLauncher`: el padre detectó el empaquetado y lanzó correctamente el worker interno. El programa y el PDF de prueba permanecen únicamente en `target/`, fuera del artefacto de aplicación y del repositorio versionado.
