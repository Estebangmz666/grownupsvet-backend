# Plan: primer incremento oficial del frontend administrativo

Fecha: **22 de octubre de 2026**, America/Bogota.

**Objetivo:** pasar de los listados por rol a la gestión de cuentas de personal: consultar detalle, crear, editar, habilitar/deshabilitar, reenviar/cancelar invitaciones y activar una cuenta desde su enlace. La referencia principal es [SCRUM-34](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-34), de la historia [SCRUM-10](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-10), con validación en [SCRUM-35](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-35).

**Estado:** plan listo para revisión; este documento no implementa el incremento. Los cambios de estado autorizados en Jira se registran en la sección 14. No se ejecutaron nuevas pruebas ni instalaciones, y no se hizo commit, push o despliegue durante esta planificación.

Se conserva el flujo de `daily.md`: revisar fuentes y repositorios, delimitar el incremento y detenerse en el plan. La petición actual de Esteban autoriza expresamente cambiar los estados correspondientes de Jira; esa autorización prevalece sobre la regla de solo lectura del flujo. El plan se guarda en la carpeta de planificación del backend siguiendo esa metodología, pero **todo el código previsto pertenece al clon activo del frontend**, salvo que aparezca un defecto de contrato que se deba revisar por separado.

## 1. Fuentes y orden de autoridad

1. Jira consultado en vivo el 22/10: títulos, descripciones, criterios, estados, responsables, fechas, vínculos y comentarios visibles de las tareas seleccionadas.
2. Código backend, DTOs y permisos actuales; [OpenAPI desplegado](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs), vuelto a consultar: **0.9.0**.
3. [Personal e invitaciones](personal-e-invitaciones.md), [usuarios y permisos vigentes](arquitectura-usuarios-y-permisos.md) y [citas y agenda](citas-y-agenda.md).
4. [Plan de vistas por rol](C:/Users/ASUS/Documents/ChatGPT/grownupsvet-frontend/docs/plan-implementacion-2026-10-21-vistas-por-rol.md) e [informe de implementación](C:/Users/ASUS/Documents/ChatGPT/grownupsvet-frontend/docs/verificacion-vistas-por-rol-2026-10-22.md).
5. Backlog y RNF del proyecto: `C:\Users\ASUS\Documents\ChatGPT\GrownupsVet\docs\backlog-jira-propuesto.md` y `requisitos-no-funcionales.md`. Son contexto; no sustituyen los estados vivos ni convierten propuestas históricas en acuerdos nuevos.

El conector nuevo de Atlassian devolvió el sitio con acceso de lectura/escritura, pero búsqueda, JQL y descubrimiento agotaron el tiempo de espera. La evidencia funcional de Jira procede de su interfaz autenticada. No se usaron datos históricos para afirmar un estado actual.

La documentación inicial de septiembre describía tres roles y capacidades todavía propuestas. Para este plan prevalecen los **cuatro roles y operaciones actuales**. P03 en Jira significa personal y permisos; **no equivale al proceso clínico P3**.

## 2. Estado de los repositorios y pendientes anteriores

Se ejecutó `git fetch --no-tags origin` correctamente en ambos repositorios, sin integrar ni alterar sus cambios locales.

| Repositorio | Ruta y rama | HEAD y referencia remota verificada | Divergencia |
| --- | --- | --- | --- |
| Frontend activo | `C:\Users\ASUS\Documents\ChatGPT\grownupsvet-frontend`, `codex/fix-web-cloudflare` | `a028b06c0a744483646768fa0da7e629d3074fd2`, igual a `origin/master`. La rama actual no tiene upstream configurado; se comparó con master por el origen y contexto del clon. | 0 commits exclusivos en cada lado. |
| Backend | `C:\Users\ASUS\Documents\ChatGPT\GrownupsVet\grownupsvet-backend`, `main` | `0bd38ed7bfe9a4cc715bb9f1e21dbcafc53c31cc`, igual a `origin/main`. | 0 commits exclusivos en cada lado. |

Ambos árboles tienen cambios anteriores sin commit. En el frontend incluyen reparaciones web, PWA/gateway y el incremento de roles. La coincidencia de commits **no incluye** esas modificaciones. Se preservarán; no se usará el clon antiguo anidado dentro de GrownupsVet ni se publicará el conjunto automáticamente.

