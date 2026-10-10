# Plan del próximo incremento: cambios administrativos de cita

Fecha: **8 de octubre de 2026**, zona `America/Bogota`.

**Seguimiento posterior del 08/10:** Esteban autorizó explícitamente implementar después de recibir este plan. La implementación local y sus verificaciones están en [el informe P07](verificacion-cambios-citas-2026-10-08.md). El contenido siguiente conserva la revisión y planificación original; sus restricciones describen aquella fase, no anulan la autorización posterior. Publicación, Jira y aceptación siguen separados.

**Seguimiento del 09/10:** Esteban autorizó commit, push y actualización de Jira después de verificar preparación. El informe registra las comprobaciones de esta fecha y la corrección de la captura del servidor de desarrollo. Publicar P07 permite completar SCRUM-43; SCRUM-81 y la historia SCRUM-13 conservan sus pendientes de contrato P12, revisión e integración.

**Estado: planificación terminada; implementación pendiente de una instrucción posterior y explícita de Esteban.** Este documento aplica `../../daily.md`. Durante esta revisión solo se consultaron fuentes, se ejecutó `git fetch --no-tags origin` y se creó este plan. No se implementó código, ejecutaron pruebas, arrancaron servicios, regeneró OpenAPI, publicó Git ni modificó Jira.

## 1. Punto de partida y fuentes

| Dato | Estado verificado el 08/10/2026 |
|---|---|
| Repositorio independiente | `C:/Users/ASUS/Documents/ChatGPT/GrownupsVet/grownupsvet-backend`, confirmado mediante `git rev-parse --show-toplevel` |
| Rama actual / seguimiento | `main` / `origin/main` |
| Remoto | `origin`: `https://github.com/Estebangmz666/grownupsvet-backend.git` |
| HEAD local | `777cd35b0a1bf589b10b1c167fcecd5d898d7cbd` |
| Commit remoto después del fetch | `777cd35b0a1bf589b10b1c167fcecd5d898d7cbd` |
| Actualización del remoto | `git fetch --no-tags origin` satisfactorio; no integró cambios al árbol |
| Divergencia | `git rev-list --left-right --count 'HEAD...@{upstream}'`: **0 locales / 0 remotos** |
| Cambios al comenzar | Ninguno modificado, preparado o sin seguimiento; diferencias local y preparada vacías |
| Cambio de esta revisión | Únicamente este plan, sin commit |
| Tecnología comprobada | Java 25, Spring Boot 4.1.1, PostgreSQL, JDBC/JPA según módulo, Flyway y springdoc; no se propone sustituir tecnologías |

Se aplicaron las instrucciones del usuario y de `daily.md`; no se encontraron archivos `AGENTS.md` adicionales en las rutas superiores inspeccionadas ni en el proyecto. Se leyeron el [README](../README.md), las guías de [citas](citas-y-agenda.md), [disponibilidad](disponibilidad-veterinaria.md), [permisos](arquitectura-usuarios-y-permisos.md), [OpenAPI](openapi/README.md), el [informe de citas](verificacion-citas-2026-09-28.md), el plan y la revisión histórica del 23/09 en la carpeta padre, el backlog previo y los acuerdos accesibles de conversaciones anteriores. La inspección de código se limitó principalmente a citas, seguridad, disponibilidad, correo y migraciones relacionadas.

