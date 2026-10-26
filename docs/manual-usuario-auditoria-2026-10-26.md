# Manual de usuario del alcance parcial de auditoría

**Corte de hoy: 26 de octubre de 2026, America/Bogota.** Se documentan P1/P2
web y P3 API publicado/verificado. No es la entrega final completa; la
auditoría integral del proyecto debe esperar la finalización de la versión
final. Este manual describe operaciones implementadas, no sesiones de
aceptación realizadas por personas usuarias.

Complementa el [expediente de auditoría](expediente-auditoria-2026-10-26.md).
Las reglas detalladas y las evidencias conservan sus documentos propios;
no se sustituyen los resultados históricos ni se asignan aprobaciones.

## Acceso y preparación

Abrir [GrownupsVet](https://grownupsvet-frontend.pages.dev/) desde el navegador.
Los cuatro roles utilizan el mismo sitio y formulario **Iniciar sesión**.
Introducir correo y contraseña propios. El sistema determina el área por el
rol y los permisos de la cuenta; no se elige un rol durante el acceso.

| Cuenta | Área inicial | Trabajo disponible en esta web |
|---|---|---|
| Propietario (`OWNER`) | Mascotas | Mascotas propias, solicitud/consulta de citas, avisos y perfil. |
| Administrador (`ADMINISTRATOR`) | Administración | Veterinarios, formación/diplomas, disponibilidad y gestión de citas según permisos. |
| Veterinario (`VETERINARIAN`) | Mi agenda | Consulta de citas asignadas por día. |
| Superadministrador (`SUPER_ADMIN`) | Administradores | Gestión de cuentas de administradores. |

La cuenta debe estar activa. El personal recibe su acceso mediante
invitación; el registro público **Crear cuenta de propietario** no crea
personal. En una demostración se utilizan cuentas y recursos ficticios
autorizados y las credenciales se entregan por un canal privado.

Si la sesión no puede comprobarse por un problema de conexión, utilizar la
opción de reintento o cerrar sesión. La aplicación mantiene un estado de
verificación y no permite continuar mostrando datos privados sin validarla.
Al terminar, utilizar **Cerrar sesión**, especialmente en un computador
compartido. Cambiar de cuenta no debe conservar recursos de la anterior.

## Propietario

### Crear la cuenta y recuperar el acceso

1. En **Crear cuenta de propietario**, diligenciar nombre completo, correo,
   fecha de nacimiento, teléfono y contraseña/confirmación. La ayuda indica
   nacimiento `AAAA-MM-DD` y teléfono con `+` y código de país, sin espacios.
2. Corregir los campos señalados y enviar una vez. Tras el éxito, utilizar
   la opción para iniciar sesión; el registro no inicia sesión por sí solo.
3. Si olvidó la contraseña, abrir **Olvidé mi contraseña** y seguir los tres
   pasos visibles: solicitud, verificación del código y contraseña nueva.
   Respetar el vencimiento indicado y no compartir códigos o enlaces.
4. Si el resultado de un registro o cambio de contraseña es incierto,
   seguir el mensaje de la pantalla para comprobar el acceso o contactar a
   la clínica. No asumir éxito ni repetir altas sin comprobar.

### Consultar y gestionar mascotas

1. Abrir la pestaña **Mascotas** y consultar activas o archivadas.
2. Abrir una mascota propia para consultar sus datos o utilizar la acción
   de creación/edición disponible. Corregir los mensajes del formulario antes
   de guardar y consultar nuevamente el resultado.
3. Para retirar una mascota del uso habitual, archivar mediante su
   confirmación. Archivar conserva el registro y sus antecedentes; no es una
   eliminación definitiva. La reactivación recupera la misma mascota.
4. Si existen más resultados, recorrer la paginación o la opción de cargar
   más disponible. La aplicación no concede acceso a mascotas de otra cuenta.

### Solicitar y consultar citas

1. Abrir **Citas** y la acción de solicitud. Elegir una mascota activa,
   horario disponible y motivo, y revisar la confirmación antes de enviar.
2. Los turnos duran 30 minutos. Para propietarios, la ventana va desde
   mañana hasta 60 días, según el calendario de Bogotá; no significa esperar
   24 horas completas desde la solicitud.
3. Consultar la cita creada. `REQUESTED` significa solicitada, no confirmada.
   La administración decide su confirmación o rechazo.
4. Abrir el detalle para consultar horario/estado vigentes y **Ver avisos de
   esta cita**. Un aviso aceptado por SMTP no confirma lectura del correo.
5. Para cancelar o reprogramar, contactar a la clínica. Este corte no ofrece
   cancelación/reprogramación autónomas del propietario. Consultar el detalle
   después de un cambio gestionado por la administración.

La web reconoce `COMPLETED` cuando la API completa una cita por finalización
clínica. Mostrar ese estado no implica que haya una pantalla de historia o
fórmula clínica implementada.

### Perfil y fotografía

En **Perfil**, consultar los datos de la cuenta. **Cambiar teléfono** permite
editar el teléfono y **Guardar** confirma la operación; los demás datos del
registro no tienen edición propia en este corte. Utilizar las acciones de
foto para escoger, procesar, guardar o quitar una imagen. Cancelar el
selector no solicita un reemplazo. La fotografía es opcional y se consulta
como recurso protegido.

**Desactivar mi cuenta** requiere confirmación y retira el acceso de la
cuenta. Leer sus consecuencias antes de confirmar; el formulario de
recuperación no debe interpretarse como reactivación de una cuenta
deshabilitada.

## Administrador

### Veterinarios y cuentas

1. En **Administración**, abrir **Veterinarios**. Filtrar por estado,
   recorrer las páginas y abrir **Ver detalle de …**.
2. **Crear veterinario** registra una cuenta pendiente de activación y su
   información profesional. Correo y rol no se cambian después mediante
   edición; la matrícula registrada no acredita verificación profesional
   externa.
3. En el detalle, **Editar datos** permite los campos autorizados. Las
   opciones de habilitar/deshabilitar y reenviar/cancelar invitación muestran
   su confirmación y consecuencias. Solicitar una invitación no acredita su
   recepción en el buzón.
4. Deshabilitar un veterinario puede dejar citas pendientes de reasignación;
   no equivale a cancelarlas automáticamente. Revisar la agenda y el estado
   de asignación antes de gestionar esos casos.

### Formación y diplomas

Desde el detalle del veterinario, abrir **Gestionar formación y diplomas**.
Se pueden consultar y agregar títulos, editar los permitidos y gestionar
sus PDF. El pregrado base es obligatorio; no se elimina como un título
adicional. Se admiten hasta 20 títulos por veterinario.

Para publicar un diploma, preparar su descarga, descargarlo, revisar el
archivo y confirmar esa revisión antes de confirmar la publicación. El PDF
es privado durante su preparación. Editar datos académicos retira la
publicación sin eliminar el archivo; eliminar el PDF conserva el título.
Ante un resultado incierto, consultar la formación actual antes de decidir
otro envío. La consulta por sí sola no identifica qué intento creó un título.

### Disponibilidad

1. En el detalle, abrir **Gestionar disponibilidad**. Consultar la fecha y
   el estado; los horarios se muestran en Bogotá.
2. **Publicar un turno** registra un turno individual de 30 minutos. Abrir
   su detalle permite las modificaciones/estados e historial disponibles;
   edición y cambio de estado requieren motivo y confirmación.
3. **Generar lote de turnos** permite revisar fechas, días de semana y una
   franja antes de confirmar. El rango admite hasta 31 fechas inclusivas y
   1000 turnos; el fin diario es exclusivo y puede ser `24:00`.
4. Una colisión rechaza el lote completo, incluso con un turno bloqueado.
   Un resultado incierto bloquea otro envío y ofrece consultar todas las
   fechas/páginas. Encontrar turnos muestra la agenda vigente, no prueba la
   procedencia de un intento anterior.

### Citas y avisos

En **Citas**, consultar un rango de hasta 31 fechas inclusivas y su estado,
recorrer páginas y abrir el detalle. Las acciones dependen del permiso y
del estado: **Confirmar cita**, revisar rechazo/cancelación, registrar
inasistencia al terminar el turno, **Reprogramar cita** o **Resolver falta
de veterinario** cuando corresponda.

El rechazo/cancelación/inasistencia requieren motivo. Cambiar el horario
requiere registrar el acuerdo previo cuando el contrato lo exige; reprogramar
siempre lo requiere. Revisar destino, fecha/hora de Bogotá y confirmación.
El servidor comprueba ocupación y versiones: un candidato visible no reserva
por sí solo el turno. Si hay conflicto o respuesta incierta, utilizar
**Consultar detalle e historial actuales** antes de otra decisión.

**Ver avisos de la cita** permite consultar programación, intentos y estado;
no reenvía correos. Los permisos administrativos de citas no dan acceso al
contenido clínico ni permiten completar/reabrir una atención clínica.

## Veterinario

En **Mi agenda**, utilizar **Día anterior**, **Hoy**, **Día siguiente** y
**Actualizar agenda**. Revisar las citas asignadas, estado y horario de
Bogotá, y recorrer sus páginas. Una lista vacía corresponde al día consultado;
no demuestra ausencia de citas en otras fechas.

La interfaz de este rol es de consulta. No hay en este corte un formulario
web para registrar/finalizar atención, fórmula o controles. La API clínica
sí dispone de esas operaciones para el veterinario autorizado; el recorrido
técnico separado se describe más abajo.

## Superadministrador

En **Administradores**, filtrar/recorrer el listado y abrir una cuenta.
**Crear administrador** prepara una cuenta pendiente de activación.
**Editar datos**, habilitar/deshabilitar y reenviar/cancelar invitación
requieren los permisos y confirmaciones correspondientes. Correo y rol son
inmutables en la edición. La invitación requiere que el entorno tenga su
canal y URL de activación configurados.

Este rol administra administradores; no hereda la gestión de veterinarios,
agenda o clínica. Si necesita demostrar otra área, cerrar sesión e ingresar
con la cuenta autorizada del rol correspondiente, sin modificar permisos.

## Clínica: recorrido API separado de la web

La [documentación interactiva](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html)
y el [contrato clínico](atenciones-clinicas.md) describen las operaciones
implementadas. Para una demostración técnica, usar exclusivamente JWT nuevos
de las cuentas de ensayo autorizadas y no copiar credenciales a capturas.

El veterinario vigente crea/actualiza el borrador y tratamiento de su cita
confirmada. Solo puede finalizar desde el inicio real del turno, con los
cinco textos obligatorios y versiones vigentes; finalización y `COMPLETED`
son atómicos. El propietario solo consulta historia publicada de sus
mascotas, incluidas archivadas; no accede a borradores. Los administradores
no reciben contenido clínico. Atenciones finalizadas e hijos son inmutables;
las adendas están fuera de alcance.

Seguir el [plan de aceptación API](plan-aceptacion-desplegada-p1-p3-2026-10-25.md)
con sus barreras temporales y presupuesto de correo. Este apartado no es
instrucción para eludirlas ni evidencia de haber ejecutado el recorrido.

## Mensajes y resultados que requieren atención

| Situación | Cómo continuar |
|---|---|
| Datos inválidos | Leer el campo indicado, corregirlo y revisar antes de enviar. |
| Credenciales rechazadas | Revisar correo/contraseña o usar recuperación; el mensaje no confirma existencia de una cuenta. |
| Demasiados intentos (`429`, al habilitar el control nuevo) | Respetar la espera informada. El cliente adapta `Retry-After` sin repetir credenciales automáticamente. Su activación desplegada se acredita en el cierre del incremento, no por este manual. |
| Sesión vencida/revocada | Volver a iniciar sesión. No sustituir tokens ni intentar acceder como otro rol. |
| Turno ocupado o versión desactualizada | Consultar datos actuales; no asumir que el cambio rechazado liberó la reserva previa. |
| Respuesta incierta | Seguir la reconciliación ofrecida. No interpretar la lectura posterior como identificación del intento que escribió. |
| Aviso `SENT` | Significa aceptación por el servidor SMTP; la recepción y lectura son evidencias distintas. |

## Fuentes y revisión posterior

Las rutas/etiquetas se contrastaron con el [frontend remoto de partida](https://github.com/Estebangmz666/grownupsvet-frontend/tree/492c71fc152ddeda55e34df808d0f132cd361dc7),
`RootNavigator`, `StaffArea`, las pantallas de propietario/administración y
los cambios verificables del incremento [PR frontend #3](https://github.com/Estebangmz666/grownupsvet-frontend/pull/3).
La fuente de permisos es [UserPermissionResolver](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/authentication/security/UserPermissionResolver.java).
Consultar [personal](personal-e-invitaciones.md), [disponibilidad](disponibilidad-veterinaria.md),
[citas](citas-y-agenda.md) y [clínica](atenciones-clinicas.md) para reglas completas.

El cierre debe añadir SHA publicados y revisión/digest realmente usados.
Un integrante distinto debe seguir este manual y registrar fecha, versión,
rol, tarea, resultado y obstáculos. La reproducción, la revisión con lector
de pantalla y las sesiones con personas mayores permanecen pendientes hasta
que ocurran; no se acreditan por redactar este documento.