Base disponible:

- Un login y sesión común; OWNER conserva su portal, SUPER_ADMIN consulta administradores, ADMINISTRATOR veterinarios y VETERINARIAN su agenda.
- El informe anterior registra TypeScript, ESLint, **121 pruebas en 20 archivos**, compilación web y recorridos con una simulación del contrato. Son resultados del incremento anterior, **no ejecuciones de esta planificación**.
- Esteban confirmó ahora un recorrido satisfactorio. No especificó los roles ni escenarios, por lo que no se transforma esa confirmación en aceptación exhaustiva de las cuatro cuentas, expiración, correo o revocación.
- Continúan pendientes aceptación integrada completa, revisión compartida y publicación. Las credenciales de Cloudflare del compañero siguen sin estar disponibles.
- Los problemas previos de foto web del propietario, algunos errores de recuperación y selector de mascotas no se incluyen de forma implícita en este incremento administrativo.

La continuación de validación de la base se incorpora al primer paso del incremento. No se rehace el login ni se abre gestión clínica para suplir sus pendientes.

## 3. Selección del incremento desde Jira

### Tareas seleccionadas y dependencias

Fechas mostradas por Jira, conservadas sin reprogramar. Los responsables son los registrados, no una redistribución nueva del equipo. La instrucción de Esteban autoriza trabajar en el portal administrativo aunque conserve otro responsable histórico en Jira.

| Tarea y título | Responsable registrado | Estado antes de esta revisión | Vencimiento | Prioridad para este plan |
| --- | --- | --- | --- | --- |
| [SCRUM-34 — P03-FG: Construir gestión de personal y cambios de estado con confirmaciones comprensibles](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-34) | Juan Manuel Peña Hernandez | Por iniciar | 26/09/2026, vencida | Principal: convierte las consultas actuales en operaciones útiles de personal. Depende del contrato/backend 33 y 78, ya Finalizado en Jira. |
| [SCRUM-35 — P03-V: Verificar escalamiento de privilegios y desactivación; revisar la matriz clínica para pruebas posteriores](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-35) | Juan José Carvajal Gómez | En curso | 27/09/2026, vencida | Validación funcional y de permisos de 34; exige revisión de matriz con el equipo. |
| [SCRUM-25 — P01-FG: Construir acceso y manejo de sesión/permisos del portal de gestión](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-25) | Juan Manuel Peña Hernandez | Por iniciar | 15/09/2026, vencida | Base ya implementada localmente; continuar su comprobación real y la integración de activación. No rehacerla. |
| [SCRUM-26 — P01-V: Verificar ambos accesos, perfil, expiración y denegación entre usuarios; adjuntar evidencia](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-26) | Juan Manuel Peña Hernandez | En curso | 16/09/2026, vencida | Regresión de sesión y conservación del acceso del propietario. No cierra sin el recorrido integrado exigido. |
| [SCRUM-75 — X05: Preparar ambos frontends y sus convenciones de interfaz](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-75) | Juan José Carvajal Gómez | Por iniciar | 10/09/2026, vencida | Continuidad de navegación común, mocks y convenciones de teclado/foco/estados. Sigue pendiente el acuerdo/reparto registrado que pide la tarea. |
| [SCRUM-10 — Historia P03 de personal](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-10) | Juan Manuel Peña Hernandez | En curso | 27/09/2026, vencida | Historia padre, no se considera terminada por implementar el frontend. |
| [SCRUM-80 — X11: Revisar recuperación, personal y horarios y cerrar evidencias](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-80) | Juan José Carvajal Gómez | En curso | 04/10/2026, vencida | Recibirá evidencia parcial de personal; recuperación y disponibilidad quedan fuera de este incremento. |

SCRUM-34 no tiene descripción propia ni comentarios visibles con reglas adicionales. Se interpreta junto con su historia padre y el contrato. SCRUM-10 exige crear/habilitar personal autorizado, datos profesionales, efecto de desactivación y ausencia de escalamiento. Su descripción y SCRUM-35 separan explícitamente las pruebas clínicas posteriores P08–P11.

Se observaron referencias de sprint en los detalles, pero no se verificó pertenencia a un sprint activo ni se tomó su fecha como vencimiento. No se cambian sprints, asignaciones, fechas ni prioridades.

### Vencimientos de los próximos siete días