Jira se consultó en vivo mediante Atlassian Rovo, sitio [GrownupsVet / SCRUM](https://uqvirtual-team-yavszk8l.atlassian.net/jira/software/projects/SCRUM/boards/1). Las decisiones nuevas del apartado 5 fueron respondidas por Esteban en esta conversación del 08/10; sus respuestas definen reglas del plan y no autorizan ejecutarlo.

El historial accesible más reciente incluye trabajo académico y la creación de `daily.md` el 06/10. El último incremento funcional que demuestra el repositorio es **0.7.0, citas**, del 28/09. Los planes previos se trataron como contexto histórico: sus referencias a citas inexistentes o a ocupación pendiente fueron superadas por el commit actual. La igualdad local/remoto acredita publicación de ese commit, no aceptación integral de las historias.

## 2. Pendientes anteriores y alcance de su evidencia

| Frente | Lo demostrado | Pendiente o límite |
|---|---|---|
| Implementación 0.7.0 | Solicitud idempotente, ocupación real, consulta, confirmación/rechazo, historia, corte, reasignación excepcional y acceso auditado al correo | Cancelación manual, reprogramación general e inasistencia; son la continuación elegida |
| Pruebas | [Informe del 28/09](verificacion-citas-2026-09-28.md): 431 pruebas satisfactorias | Son resultados históricos; en esta revisión no se ejecutó ninguna prueba |
| Contrato generado | OpenAPI 3.1.0, contrato 0.7.0, 57 operaciones/38 rutas y 31 respuestas validadas en aquella ejecución | Ampliar P07 y volver a generar/validar durante implementación |
| Commit y publicación | [Commit 777cd35](https://github.com/Estebangmz666/grownupsvet-backend/commit/777cd35b0a1bf589b10b1c167fcecd5d898d7cbd) presente en `origin/main` tras fetch | Este plan no tiene commit ni publicación |
| P06 / SCRUM-12 | SCRUM-39 y SCRUM-36 están Finalizado; el commit y la documentación respaldan el núcleo backend | Integración de los dos frontends, validación conjunta SCRUM-42 y aceptación; no rehacer ese núcleo |
| Revisión de contrato P06 | SCRUM-79 En revisión; comentario del 28/09 distingue publicación de revisión compartida | Registrar revisión de los tres integrantes; no darla por realizada por existir snapshot |
| P07 / SCRUM-43 | Rechazo, cancelación automática al corte y reasignación por veterinario inactivo | Completar los tres cambios administrativos acordados hoy |
| P12 / SCRUM-47 | Avisos durables de deshabilitación/reasignación, despacho tras commit y recuperación | Avisos generales, recordatorio previo y reglas del canal; quedan para otro incremento |
| Entorno y entrega | Pruebas históricas en PostgreSQL; correo de citas con `JavaMailSender` simulado | SMTP remoto, buzón real, despliegue, interfaces y accesibilidad visual no acreditados |

Los comentarios actuales del 28/09 en SCRUM-12, SCRUM-43, SCRUM-47, SCRUM-79 y SCRUM-81 concuerdan con el alcance parcial del informe. SCRUM-13 y SCRUM-15 siguen Por iniciar aunque sus subtareas backend están En curso: el estado de una subtarea no cierra la historia. La épica SCRUM-8 mantiene comentarios del 07/09 que describen un backend anterior; deberán actualizarse en un seguimiento posterior con evidencia vigente.

## 3. Jira: vencimientos y dependencias actuales

Ventana: vencidas antes del 08/10, vencen hoy y próximos siete días calendario **09/10–15/10 inclusive**. La consulta devolvió **37 incidencias abiertas: 24 vencidas, 2 vencen hoy y 11 próximas**. No hubo paginación pendiente. Una consulta adicional encontró cero incidencias abiertas sin fecha; no fue necesario ampliar a 14 días.

Consulta JQL de selección: `project = SCRUM AND statusCategory != Done AND duedate <= "2026-10-15" ORDER BY duedate ASC, priority DESC`. Se consultaron descripción, criterios disponibles, subtareas, comentarios, responsable, fecha, sprint y enlaces de las candidatas. SCRUM-43 carece de descripción propia y sus reglas se contrastaron con la historia SCRUM-13, comentarios y acuerdos de Esteban; Jira no especificaba sus actores ni plazos.

Responsables según Jira: **Esteban = ESTEBAN GÓMEZ LEON; Juan José = JUAN JOSÉ CARVAJAL GÓMEZ; Juan Manuel = JUAN MANUEL PEÑA HERNANDEZ**. Los títulos de la tabla son resúmenes, salvo las tareas backend/contrato prioritarias. «Padre» indica jerarquía; «bloquea» indica un enlace de dependencia registrado, y «funcional» una dependencia deducida del trabajo.

| Issue | Título | Responsable | Estado | Fecha límite | Condición | Dependencias | Motivo de atención |
|---|---|---|---|---|---|---|---|
| [SCRUM-75](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-75) | X05: Preparar ambos frontends y convenciones | Juan José | Por iniciar | 2026-09-10 | Vencida | Coordinación con Juan Manuel | Base de interfaces, mocks y accesibilidad |
| [SCRUM-6](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-6) | P01: Acceso y perfil | Juan José | En curso | 2026-09-15 | Vencida | 67 y BE 23 Finalizado | Falta integración/aceptación de portales |
| [SCRUM-25](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-25) | P01-FG: Acceso y sesión de gestión | Juan Manuel | Por iniciar | 2026-09-15 | Vencida | Padre 6 | Portal de gestión pendiente |
| [SCRUM-24](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-24) | P01-FP: Registro, acceso y perfil | Juan José | Por iniciar | 2026-09-15 | Vencida | Padre 6 | Portal del propietario pendiente |
| [SCRUM-26](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-26) | P01-V: Acceso, perfil y permisos | Juan Manuel | Por iniciar | 2026-09-16 | Vencida | Padre 6 | Validación integrada pendiente |
| [SCRUM-28](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-28) | P04-FP: Gestión de mascotas | Juan José | Por iniciar | 2026-09-18 | Vencida | Padre 7 | Interfaz de mascotas pendiente |
| [SCRUM-77](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-77) | X08: Demo de acceso y mascotas | Juan Manuel | Por iniciar | 2026-09-20 | Vencida | X04, X05, P01 y P04 en descripción | Demo compartida pendiente |
| [SCRUM-29](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-29) | P04-V: Datos inválidos y propiedad | Juan Manuel | Por iniciar | 2026-09-20 | Vencida | Padre 7 | Validación integrada pendiente |
| [SCRUM-7](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-7) | P04: Gestión de mascotas | Juan José | En curso | 2026-09-20 | Vencida | 76 y BE 27 Finalizado | FP/validación pendientes |
| [SCRUM-31](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-31) | P02-FP: Recuperación accesible | Juan José | Por iniciar | 2026-09-23 | Vencida | Padre 9 | Integración de recuperación pendiente |
| [SCRUM-32](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-32) | P02-V: Recuperación y desactivación | Juan Manuel | Por iniciar | 2026-09-24 | Vencida | Padre 9 | Validación de recuperación pendiente |
| [SCRUM-9](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-9) | P02: Recuperación por correo | Juan José | En curso | 2026-09-24 | Vencida | 76 y BE 30 Finalizado | FP/validación pendientes |
| [SCRUM-34](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-34) | P03-FG: Gestión de personal | Juan Manuel | Por iniciar | 2026-09-26 | Vencida | Padre 10 | Gestión frontend pendiente |
| [SCRUM-35](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-35) | P03-V: Privilegios y desactivación | Juan José | Por iniciar | 2026-09-27 | Vencida | Padre 10 | Validación conjunta pendiente |
| [SCRUM-10](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-10) | P03: Personal y roles | Juan Manuel | Por iniciar | 2026-09-27 | Vencida | 78 y BE 33 Finalizado | FG/validación pendientes |
| [SCRUM-8](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-8) | E1: Usuarios y mascotas | Esteban | En curso | 2026-09-27 | Vencida | Épica e historias integradas | Seguimiento antiguo; aceptación pendiente |
| [SCRUM-79](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-79) | X10: Publicar OpenAPI de solicitud, confirmación y agenda | Esteban | En revisión | 2026-10-02 | Vencida | Bloquea 12 | Revisión de los tres pendiente |
| [SCRUM-37](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-37) | P05-FG: Administrar intervalos | Juan Manuel | Por iniciar | 2026-10-02 | Vencida | Padre 11 | Gestión frontend pendiente |
| [SCRUM-80](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-80) | X11: Revisar recuperación y horarios | Juan José | Por iniciar | 2026-10-04 | Vencida | P02/P03/P05, ambos frontends | Recorridos conjuntos pendientes |
| [SCRUM-38](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-38) | P05-V: Fechas, solapamientos y teclado | Juan José | Por iniciar | 2026-10-04 | Vencida | Padre 11 | Validación integrada pendiente |
| [SCRUM-11](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-11) | P05: Publicar horarios sin conflictos | Juan Manuel | Por iniciar | 2026-10-04 | Vencida | 78 y BE 36 Finalizado | FG/validación pendientes |
| [SCRUM-81](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-81) | X12: Publicar OpenAPI de cambios de cita y avisos | Esteban | En curso | 2026-10-07 | Vencida | Bloquea 13 y 15 | Contrato P07/P12 incompleto; prioridad del incremento |
| [SCRUM-41](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-41) | P06-FG: Confirmación y agenda | Juan Manuel | Por iniciar | 2026-10-07 | Vencida | Padre 12 | Interfaz de gestión pendiente |
| [SCRUM-40](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-40) | P06-FP: Solicitud y seguimiento | Juan José | Por iniciar | 2026-10-07 | Vencida | Padre 12 | Interfaz del propietario pendiente |
| [SCRUM-42](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-42) | P06-V: Solicitud y concurrencia | Juan José | Por iniciar | 2026-10-08 | Vence hoy | Padre 12 | Verificación conjunta pendiente |
| [SCRUM-12](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-12) | P06: Solicitar y confirmar una cita | Esteban | En curso | 2026-10-08 | Vence hoy | 79 En revisión; BE 39 Finalizado | Coordinar aceptación, no rehacer backend |
| [SCRUM-45](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-45) | P07-FG: Cambios e historial | Juan Manuel | Por iniciar | 2026-10-11 | Próxima | Padre 13; contrato P07 funcional | Preparar integración del contrato nuevo |
| [SCRUM-44](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-44) | P07-FP: Cambios permitidos al propietario | Juan José | Por iniciar | 2026-10-11 | Próxima | Padre 13 | Revisar título/criterios por administración exclusiva acordada |
| [SCRUM-43](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-43) | P07-BE: Implementar transiciones, liberación/reasignación de ocupación y registro de inasistencia | Esteban | En curso | 2026-10-11 | Próxima | Padre 13; 81 bloquea la historia | Continuación principal del backend parcial |
| [SCRUM-46](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-46) | P07-V: Cambios y permisos | Juan José | Por iniciar | 2026-10-12 | Próxima | Padre 13 | Liberación y rollback de reserva original |
| [SCRUM-13](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-13) | P07: Cancelar, reprogramar e inasistencia | Juan Manuel | Por iniciar | 2026-10-12 | Próxima | Bloqueada por 81 | Historia completa necesita FG/FP/validación |
| [SCRUM-82](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-82) | X13: Publicar OpenAPI de registro y finalización de atención | Esteban | Por iniciar | 2026-10-13 | Próxima | Bloquea 14; reglas clínicas/contratos de citas | Preparar después de P07, no desplazar continuidad |
| [SCRUM-48](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-48) | P12-FP: Consulta/recepción de avisos | Juan José | Por iniciar | 2026-10-13 | Próxima | Padre 15; canal acordado | Requiere definición del siguiente incremento P12 |
| [SCRUM-47](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-47) | P12-BE: Implementar programación persistente y avisos de citas sin duplicación, según el canal elegido | Esteban | En curso | 2026-10-13 | Próxima | Padre 15; reglas P12 pendientes | Continuación posterior; avisos actuales son parciales |
| [SCRUM-49](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-49) | P12-V: Reinicio y cambios de avisos | Juan Manuel | Por iniciar | 2026-10-14 | Próxima | Padre 15 | Persistencia, repetición e invalidación |
| [SCRUM-20](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-20) | E2: Citas y comunicación de cambios | Juan Manuel | Por iniciar | 2026-10-14 | Próxima | Épica e historias aceptadas | Cierre requiere integración; sin sprint registrado |
| [SCRUM-15](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-15) | P12: Avisos de citas y próximos eventos | Esteban | Por iniciar | 2026-10-14 | Próxima | Bloqueada por 81; BE 47 En curso | P12 completo requiere canal, reglas y frontend |

**Sprints:** S3 está activo del **05/10 al 18/10**, y agrupa 81, 40–49, 12, 13, 15 y 82. Las tareas anteriores de septiembre y 79/37 corresponden a S1/S2 cerrados; algunas de S1 también aparecen en S6 futuro. Esa pertenencia adicional no modifica sus vencimientos. SCRUM-20 no tiene sprint registrado. El cierre de S3 el 18/10 no es la fecha límite de cada tarea.

Clínica se consultó como dependencia: SCRUM-14 vence 18/10, SCRUM-50 el 17/10 y SCRUM-83 el 16/10, fuera de esta ventana. SCRUM-82 sí entra, pero todavía depende de decisiones clínicas; se recomienda continuar P07 antes de abrir ese dominio. No se reasigna trabajo de los compañeros a Esteban ni se interpreta su estado Jira como una inspección de sus repositorios frontend.

## 4. Objetivo, prioridad y límites

**Incremento recomendado:** completar el backend administrativo de **P07 / SCRUM-43** y publicar la **parte P07 de SCRUM-81**: cancelación manual, reprogramación general e inasistencia. La continuidad sobre el 0.7.0, el vencimiento del 11/10 y el contrato vencido que bloquea a los consumidores justifican este orden.

Incluye transiciones, permisos, validaciones temporales, ocupación atómica, historia, idempotencia, invalidación de avisos antiguos, documentación y contrato. Se propone versión de contrato **0.8.0**, manteniendo OpenAPI 3.1.0; es una propuesta de versionado, no un contrato generado hoy.

Quedan fuera: autoservicio de cambios del propietario, frontend, clínica/finalización de atención, adendas, urgencias, cambios de tecnología, nuevas sedes, Thymeleaf, sustitución de Mailtrap/SMTP, nuevos canales, nuevos avisos generales y recordatorios P12. Se conserva la reasignación excepcional existente por veterinario deshabilitado con sus propias reglas, incluidos los casos permitidos del mismo día.

P07 actualizará agenda e historial consultables por los roles autorizados. **No añadirá envíos nuevos de cancelación, inasistencia o reprogramación general:** los eventos necesarios quedarán persistidos para el posterior P12. Sí evitará que los avisos existentes anuncien un horario ya superado. Completar P07 no permite cerrar SCRUM-81 completa, porque conserva P12 y revisión compartida pendientes.

Antes de ampliar el contrato, se deberá preparar una revisión concreta con los dos consumidores: estados nuevos, operación propuesta, errores y administración exclusiva. La realización y aceptación de esa revisión se registrarán separadamente; este documento no acredita contacto con integrantes.

## 5. Reglas de negocio confirmadas y decisiones técnicas

### 5.1 Acuerdos conservados

Una sede en Armenia; zona `America/Bogota`; turnos de 30 minutos e intervalos `[inicio, fin)`. `REQUESTED` y `CONFIRMED` ocupan; las citas terminales conservan historia. Propietarios consultan propias, veterinarios las actualmente asignadas y administradores todas. `SUPER_ADMIN` no hereda agenda. La identidad del propietario se deriva de la cuenta; no se puede transferir una cita a otra mascota o propietario mediante estos cambios.

El corte usa `APPOINTMENT_WORKDAY_START_TIME`, sin horario comercial predeterminado. Las solicitudes vencidas no pueden recuperar vigencia por reprogramación. Desactivar cuentas o archivar mascotas no cancela automáticamente las citas. La revelación del correo conserva su autorización y auditoría actuales.

### 5.2 Respuestas de Esteban del 08/10

| Operación | Actor | Origen | Condición temporal | Resultado y datos obligatorios |
|---|---|---|---|---|
| Cancelación manual | Solo administrador activo | `REQUESTED` o `CONFIRMED` | `ahora < inicio original`; en el instante del inicio se rechaza | `CANCELLED`, motivo no vacío, actor/hora/historia y liberación del turno |
| Reprogramación general | Solo administrador activo, con acuerdo previo del propietario | `REQUESTED` o `CONFIRMED` normalmente asignada | `ahora < inicio original`; para `REQUESTED`, también `ahora < corte original`. Destino desde mañana hasta 60 días según fecha local | Conserva estado, UUID, propietario y mascota; registra motivo y constancia del acuerdo; destino libre y plazo válido |
| Inasistencia | Solo administrador activo | `CONFIRMED` | `ahora >= fin original`; no al inicio ni con una tolerancia inventada | Estado de inasistencia, motivo no vacío y actor/hora/historia |

La reprogramación exige canal, fecha del contacto y nota del acuerdo, reutilizando la estructura existente: `PHONE`, `WHATSAPP` u `OTHER`, fecha ISO 8601 textual con offset explícito, no posterior al cambio, y nota no vacía. El acuerdo es constancia administrativa; el sistema no verifica conversaciones externas.

### 5.3 Decisiones técnicas propuestas para implementar esas reglas

- Representar inasistencia como **`NO_SHOW`**, terminal en este incremento. No se implementa corrección/reapertura ni transición a atención completada: clínica y sus reglas aún no existen. Un error administrativo necesitará un flujo auditado acordado posteriormente, sin sobrescribir historia.
- Cancelación e inasistencia conservan snapshots y versiones y no exigen que propietario, mascota o veterinario sigan activos para registrar el resultado administrativo; el administrador sí debe estar activo. Cancelar no se condiciona a resolver previamente `NEEDS_REASSIGNMENT`. No hay marcado automático de inasistencia: el administrador registra el hecho.
- Reprogramación general exige asignación `ASSIGNED`, participantes activos y mascota del propietario original. Si el veterinario está deshabilitado o la cita necesita reemplazo, se utiliza el flujo excepcional existente; no se eliminan sus guardas.
- El destino puede corresponder al mismo veterinario o a otro activo, dentro de turnos publicados de la sede. No se admite el mismo turno como una nueva reprogramación efectiva; un replay de una intención ya aplicada sí conserva su semántica.
- Cancelar o marcar `NO_SHOW` elimina la ocupación activa mediante el estado, sin borrar cita, turno, asignaciones ni historia. El turno pasado de una inasistencia no se ofrece para nuevas solicitudes.
- Para una `REQUESTED` reprogramada, comprobar primero que el corte original no venció; después calcular y persistir el corte del nuevo día con la configuración vigente. Rechazar un destino cuyo plazo ya no sea válido. Así no se resucita una solicitud vencida ni se impide todo cambio válido de fecha.
- Conservar límites existentes de motivo (1000 caracteres) y nota (500), tipos estrictos, normalización y errores de privacidad. Las decisiones rutinarias anteriores se documentan como diseño, no como funcionalidades implementadas.

## 6. Diseño previsto

### 6.1 API: existente frente a propuesta

La estructura se validó en `appointment/controller/AppointmentController.java`; no se presenta ninguna ruta nueva como existente.

| Recurso | Estado actual | Cambio previsto |
|---|---|---|
| `PATCH /api/v1/appointments/{appointmentId}/status` | Solo administrador; confirma/rechaza `REQUESTED` con `UpdateAppointmentStatusRequestDTO` | Ampliar destinos permitidos a cancelación manual y `NO_SHOW`; aplicar matriz de origen/plazo/motivo. Mantener confirmación/rechazo actuales |
| `POST /api/v1/appointments/{appointmentId}/reschedulings` | **Ruta propuesta, inexistente** | Registrar una reprogramación administrativa idempotente; seguir el patrón de recurso subordinado usado por `veterinarian-reassignments` |
| GET de agenda, detalle e historial | Existentes, filtrados por rol | Exponer estado/eventos nuevos sin datos adicionales privados; filtros deben admitir `NO_SHOW` |
| `POST .../veterinarian-reassignments` | Existente y limitado al veterinario no disponible | Conservar comportamiento; compartir solo auxiliares realmente comunes |

La ruta propuesta utilizaría **`RescheduleAppointmentRequestDTO`** (nombre nuevo propuesto), con `clientRequestId`, `availabilitySlotId`, `expectedAvailabilitySlotVersion`, `expectedVersion`, `reason`, `agreementChannel`, `agreementContactedAt` y `agreementNote`. La constancia será obligatoria para la operación general. Reutilizar `AppointmentResponseDTO`; respuesta propuesta `200` para aplicación/replay, privada y sin caché.

Reutilizar `APPOINTMENT_MANAGE`, exclusivo de administrador, para las tres acciones; no se necesita añadir permisos ni invalidar JWT por un nuevo conjunto de autoridades. Mantener comprobación de cuenta/rol/token vigente en cada petición y `401`, `403`, `404` conforme al contrato existente. Validaciones de cuerpo: `400`; conflictos de versión, estado, plazo, ocupación o clave: `409`, con códigos funcionales estables. Identificar códigos nuevos como propuestas y documentarlos desde Java antes de generar el snapshot.

### 6.2 Componentes y persistencia

| Archivo/componente existente | Trabajo futuro |
|---|---|
| `appointment/model/AppointmentStatus.java` | Añadir `NO_SHOW`; revisar usos de estados activos/terminales |
| `appointment/controller/AppointmentController.java` | Ampliar descripción de estado y documentar operación propuesta |
| `appointment/dto/UpdateAppointmentStatusRequestDTO.java` | Conservar compatibilidad; validación condicional del motivo en servicio y contrato |
| `appointment/dto/` | Añadir DTO propuesto y reutilizar `StrictAgreementContactedAtDeserializer` |
| `appointment/service/AppointmentService.java` | Transiciones, reprogramación, replay, ocupación, snapshots, eventos y constancia |
| `appointment/service/AppointmentEmailDispatcher.java` | Invalidar tareas antiguas de reasignación si un cambio de horario/estado las supera; conservar recuperación y tokens |
| `authentication/security/UserPermissionResolver.java` | Verificar permisos actuales; no ampliar rol propietario/veterinario/superadministrador |
| `shared/configuration/OpenApiConfiguration.java` | Completar enums/campos obligatorios/nulabilidad del historial generado |
| `src/main/resources/db/migration/` | Nueva migración, provisionalmente V16, verificando antes que siga libre |
| Pruebas HTTP, transaccionales, de correo y validador OpenAPI | Extender escenarios del apartado 8 |

Las citas usan `NamedParameterJdbcTemplate` directamente; **no existe `AppointmentRepository`**. Mantener ese modelo en este incremento. Extraer auxiliares pequeños para validación del acuerdo, movimiento atómico o eventos si evita duplicación; no emprender una reestructuración amplia.

Conservar **V1–V15**. La migración nueva ampliará `ck_appointment_status`, estados anterior/nuevo de los eventos y tipos de evento para cancelación humana, inasistencia y reprogramación; comprobar los nombres reales antes de escribirla. Se proponen eventos distinguibles `CANCELLED`, `NO_SHOW` y `RESCHEDULED`, preservando `DAILY_CUTOFF`/actor `SYSTEM`. Revisar CHECK y triggers para que esos cambios no vulneren snapshots ni historia inmutable.

Reutilizar **`appointment_operation_idempotency`**, ampliando `ck_appointment_operation_type` de `REASSIGNMENT` a incluir `RESCHEDULING`; no crear una segunda tabla de idempotencia. Mantener unicidad por cita/tipo/clave y fingerprint de la intención normalizada, incorporando actor para evitar que otro administrador use una clave ajena como replay. Evaluar esa protección para la nueva operación sin alterar inadvertidamente el contrato de reasignación anterior.

Conservar los índices únicos parciales de ocupación por turno y mascota/intervalo, aplicables a `REQUESTED`/`CONFIRMED`. Añadir `NO_SHOW` no lo convierte en estado ocupado ni exige eliminar snapshots. Si se modifica una restricción, justificarla y verificarla directamente en PostgreSQL.

### 6.3 Atomicidad, reintentos y avisos existentes

Una reprogramación efectiva guardará cambio de turno/fecha, versión, asignación histórica, evento, constancia e intención idempotente **en una sola transacción**. Si el destino está ocupado, cambió de versión o falla cualquier escritura, el origen permanece reservado y no se conserva una operación parcial.

Respetar el protocolo actual de bloqueos: cuentas/mascota conforme al servicio, cita antes de turnos, veterinarios y turnos en orden estable por UUID; contemplar los bloqueos de FK del historial. No invertir orden entre cancelación, corte, reprogramación, reasignación y deshabilitación del personal.

Cancelación/inasistencia reutilizarán `expectedVersion` y replay inmediato estricto: mismo actor, intención normalizada y evento humano aplicado a esa versión. Una cancelación por `DAILY_CUTOFF` no es replay de cancelación administrativa. Un reintento no agrega otra versión/evento ni libera otra reserva. Revalidar autenticación en todos los replays; diferenciar replay probado de una nueva operación fuera de plazo.

La invalidación de avisos pendientes se basará en el último evento relevante de horario/asignación/estado, **no en la versión global por sí sola**, pues consultar auditadamente el correo también incrementa versión. Una reprogramación posterior debe superar un aviso `OWNER_REASSIGNED` del horario anterior aunque continúe el mismo veterinario. Marcar tarea superada antes de SMTP o al recuperar el trabajo, respetando el token de adquisición; no modificar el resultado de un worker que ya perdió el arrendamiento.

Se conservan tabla durable, listener `AFTER_COMMIT`, sondeo, executor acotado y transacciones breves de `AppointmentEmailDispatcher`. SMTP no sucede dentro de la transacción de cita. Este alcance no añade tipos de correo P12 ni cambia proveedor. Un correo ya en tránsito no puede retirarse; no se promete entrega exactamente una vez ni ausencia absoluta de duplicados ante respuesta SMTP incierta.

## 7. Pasos de implementación futura

1. **Revalidar el punto de partida al recibir la autorización de implementar.** Comparar cambios nuevos con este plan y conservarlos. Resultado: baseline y alcance confirmados; no asumir que HEAD permanece igual.
2. **Preparar revisión de contrato P07 para ambos consumidores.** Mostrar reglas, `NO_SHOW`, operación propuesta, versión/replay y errores. Registrar observaciones y decisiones; mantener pendientes externos visibles si los compañeros no están disponibles.
3. **Diseñar y aplicar una migración nueva en pruebas.** Ampliar estados/eventos/tipo de operación sin editar migraciones aplicadas; conservar restricciones e inmutabilidad. Resultado: esquema consistente para todos los cambios P07.
4. **Implementar cancelación manual e inasistencia.** Ampliar operación de estado existente, matriz de transiciones, motivos, tiempos, versión y replay humano. Resultado: cambios administrativos auditados y ocupación correcta.
5. **Implementar reprogramación general.** DTO/ruta propuestos, acuerdo obligatorio, elegibilidad, ventana, cortes, reserva atómica, snapshots e idempotencia persistida. Resultado: cambio válido o conservación íntegra del origen ante fallo.
6. **Ajustar consultas e invalidación de avisos existentes.** Propagar estado/eventos nuevos; impedir avisos del horario anterior y conservar protección de adquisición/recuperación. Resultado: agenda/historial vigentes sin ampliar P12.
7. **Ejecutar pruebas pertinentes y regresiones.** Cubrir matriz del apartado 8 con PostgreSQL y HTTP reales; corregir hallazgos antes de generar evidencia final.
8. **Generar y validar contrato desde Java.** Validar documento y respuestas reales; copiar snapshot solamente al finalizar ambas verificaciones. Resultado: contrato propuesto 0.8.0 verificable.
9. **Actualizar documentación conforme se modifica cada recurso.** Completar guía de reglas, configuración pertinente, informe fechado y textos obsoletos. Resultado: trazabilidad coherente entre código, contrato, pruebas y Jira.
10. **Preparar entrega y seguimiento revisables.** Diff, resultados, limitaciones, criterios cubiertos y pendientes compartidos. Commit/push, mensajes a integrantes, cambios Jira y despliegue se realizarán solo con la autorización correspondiente; no están ejecutados ni se infieren de esta planificación.

El 11/10 es vencimiento registrado de SCRUM-43, no una promesa nueva de capacidad. Si no cabe todo P07, entregar avances identificados sin cerrar la subtarea o historia como si estuvieran completos; no recortar permisos, atomicidad, historia o pruebas de conflictos.

## 8. Verificación prevista

Las siguientes son pruebas **por realizar**, no resultados de esta revisión. Reutilizar `AppointmentHttpIntegrationTests`, `AppointmentTransactionIntegrationTests`, `AppointmentEmailDispatcherIntegrationTests` y las pruebas de disponibilidad/seguridad ya existentes. Las de concurrencia usarán conexiones PostgreSQL distintas y coordinación verificable, siguiendo las convenciones actuales.

| Escenario | Resultado que deberá comprobarse |
|---|---|
| Administrador cancela `REQUESTED` y `CONFIRMED` antes del inicio | Estado terminal, motivo/actor/evento únicos; turno libre y cita conservada |
| Cancelación un instante antes, en y después del inicio | Permitir antes; rechazar en/después sin escrituras |
| Inasistencia antes, en y después del fin | Rechazar antes; permitir en/después solo para `CONFIRMED`; no convertir a atención |
| Propietario, veterinario y superadministrador intentan gestionar | `403`; ausente/obsoleto/desactivado `401` conforme seguridad; ninguna alteración |
| Lecturas propias/asignadas y cita ajena | Aislamiento previo a paginación; respuestas e historial sin correo expuesto |
| Motivo vacío/espacios, exceso de longitud y JSON incorrecto | `400`, errores estables; no coerción de versión decimal/textual ni fecha epoch |
| Acuerdo faltante, vacío, canal inválido, fecha sin offset o futura | `400` y conservación completa del origen |
| Reprogramación `REQUESTED` y `CONFIRMED` | Conserva UUID/participantes/estado; destino y snapshots correctos; constancia persistida |
| Destino hoy, mañana, día 60 y día 61 | Hoy y 61 rechazados; mañana y 60 aceptables si cumplen el resto |
| Reprogramación en inicio original o con corte original vencido | Conflicto; no resucitar solicitud aunque el sondeo todavía no haya corrido |
| Destino ocupado/bloqueado, participante inactivo o versión obsoleta | Conflicto y rollback; origen, versión, eventos e idempotencia sin cambios parciales |
| Misma clave de reprogramación, intención igual/distinta u otro actor | Replay definido; conflicto para intención/actor incompatibles; un único evento efectivo |
| Repetición de transición con otro motivo/actor; cancelación SYSTEM | No reconocer replay falso; no duplicar eventos/versiones |
| Carreras cancelación-confirmación/corte/reprogramación y dos destinos concurrentes | Un único resultado efectivo por versión; no deadlock ni doble ocupación; perdedor con conflicto |
| Carrera contra deshabilitar/reactivar veterinario o nueva solicitud del destino | Guardas de cuentas/asignación consistentes; reserva final válida |
| Historia, snapshots y restricciones PostgreSQL | Inmutabilidad y ocupación activas verificadas directamente; migración nueva válida sin editar V1–V15 |
| Aviso pendiente del horario previo, acceso auditado al correo y nuevo cambio | Solo cambios de negocio relevantes superan avisos; auditoría de correo no invalida uno legítimo |
| Dispatcher: rollback, commit, recuperación y worker con token vencido | Conserva garantías históricas y no envía horarios/estados superados; ningún resultado sobrescrito por worker viejo |
| OpenAPI y regresiones | Enums completos, required/nulabilidad, respuestas/error reales, filtros y permiso existente; acceso, mascotas, personal y disponibilidad conservados |

Comprobaciones futuras desde el backend, con Java 25 y PostgreSQL de pruebas configurados:

- Ejecutar las pruebas focalizadas de los componentes modificados durante desarrollo y después **`.\mvnw.cmd verify`** para la evidencia final.
- Ejecutar **`& target/openapi-validation-env/Scripts/python.exe scripts/validate_generated_openapi.py`**, preparando sus dependencias según [la guía existente](openapi/README.md) si falta el entorno.
- Tras ambas comprobaciones satisfactorias, actualizar `docs/openapi/openapi.json` desde `target/generated-openapi/openapi.json`; nunca editar el JSON manualmente.
- Ejecutar `git diff --check` y revisar estadísticas/diff; registrar comandos, fecha, commit o estado del árbol, cantidad real de pruebas y resultados. No anticipar que el nuevo total será 431.

Las pruebas usarán identidades ficticias y esquemas aislados en `grownupsvet_test`; no migrar `grownupsvet_dev` como efecto de verificar el incremento. Una validación de SMTP remoto o buzón será evidencia distinta, cuando exista entorno autorizado; esta entrega backend no la presume.

## 9. Documentación y seguimiento futuro

Actualizar al implementar: `README.md`, `docs/citas-y-agenda.md`, `docs/openapi/README.md`, DTO/anotaciones Java, personalización OpenAPI y validador independiente; escribir un informe de verificación fechado con escenarios y límites. El documento académico padre de casos de prueba queda fuera del código backend y su actualización se coordinará aparte; CP-011 existente prueba reasignación excepcional, no reprogramación general, y CP-013–015 siguen siendo clínica prevista.

Corregir en ese trabajo autorizado los textos obsoletos localizados: `docs/personal-e-invitaciones.md:3` aún presenta citas aplazadas, `docs/mascotas-y-recuperacion.md:47` dice que el módulo no existe y `docs/arquitectura-usuarios-y-permisos.md:26` trata disponibilidad/confirmación como futuras. La anotación `VeterinarianAvailabilityController.java:84` dice que la protección por reservas se integrará, y aparece en el snapshot; corregir la fuente Java y regenerar posteriormente. Conservar como historia las secciones de contratos anteriores, incluida la antigua ventana de 2 horas de 0.6.0; el contrato actual usa desde mañana.

Seguimiento Jira propuesto para después de implementar, verificar y, si se autoriza, publicar:

| Issue | Evidencia/acción propuesta; no realizada hoy |
|---|---|
| SCRUM-43 | Registrar reglas, implementación, migración, commit/publicación y pruebas. Finalizado solo si todo su alcance backend y evidencia están cubiertos |
| SCRUM-81 | Registrar parte P07 publicada/revisada y pendientes P12. No cerrar completa por terminar este incremento |
| SCRUM-13 | Registrar avance BE y pendientes 44/45/46, integración y aceptación; no cerrar por finalizar 43 |
| SCRUM-44 | Revisar con Juan José título y criterios: propietario consultará resultados; los cambios quedaron exclusivos de administración. No editar ni implementar su portal aquí |
| SCRUM-79 / SCRUM-12 | Coordinar revisión pendiente P06 y aceptación/integración; no volver a implementar P06 |
| SCRUM-47 / SCRUM-15 | Mantener explícito avance parcial; acordar eventos, canal y momento del recordatorio para el siguiente plan |
| SCRUM-82 | Preparar después decisiones clínicas sin declarar listo su contrato por heredar P07 |

Los comentarios futuros deberán separar implementación, verificación automatizada, commit, publicación, revisión, integración y aceptación, enlazando evidencia concreta. No afirmar revisión de integrantes, recepción en buzón o despliegue sin su registro.

## 10. Riesgos, dependencias y decisiones pendientes

| Riesgo/dependencia | Tratamiento previsto |
|---|---|
| Plazo corto y varios frentes a cargo de Esteban | P07 como incremento coherente; P12 y clínica separados. No comprometer fechas nuevas sin capacidad |
| Consumidores con estado `NO_SHOW` o nueva ruta desconocidos | Contrato concreto y revisión; consumidores deben manejar nuevos enums y conflictos de versión |
| `REQUESTED` vencida todavía no procesada por el sondeo | Validar corte guardado dentro de cada operación; no basarse solo en el estado |
| Deadlock por orden de FK/cita/turnos | Conservar protocolo existente y ejercer carreras reales, no solo mocks |
| Avisos antiguos tras reprogramación general | Revalidar evento de negocio/horario y `SUPERSEDED`; conservar arrendamientos |
| Modificar migración ya aplicada o perder historial | Nueva migración y pruebas PostgreSQL de restricciones/inmutabilidad |
| Cierre prematuro de 81/13/15 | Criterios explícitos; contrato P12, portales y revisión permanecen separados |
| Texto Jira 44 contradictorio con permiso aprobado | Registrar ajuste propuesto para coordinación, sin modificar tarea ajena durante revisión |
| Hora de jornada del entorno real | Configuración externa obligatoria; no adoptar `00:00` de pruebas como acuerdo comercial |

**No quedan decisiones de negocio indispensables pendientes para planificar P07:** Esteban respondió actores, estados, plazos, acuerdo y ventana del destino. `NO_SHOW`, nombres nuevos, estrategia de persistencia y separación de P12 son propuestas técnicas de este documento, todavía por implementar y revisar en el contrato.

Para un futuro incremento P12 habrá que definir eventos/destinatarios/canal, anticipación del recordatorio, tratamiento de destinatarios desactivados y evidencia de entrega. Para clínica habrá que acordar permisos/visibilidad, borradores, finalización y correcciones. Esas preguntas no bloquean este plan P07 ni autorizan decidirlas silenciosamente.

## 11. Condiciones de cierre y parada

El **incremento backend P07** estará completo cuando las tres operaciones respeten las reglas confirmadas, mantengan reserva/historia/idempotencia/permisos, invaliden avisos superados, pasen las verificaciones pertinentes y queden documentadas con el contrato generado y evidencia actual. La publicación, si se autoriza, deberá comprobarse en el remoto y registrarse aparte.

La **historia P07 / SCRUM-13** requerirá además integración de ambos portales según roles, errores/confirmaciones comprensibles, verificación compartida SCRUM-46 y aceptación. El contrato SCRUM-81 completo requerirá además P12 y la revisión registrada de los tres; no puede cerrarse con P07 solamente. SMTP externo, buzón y despliegue tienen condiciones/evidencias propias.

**Esta sesión se detiene al entregar el plan.** Solo se realizó revisión y planificación; las verificaciones descritas se ejecutarán durante una implementación autorizada posteriormente. Ninguna respuesta de reglas ni el cierre del documento inicia esa implementación.