La consulta de Jira `project = SCRUM AND statusCategory != Done AND duedate >= "2026-10-22" AND duedate <= "2026-10-29" ORDER BY duedate ASC, key ASC` devolvió **14 actividades**, todas Por iniciar:

| Fecha | Actividades y responsable registrado | Motivo para no incluirlas ahora |
| --- | --- | --- |
| 22/10 | SCRUM-53 fórmula backend — Esteban; SCRUM-54 fórmula frontend de gestión — Juan Manuel; SCRUM-85 contrato de historial/recordatorios — Esteban. | Pertenecen a clínica y contratos siguientes, sin el recorrido P08 previo. |
| 23/10 | SCRUM-16 historia de fórmula — Juan Manuel; SCRUM-55 validación — Juan José. | Dependencias clínicas y de atención pendientes. |
| 25/10 | SCRUM-56 historial backend — Esteban; SCRUM-57 historial propietario — Juan José. | Otro proceso y otro portal. |
| 26/10 | SCRUM-17 historia de historial — Juan José; SCRUM-58 validación — Juan Manuel. | Dependen de atenciones y tratamientos. |
| 28/10 | SCRUM-59 adendas backend — Esteban; SCRUM-60 adendas gestión — Juan Manuel; SCRUM-61 lectura propietario — Juan José. | No existe todavía el recorrido clínico que deben corregir. |
| 29/10 | SCRUM-18 historia de adendas — Esteban; SCRUM-62 validación — Juan José. | Mismas dependencias; no se abre P3 en este plan. |

La selección prioriza continuidad y prerrequisitos disponibles frente a fechas antiguas o cercanas. No se amplió a 14 días porque ya hay candidatas y una continuidad habilitada. Este cuadro informa el corte de Jira; **no crea un cronograma ni compromisos de entrega**.

## 4. Resultado y límites del primer incremento

### Incluido

1. Conservar listas, filtros y paginación y añadir navegación lista → detalle → crear/editar.
2. SUPER_ADMIN gestiona cuentas de administradores; ADMINISTRATOR gestiona cuentas y perfil base de veterinarios.
3. Alta sin contraseña, edición de campos permitidos y presentación de correo/rol como datos inmutables.
4. Cambiar estado con confirmación comprensible y efectos reales del backend.
5. Reenviar y cancelar invitaciones, mostrando espera/cuota/conflicto sin atribuir entrega de correo al éxito HTTP.
6. Formulario público de activación, accesible desde enlace directo y recarga; éxito seguido de login normal, sin autoautenticar.
7. Estados de carga, vacío, error de campo, denegación y resultado incierto, con aislamiento de operaciones entre sesiones.
8. Verificación de roles, teclado, foco, móvil/computador y recorrido integrado con evidencia identificada como real o simulada.

### Fuera de este incremento

- Disponibilidad (SCRUM-37), confirmación/gestión de citas (SCRUM-41/45), reasignación de citas, clínica P3 e historia del propietario.
- Editor de roles/permisos, herencia entre roles, creación de SUPER_ADMIN, cambio del correo o del rol y nuevas reglas clínicas.
- Títulos adicionales, PDF de diplomas y su publicación; sí se incluyen el **título base y su institución**, obligatorios en el perfil veterinario. Es una delimitación de esta entrega, no una afirmación de que esas otras funciones estén implementadas ni de que dejen de pertenecer al producto.
- Fotos/perfil editable propio del personal, búsqueda libre, orden arbitrario, importación masiva, métricas o tableros nuevos.
- Cambios de backend, esquema, OpenAPI generado, SMTP, secretos o despliegue. Las dependencias ambientales se verifican antes de aceptar el recorrido completo y se atienden en trabajo explícito, no dentro de la planificación.

Si durante la revisión compartida se determina que un criterio de SCRUM-34 exige funciones excluidas, este incremento aportará avance parcial y la tarea seguirá abierta. No se ampliará el plan silenciosamente ni se cerrará un criterio diferido.

## 5. Roles, permisos y estados vigentes

| Actor | Recurso que puede gestionar | Permiso exigido |
| --- | --- | --- |
| SUPER_ADMIN | Administradores | `ADMINISTRATOR_MANAGE` |
| ADMINISTRATOR | Veterinarios y su perfil profesional | `VETERINARIAN_MANAGE` |
| VETERINARIAN / OWNER | Ninguna gestión de personal | No se les muestra ni permite esta navegación. |

Se comprueban rol y permiso tanto en botones como al abrir rutas. El backend conserva la autorización definitiva. El formulario determina el tipo de cuenta por el recurso; no hay un selector libre de rol. La redacción general de la historia sobre cambios de rol no autoriza inventar una operación que el contrato actual no tiene.

- Alta: `PENDING_ACTIVATION`, sin contraseña ni acceso. Un `PATCH ACTIVE` no sustituye la activación.
- Activación válida: `ACTIVE`; permite iniciar sesión con la contraseña establecida.
- Cancelación de invitación: `DISABLED`, con enlace invalidado; no borra la cuenta.
- Deshabilitar invalida invitaciones y sesiones previas. Rehabilitar una cuenta que ya tenía contraseña la conserva, pero **no revive sus sesiones antiguas**.
- Un veterinario deshabilitado deja de aparecer con turnos públicos y sus citas futuras se marcan para reasignación, **no se cancelan**. La confirmación lo explicará; la reasignación no se implementa aquí.
- Los DTO no contienen `hasPassword` ni `canReactivate`. Una cuenta `DISABLED` puede necesitar rehabilitación o una nueva invitación. No se deducirá esa diferencia solo por su estado: se atenderán `STAFF_ACTIVATION_REQUIRED` y `STAFF_INVITATION_STATE_CONFLICT`, explicando la alternativa disponible sin ejecutar otra operación automáticamente.
- Cambiar efectivamente el título/institución base, tras la normalización del backend, retira la publicación del diploma asociado; el formulario debe advertir ese efecto aunque la gestión del PDF quede fuera.

## 6. Contrato que consumirá el frontend

Prefijo **`/api/v1`**, confirmado en OpenAPI vivo y controladores. Son operaciones existentes; no se proponen endpoints nuevos.

| Operación | Ruta | Resultado que debe interpretar la interfaz |
| --- | --- | --- |
| Listar | `GET /administrators` o `/veterinarians` | Página con `items`, `page`, `size`, `totalElements`, `totalPages`. |
| Detalle | `GET /administrators/{id}` o `/veterinarians/{id}` | 200, perfil administrativo o veterinario. |
| Crear | `POST /administrators` o `/veterinarians` | 201 y `Location`; cuenta pendiente e invitación persistidas, **no correo entregado**. |
| Editar | `PUT /administrators/{id}` o `/veterinarians/{id}` | 200; reemplazo completo de campos editables, no PATCH parcial. |
| Habilitar/deshabilitar | `PATCH /administrators/{id}/status` o `/veterinarians/{id}/status` | Cuerpo con `status: ACTIVE` o `DISABLED`; 200 o conflicto de activación. |
| Reenviar invitación | `POST /staff/{userId}/invitations`, sin cuerpo | 202 con `invitationId`, `userId`, `expiresAt`, `deliveryStatus: PENDING`. |
| Cancelar invitación | `DELETE /staff/{userId}/invitations` | 204; cancelación idempotente para el estado aplicable. |
| Activar | `POST /auth/account-activations` | Público, `token`, `password`, `confirmPassword`; 204, sin token de sesión. |

El UUID devuelto en estos perfiles corresponde al usuario; debe validarse el DTO real y usarse ese identificador para las invitaciones. No se creará una equivalencia imaginaria con un identificador de sesión o de invitación.

Las listas conservan `page` desde cero, `size` entre 1 y 100 y `status` opcional. El orden es fijo `createdAt DESC, id ASC`. No se inventan parámetros `search`, `sort` o filtros de autorización.

### Datos de formularios

| Formulario | Campos y validación del contrato |
| --- | --- |
| Crear administrador | `email` y `fullName`. Nombre hasta 150 caracteres y al menos dos componentes; no exigir exactamente dos apellidos. Correo entre 3 y 254. |
| Editar administrador | Solo `fullName`; correo visible pero no editable. |
| Crear veterinario | `email`, `fullName`, `professionalPhoneNumber`, `professionalRegistrationNumber`, `baseDegreeTitle`, `baseDegreeInstitution`, `biography` opcional. |
| Editar veterinario | Los campos editables anteriores, **sin email**. `PUT` envía el conjunto completo; omitir/null biografía elimina su contenido. |
| Activación | Token opaco de 43 caracteres Base64URL; contraseña de 15–128 puntos de código Unicode y confirmación exacta. No recortar ni transformar contraseñas ni imponer una combinación de símbolos distinta al contrato. |

Teléfono profesional E.164, `+` seguido de 2–15 dígitos, primero distinto de cero. Matrícula hasta 50 caracteres, patrón `^[A-Za-z0-9][A-Za-z0-9 ./-]{0,49}$`, única y normalizada a mayúsculas por el backend. Título e institución hasta 200 cada uno; biografía hasta 2000. No se pide nacimiento ni contraseña al operador que crea la cuenta. Registrar matrícula no implica validación ante COMVEZCOL.

## 7. Invitación, activación y navegación pública

El backend genera el enlace como `STAFF_ACTIVATION_URL + "?token=" + token`. Se propone la ruta **del frontend** `/activate-account`; no es una nueva ruta API. Debe resolverse en Pages con entrada directa y recarga, conservando una sola aplicación y el navegador raíz común.

1. El operador crea el perfil; la interfaz muestra «Cuenta creada, pendiente de activación». No muestra una contraseña temporal ni copia enlaces que la API no devuelve.
2. La persona abre el enlace y ve el formulario. **Abrirlo o recargar no consume la invitación**: no hay GET de validación de token en el contrato y no se inventará uno.
3. Se envía el token únicamente a la activación pública, sin adjuntar por defecto la sesión del operador. Al enviar contraseña y confirmación válidas se consume el token.
4. Un 204 muestra activación terminada y acceso al login normal. No produce sesión automática ni cambia la cuenta de otro usuario que ya tuviera una sesión abierta.
5. Ausencia de token, formato incorrecto o enlace inválido/vencido/consumido producen un estado comprensible; se indica solicitar otra invitación al personal autorizado.

Los enlaces duran 48 horas y son de un solo uso. Reenviar invalida el enlace anterior, tiene separación mínima de 60 segundos y máximo cinco invitaciones por cuenta en ventana móvil de 24 horas, incluida la inicial. La interfaz no inventará un contador restante si el servidor no lo informa.

Token y contraseña no van a la instantánea de sesión, almacenamiento persistente, analítica ni mensajes de error. El token se mantiene solo mientras se atiende el enlace y se retira de la URL al terminar o abandonar. No se añaden llamadas a terceros desde el formulario. Evidencia, logs y capturas deben omitir credenciales y tokens reales. Un enlace entrante mientras exista otra sesión debe poder abrirse sin sustituir su identidad.

## 8. Comportamiento de formularios, errores y concurrencia

- Errores de API por `errorCode` y `fieldErrors`; no comparar el texto del servidor. Mantener valores no sensibles del formulario, foco en el resumen/campo afectado y un mensaje de corrección.
- 400: validación, estado no permitido, confirmación distinta o `STAFF_INVITATION_INVALID`. No convertir todos los 400 en «enlace vencido».
- 401: mecanismo común de sesión vigente; 403: acceso denegado conservando sesión, nunca lista vacía. Un fallo de conexión no equivale a falta de permisos.
- 404: cuenta ya ausente/no disponible; permitir volver y actualizar listado.
- 409: `EMAIL_ALREADY_REGISTERED`, `PROFESSIONAL_REGISTRATION_ALREADY_EXISTS`, `STAFF_ACTIVATION_REQUIRED` o `STAFF_INVITATION_STATE_CONFLICT`, con explicación específica.
- 429 `STAFF_INVITATION_RATE_LIMITED`: informar espera/cuota; no prometer `Retry-After` ni una cuenta regresiva exacta no publicada.
- 503 `STAFF_INVITATIONS_UNAVAILABLE`: no mostrar cuenta creada o correo enviado. La alta con invitaciones deshabilitadas se revierte en backend.
- Bloquear doble envío mientras una operación esté pendiente. Ante pérdida de respuesta después de POST/PUT/PATCH/DELETE, el resultado puede ser incierto: ofrecer consultar de nuevo, **sin repetir mutaciones automáticamente**. Abortar la espera de la interfaz no deshace una escritura del servidor.
- Capturar contexto de sesión al comenzar y comprobarlo antes de modificar estado, navegar o continuar otra petición. Una respuesta tardía de A no muestra éxito, borra formularios ni cierra la cuenta B.
- Después de una escritura confirmada, actualizar detalle y consultar la página aplicable al filtro actual. Si deja de coincidir o la última página queda vacía, ajustar la página de manera explícita. No alterar el orden contratado para fingir que un alta siempre aparece primera.
- Las ediciones de estos perfiles no tienen versión/If-Match en el contrato: recargar antes de editar y no afirmar protección frente a toda actualización simultánea. Un control optimista nuevo requeriría otro cambio de backend.

## 9. Diseño previsto y reutilización

Las rutas siguientes pertenecen al **clon activo del frontend**. Los nombres nuevos son propuestas, no archivos ya implementados.

| Módulo existente | Cambio previsto |
| --- | --- |
| `apps/administrativo/src/StaffArea.tsx`, pantallas, componentes y estilos del módulo | Extender lista a detalle y formularios; navegación interna bajo el único `NavigationContainer`. Componer campos y estados comunes sin una pantalla genérica que ignore diferencias de roles. |
| `useStaffPage.ts`, `staffPageRequests.ts` y `loadInitialStaffData.ts` | Reutilizar paginación, cancelación, identidad y primera carga; invalidación/refresco después de cambios. |
| `packages/api-client/src/staff.api.ts` y `staff.types.ts` | Añadir detalle, altas, actualizaciones, estado e invitaciones con DTOs explícitos y validación de respuestas. No modificar el contrato generado del backend. |
| `src/navigation/AuthenticationNavigator.tsx`, `RootNavigator.tsx` y flujo público compartido | Incorporar activación y lectura del enlace, sin otro proveedor ni sesión paralela; conservar login/registro/recuperación. |
| `packages/authentication` y cliente HTTP común | Reutilizar generación, token capturado, expiración y tratamiento de 401. Separar la solicitud pública de activación de la sesión autenticada. |
| `packages/accessibility-kit` y `ConfirmDialog` | Reutilizar controles y estados; comprobar en navegador foco inicial, devolución del foco y Escape antes de considerarlos suficientes. |

Propuestas de componentes: `StaffAccountDetailsScreen`, `AdministratorAccountFormScreen`, `VeterinarianAccountFormScreen` y `StaffAccountActivationScreen`. Se mantendrán nombres claros, tipos por operación y código en inglés, con mensajes de interfaz en español. No se cambia Expo/React Native Web ni se extraen servicios nuevos.

No se requieren tablas, migraciones, endpoints, secretos nuevos ni regeneración manual de OpenAPI para las operaciones descritas. Una discrepancia deberá registrarse y revisarse antes de continuar la parte afectada.

## 10. Secuencia de implementación futura

| Paso | Trabajo | Resultado comprobable |
| --- | --- | --- |
| 1. Fijar base y entorno | Revalidar rama/cambios, contrato, permisos y pruebas de la base; comprobar qué cuentas de prueba existen y si invitaciones/URL están configuradas, sin exponer secretos. | Matriz de operaciones y entorno real/simulado identificados; sin sobrescribir el incremento previo. |
| 2. Cliente y simulación | Incorporar DTOs/operaciones y ampliar el sustituto HTTP derivado del contrato para alta/edición/estado/invitación/activación y errores relevantes. | Respuestas y transiciones fieles al contrato; sin tokens ficticios aceptados como evidencia del servidor real. |
| 3. Detalle y formularios | Añadir navegación, lectura individual, alta y edición por actor; errores de campo y conservación de valores. | SUPER_ADMIN completa administradores; ADMINISTRATOR completa perfil base veterinario. |
| 4. Estados e invitaciones | Confirmaciones específicas, envío/cancelación, conflictos y refresco de la lista. | No doble escritura ni éxitos ficticios; efectos de deshabilitación explicados. |
| 5. Activación pública | Resolver enlace directo/recarga y completar contraseña → 204 → login, aislado de sesiones abiertas. | Recorrido ficticio completo y preparado para la URL real. |
| 6. Verificación integrada | Ejecutar matriz siguiente, comprobar solicitudes y estados visibles, distinguir datos simulados de reales. | Evidencia reproducible de los escenarios cubiertos y lista explícita de pendientes. |
| 7. Documentación y revisión | Actualizar README/guía, manual y evidencia pertinente durante la implementación; contrastar con criterios Jira y revisión del equipo. | Informe por SHA/árbol probado y propuesta de transición, sin cerrar funciones que no se entregaron. |

No hay fechas u horas propuestas para estos pasos. Terminar este plan **no autoriza ejecutar** la secuencia.

## 11. Verificación prevista y aceptación observable

| Grupo | Casos mínimos y resultado esperado |
| --- | --- |
| Acceso | Los dos actores gestores acceden solo a su recurso. OWNER/VETERINARIAN y un rol sin permiso no pueden abrir acciones/rutas; backend deniega solicitudes manipuladas. Sin herencia de SUPER_ADMIN hacia veterinarios. |
| Alta | Ambas cuentas pendientes, campos requeridos/límites Unicode, teléfono/matrícula, duplicados y 503. Éxito persiste una sola cuenta; correo/rol no elegibles; no contraseña del operador. |
| Edición | Conjunto completo de datos, eliminación explícita de biografía, correo inmutable, actualización y efecto sobre diploma base. Cancelar conserva datos del servidor. |
| Estado | Confirmar/cancelar deshabilitación; sesiones anteriores rechazadas; rehabilitación conserva contraseña sin revivir sesiones; pendiente no se salta activación. Citas futuras del veterinario no se presentan como canceladas. |
| Invitación | Reenvío, cancelación repetida, enlace anterior inválido, espera/cuota/conflicto y cuenta con contraseña que debe recuperar acceso. Respuestas 201/202 no se presentan como buzón entregado. |
| Activación | Enlace directo y recarga; apertura sin consumo, token ausente/incorrecto/vencido/consumido, contraseña válida/común/inválida, confirmación exacta, doble envío/uso simultáneo, 204 y login con cuenta activa. No se altera otra sesión abierta. |
| Red y sesión | 401/403/404/409/429/503, pérdida de respuesta después de escribir, expiración a mitad de formulario, logout/login A→B con operación A pendiente; ninguna continuación altera B. |
| Listas | Más de una página, cambios de filtro durante cargas, creación/estado que cambia la pertenencia al filtro, última página vacía y recarga. |
| Interacción | Recorrido por teclado, foco visible y recuperación del foco tras diálogo/error, etiquetas y errores comprensibles, estados no solo por color, ampliación y ancho móvil/desktop sin perder acciones. No confundir un test de contraste con aceptación de toda la accesibilidad. |
| Regresión | Login/registro/recuperación públicos siguen abriendo; propietario conserva lecturas y navegación; veterinario conserva agenda diaria; no peticiones de propietario al restaurar personal. |

Comprobaciones futuras: `npm run check`, `npm run web:build`, `npm run web:pages` desde el frontend. Pruebas unitarias/integración de decisiones de sesión, DTO, errores, formularios y coordinación; recorridos del navegador contra simulación para fallos y contra backend autorizado para aceptación. No se repite Maven ni se modifica OpenAPI por un cambio exclusivamente frontend salvo discrepancia que lo justifique.

La evidencia deberá incluir fecha, commit base y cambios locales, entorno y versión del contrato, casos/resultados, solicitudes sin credenciales, capturas móvil/computador y limitaciones. No se sumarán pruebas HTTP del mock a la cifra de pruebas del proyecto ni se presentarán como integración real.

## 12. Dependencias y condiciones de parada

| Dependencia o riesgo | Tratamiento y límite |
| --- | --- |
| URL pública de activación y acceso a Cloudflare | No bloquea diseñar/desarrollar/probar localmente. Sí impide afirmar recorrido público completo mientras no exista URL válida. No publicar ni crear una cuenta alternativa durante esta planificación. |
| Invitaciones habilitadas | La guía y el corte del 15/10 indican invitaciones deshabilitadas por falta de URL frontend. **No se consultó la configuración actual de Cloud Run**: sigue sin verificar, no se afirma que continúe deshabilitada. Comprobar antes de la integración real. |
| Configuración de correo | Verificar entorno y canal reales sin cambiar SMTP por inferencia. SMTP aceptado, captura en Sandbox y recepción en buzón son evidencias diferentes. No asegurar ninguna por un 201/202. |
| Contrato de activación en producción | `STAFF_ACTIVATION_URL` exige HTTPS real y no acepta query/fragmento en su configuración; el backend añade el token. Al habilitar invitaciones se necesitan la clave estable y configuración de correo ya documentadas. No registrar valores de secretos. |
| Credenciales y datos de prueba | Cuentas apropiadas y destinatarios controlados para cambios de estado/correo. No usar datos reales de terceros ni deshabilitar cuentas operativas para demostrar el flujo. |
| Historia de permisos clínicos aún pendiente | No inventar relación clínica ni acceso del administrador. SCRUM-35 conserva su revisión pendiente con el equipo; ejecución de pruebas clínicas cuando existan P08–P11. No bloquea las operaciones de cuentas ya contratadas. |
| Cambios de contrato o nueva decisión de negocio | Detener solo la parte dependiente y preguntar antes de implementar. No construir un endpoint, permiso o comportamiento supuesto para seguir. |
| Resultado incierto de una escritura o cambio de sesión | No repetir automáticamente. Reconciliar mediante consulta; la cancelación local no revierte el servidor. |

No se detectó una decisión de negocio nueva indispensable para redactar el núcleo de este plan: sus actores, datos y estados están en el contrato. La publicación/configuración y la revisión compartida son dependencias de aceptación, no respuestas que se asuman aprobadas por el paso del tiempo.

## 13. Condiciones de cierre y continuidad

El incremento se considera **implementado localmente** cuando todas sus funciones incluidas y errores operan con el contrato, las comprobaciones correspondientes pasan y se documenta el entorno probado. Se considera **integrado y aceptado** cuando además se demuestra el recorrido con backend y datos autorizados, efecto real sobre sesiones, enlace de activación y canal de correo verificado, y se registra la revisión requerida.

Si falta URL pública/configuración, el informe deberá decir «implementado y verificado localmente; aceptación pública pendiente». No se presentará como personal completo en producción. Las tareas 34/35 y sus historias no se cierran automáticamente por un build correcto o datos simulados; 75 tampoco se cierra sin sus acuerdos. SCRUM-26 y 80 mantienen todos los criterios de sus descripciones, aunque este incremento solo aporte parte de ellos.

El siguiente trabajo se elige otra vez desde Jira: completar pendientes de este núcleo antes de disponibilidad, citas o clínica, salvo prioridad y dependencias verificadas que justifiquen otra decisión. Títulos adicionales/diplomas requieren una selección explícita desde el backlog si se abordan después.

## 14. Seguimiento de Jira autorizado en esta revisión

El flujo actual ofrece **Por iniciar, En curso, En revisión y Finalizado**. No existe una opción «Iniciado» en el menú consultado. Se utiliza **En curso** como equivalente a la intención de iniciar, sin crear ni renombrar estados.

| Tarea | Acción realizada y motivo | Resultado verificado |
| --- | --- | --- |
| SCRUM-34 | Por iniciar → En curso: incremento de gestión de personal seleccionado y planificado. | Aplicado en Jira el 22/10 y comprobado nuevamente después de recargar. |
| SCRUM-25 | Por iniciar → En curso: acceso/sesión por rol ya implementados, con aceptación integrada pendiente. | Aplicado en Jira el 22/10 y comprobado nuevamente después de recargar. |
| SCRUM-75 | Por iniciar → En curso: base común de ambos portales implementada y ampliación planificada; acuerdos pendientes. | Aplicado en Jira el 22/10 y comprobado nuevamente después de recargar. |
| SCRUM-10, 26, 35 y 80 | Conservar En curso; ya estaban iniciadas y mantienen criterios pendientes. | Estado comprobado en vivo; no necesitan transición. |
| SCRUM-37, 41 y 45 | Conservar Por iniciar; no se planifica su implementación en este incremento. | Estado comprobado en vivo; sin transición. |

No se modifican responsables, fechas, sprints, descripciones ni comentarios. No se cierra ninguna tarea. La revisión y planificación terminan con este documento y las transiciones solicitadas; la implementación espera la siguiente instrucción de Esteban.

Evidencia visual de la tarea principal: [SCRUM-34 en curso](C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/output/plan-frontend-administrativo-2026-10-22/jira-scrum-34-en-curso.png). La consulta `key in (SCRUM-25,SCRUM-34,SCRUM-75) ORDER BY key ASC` mostró las tres En curso después de recargar; las transiciones se verificaron también al aplicarlas.
