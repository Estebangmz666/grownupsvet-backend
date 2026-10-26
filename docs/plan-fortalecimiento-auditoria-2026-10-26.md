# Plan de fortalecimiento y preparación de auditoría de GrownupsVet

**Fecha:** 26 de octubre de 2026, America/Bogota.

**Estado:** aprobado explícitamente por Esteban el 26/10/2026; ejecución y evidencias en el expediente parcial.

**Ventana de auditoría comunicada:** del 26 de octubre al 3 de noviembre de 2026, sin horario confirmado.

La aprobación habilitó los incrementos descritos y sus actualizaciones de seguimiento. Este archivo conserva la planificación original; los resultados, límites y bloqueos de ejecución se registran por separado en el expediente. El usuario precisó posteriormente una política de **5 intentos por correo y peer cada 15 minutos**, con 60 por peer/minuto y clave HMAC aleatoria independiente de 32 bytes. La activación requiere evidencia remota; su autorización no demuestra que Secret Manager o el despliegue hayan quedado completados.

**Corte y alcance:** 26 de octubre de 2026, America/Bogota. La auditoría es parcial sobre P1/P2 web y P3 API publicados y comprobados según su evidencia. Para auditar el proyecto completo debe esperarse a que el equipo termine y entregue la versión final. Los pendientes dentro del alcance actual se mantienen visibles.

## 1. Objetivo y criterio de trabajo

Conseguir que el equipo pueda demostrar lo que funciona, localizar sus soportes y explicar con precisión sus limitaciones. Corregir primero las debilidades que comprometen seguridad, conservación de evidencia o recorridos esenciales. Mantener un paquete defendible al terminar cada incremento, porque la visita puede ocurrir antes del 3 de noviembre.

El principal problema encontrado es la distancia entre producto publicado, documentación y aceptación registrada. Hay mecanismos y pruebas importantes ya construidos; el trabajo consiste en acreditar sus límites, completar validaciones y corregir riesgos concretos. No se propone reescribir el proyecto ni cambiar su stack.

Cada cierre distinguirá cinco estados: implementado, verificado técnicamente, publicado, comprobado en el entorno desplegado y aceptado por sus usuarios/revisores. Un estado no concede automáticamente los siguientes.

## 2. Base verificable y límites del alcance

| Fuente | Corte utilizado | Qué permite planificar |
| --- | --- | --- |
| Backend oficial | [main, 736805879fcb6ffe968242d0647439fbe0ddf3f6](https://github.com/Estebangmz666/grownupsvet-backend/tree/736805879fcb6ffe968242d0647439fbe0ddf3f6), comprobado con fetch y referencia remota | P1/P2 y API clínica P3; Flyway V1–V22, CI, respaldo y observabilidad publicados. |
| Frontend oficial | [main, 492c71fc152ddeda55e34df808d0f132cd361dc7](https://github.com/Estebangmz666/grownupsvet-frontend/tree/492c71fc152ddeda55e34df808d0f132cd361dc7), confirmado en GitHub | Web P1/P2 por rol, formación/diplomas y compatibilidad con citas COMPLETED. Repositorio público tras el cambio autorizado. |
| API desplegada | [OpenAPI de Cloud Run](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs), HTTP 200, versión 0.10.0, 47 rutas en la revisión preliminar | Las rutas clínicas existen en el entorno público; su existencia no acredita aceptación completa. |
| Frontend publicado | [Cloudflare Pages](https://grownupsvet-frontend.pages.dev/), HTTP 200 en la revisión preliminar | URL para los recorridos web. Identificar nuevamente su despliegue exacto al ejecutar. |
| Jira | Proyecto SCRUM consultado en vivo el 26/10; 83 incidencias devueltas, SCRUM-6 a SCRUM-88 | Estados, responsables y descripciones actuales para preparar su reconciliación. |
| TestRail | Copia exportada/documentada del 14–15/10: S1, 84 casos; R1 histórica 54 Passed/30 Blocked | Base provisional para detectar reutilización. El servidor actual pidió iniciar sesión: sus casos y resultados actuales se verificarán antes de editar. |
| Documentos académicos | Guía de auditoría y copias locales disponibles | Preparar matriz documental. Las versiones actuales de Drive deben leerse antes de reemplazar o editar documentos. |

Las modificaciones locales sin publicar de ambos repositorios quedan fuera de las afirmaciones sobre el producto remoto. No se integrarán como efecto secundario del plan. La carpeta frontend anidada en GrownupsVet apunta al repositorio antiguo de Juan José; la entrega debe identificar explícitamente el repositorio oficial de Esteban y el SHA evaluado.

**Incluido:** funcionalidades publicadas de P1/P2, API P3 publicada, documentación, Jira, catálogo y ejecuciones TestRail, controles de seguridad, CI, base de evaluación y procedimientos operativos necesarios para defenderlas.

**Límites funcionales:** la interfaz clínica P3 sigue pendiente y no se incorpora en este plan. Sus recorridos de usuario permanecerán pendientes. Adendas P11, cancelación/reprogramación autónoma del propietario y desarrollo nativo Android/iOS quedan fuera. La PWA se evaluará solo en plataformas compatibles y según su implementación actual, sin prometer operaciones privadas offline.

Los casos de funcionalidades nuevas que produzca una corrección —por ejemplo, la limitación de login— entrarán a la ejecución de aceptación únicamente cuando esa corrección esté publicada y se identifique su nueva versión.

## 3. Orden de ejecución y esfuerzo orientativo

Los tiempos son horas efectivas estimadas, no compromisos de disponibilidad ni fechas docentes. Los recorridos con reloj real y la participación de usuarios pueden alargar el calendario. El total orientativo es **32–56 horas**, antes de defectos nuevos y esperas externas.

| Incremento | Prioridad y motivo | Esfuerzo | Dependencia | Resultado revisable |
| --- | --- | --- | --- | --- |
| I0. Corte y conservación | Urgente: evitar pérdida de evidencia y confusión de versión | 1–2 h | Aprobación | Índice, manifiesto y evidencia conservada. |
| I1. Documentación y seguimiento inicial | Alta: el auditor compara producto, documentos y Jira | 4–6 h | I0 | Documentación vigente identificada, matriz de brechas y actualización justificada de Jira. |
| I2. CI y protección de frontend | Alta: conservar evidencia y evitar integrar cambios fallidos | 2–4 h | I0 | Retención ampliada, política de integración y prueba de bloqueo. |
| I3. Base de evaluación y reproducción | Alta: permitir revisar estructura, datos e integridad sin depender de producción | 4–6 h | I0 | Modelo de datos, base aislada y guía seguida por otro integrante. |
| I4. Limitación de login | Alta: corregir un riesgo real de intentos automatizados | 8–14 h | I3 o entorno de pruebas seguro ya disponible; política aprobada | Control persistente, contrato, interfaz, pruebas y publicación verificados. |
| I5. Aceptación integrada y métricas | Alta: demostrar recorridos sobre la versión real | 4–8 h, más esperas | I0/I3 y versión estable; repetir los accesos afectados tras I4 | Resultados P1/P2 web y P3 API, persistencia, correo y mediciones con límites explícitos. |
| I6. Accesibilidad y personas mayores | Alta por el público objetivo | 3–6 h, más coordinación | Recorridos estables de I5 | Sesiones y revisión técnica; incidencias y repetición de tareas afectadas. |
| I7. Operación y recuperación | Media-alta: evitar depender de conocimiento individual | 3–5 h | I0; coordinación con I5 | Procedimientos actuales, configuración reproducible y ensayo de recuperación. |
| I8. Cierre de TestRail, Jira y expediente | Alta para la defensa | 3–5 h | Resultados de cada incremento | Trazabilidad, resultados y guion de defensa comprobados por el equipo. |

**Primer paquete utilizable:** comenzar con I0 y una versión breve de I1: portada de estado, enlaces, fortalezas, pendientes y guion P1/P2. Completarlo antes de las correcciones mayores. I2, la revisión documental y el diseño de casos pueden avanzar en paralelo después de aprobar. Actualizar el expediente después de cada incremento; I8 consolida, no posterga toda la evidencia hasta el final.

## 4. Incrementos: por qué, cómo y cuándo se consideran cerrados

### I0. Fijar la versión y preservar la evidencia que puede vencer

**Por qué:** un resultado antiguo o asociado a otra rama puede ser correcto y aun así no demostrar el estado auditado. Ambos workflows conservan artefactos siete días; los del 25/10 podrían vencer el 1/11.

**Cómo:**

1. Volver a consultar repositorios, ramas, SHAs, checks y despliegues; registrar diferencias respecto a este plan sin incorporar cambios locales automáticamente.
2. Descargar y revisar los reportes y artefactos relevantes mientras existan. Conservar solo evidencia saneada: resultado, fecha Bogotá, commit, run, versión, entorno y hash del archivo.
3. Crear un índice con las secciones 6.1–6.11 de la guía y el enlace al soporte vigente. Registrar los faltantes y su responsable propuesto.
4. Añadir un guion corto por rol y una lista de restricciones conocidas. Mantener las credenciales de demostración en un canal privado separado.

**Cierre:** otro integrante abre los enlaces y localiza pruebas, contrato, despliegue, modelo de datos y restauración sin depender de explicaciones verbales. Cambiar retención futura no se presenta como recuperación de artefactos ya vencidos.

### I1. Actualizar documentos vivos y reconciliar Jira

**Por qué:** hoy hay documentos que todavía describen Sandbox, backend 0.9, CI pendiente o clínica inexistente. Jira también conserva esas descripciones y tareas publicadas como Por iniciar. Esto afecta gestión, requisitos, diseño, documentación y pruebas.

**Cómo:** identificar primero la versión canónica en repositorio, Drive y Jira; comparar sus contenidos y actualizar la más reciente. Conservar los informes fechados como evidencia histórica. Publicar un estado vigente que enlace esos cortes, en vez de reescribir resultados anteriores.

| Documento o registro | Cambio previsto | Comprobación de cierre |
| --- | --- | --- |
| Estado del proyecto e índice de auditoría | Fecha, SHAs, despliegues, alcance por rol, pruebas y pendientes separados | Cada afirmación tiene fuente; P3 API e interfaz clínica aparecen diferenciadas. |
| Manual de usuario | Recorridos web reales P1/P2: acceso, activación, recuperación, mascotas, personal, disponibilidad, citas, avisos y diplomas | Un integrante sigue cada procedimiento; controles y capturas coinciden con el sitio. P3 API tiene anexo técnico, sin pantallas inventadas. |
| C4, BPMN y modelo de datos | Gateway/Cloudflare Pages, Cloud Run, PostgreSQL Render, Gmail y monitoreo local; componentes y relaciones actuales | Diagramas, texto y versión publicada concuerdan. Distinguir capacidades implementadas y futuras. |
| RNF y Plan de Calidad | Identificar catálogo aprobado, mantener IDs y actualizar evidencia/estado; resolver la diferencia histórica de codificación M1–M9 mediante referencia o equivalencia | Se utiliza la definición aprobada de octubre; no se renumeran requisitos ni se cambian metas para aprobar. |
| OpenAPI e integración | Identificar backend/docs/openapi como fuente generada actual; aclarar la copia histórica de raíz que aún describe 0.1.0 | Java, snapshot y contrato desplegado concuerdan para la versión evaluada; JSON generado desde Java. |
| Instalación, CI y despliegue | Repositorio oficial, requisitos, variables sin secretos, gateway, build, pruebas, configuración y verificación posterior | Un segundo integrante reproduce el arranque y consulta el entorno correcto. |
| Seguridad y operación | Limitaciones de login, sesión, permisos, observabilidad, respaldo y recuperación | Procedimientos corresponden al comportamiento real; mejoras propuestas no figuran como implementadas. |
| Documentos de pruebas | Vincular el catálogo, la ejecución vigente y el informe de resultados; conservar R1 histórica | No se presenta 54/84 como porcentaje actual ni como cobertura de código. |
| Gestión | Cronograma vigente, responsables, riesgos, decisiones, revisión de contratos y cambios de alcance | Jira enlaza evidencia y conserva lo que falta aceptar. Revisar el soporte del tratamiento de datos mencionado en SCRUM-6; si falta, registrarlo como decisión pendiente, sin inventar una autorización. |

**Cierre:** documentos vivos coherentes con el producto y una matriz requisito → Jira → componente/pantalla/operación → caso → resultado → evidencia → versión. La guía de cambios de Jira está en la sección 5.

### I2. Conservar diagnósticos y exigir CI en frontend

**Por qué:** tener un workflow no equivale a impedir una integración fallida. La rama main del frontend devuelve protected:false en la consulta realizada para este plan. El backend ya tiene evidencia de protección y de un fallo controlado bloqueado: se verificará y reutilizará.

**Cómo:**

1. Propuesta: pasar la retención de artefactos de ambos repositorios a **30 días**, si la política de GitHub lo permite, y conservar aparte el expediente académico saneado.
2. Confirmar el nombre del check del frontend —actualmente Tipos, lint y pruebas— y exigirlo mediante PR a main. Mantener una política compatible con el equipo: sin force push ni eliminación de main, rama actualizada y aplicación a administradores. Cero aprobaciones humanas permite el flujo individual; no se presentará como revisión por pares obligatoria.
3. Verificar la protección vigente del backend antes de cambiarla; no volver a configurarla por defecto si ya cumple.
4. Demostrar en una rama de ensayo un CI satisfactorio y un fallo controlado que bloquee integrar, sin mezclar código defectuoso con una entrega real.
5. Documentar que Cloudflare Pages ejecuta sus propias verificaciones y que una URL publicada requiere su comprobación específica.

**Cierre:** nuevo run con retención acordada, política efectiva consultable, PR de prueba bloqueado por el check fallido y main preservada. No escribir un test que solo compare la constante de retención del YAML.

### I3. Entregar una base de evaluación aislada

**Por qué:** Flyway permite reconstruir el esquema, pero la auditoría también evalúa relaciones, restricciones, datos de ejemplo, accesos y recuperación. Una base aislada facilita comprobarlos sin compartir el contenido de producción.

**Cómo:**

1. Elaborar diagrama entidad–relación y diccionario breve a partir de las migraciones y entidades publicadas: claves, relaciones, restricciones, índices y estados relevantes.
2. Preparar PostgreSQL 18 con una base exclusiva de evaluación, migraciones del SHA elegido y datos ficticios coherentes con los cuatro roles. Mantener el correo externo deshabilitado en esa base.
3. Reutilizar el helper de demostración P1/P2 donde aplique; documentar sus cuentas profesionales previas y límites. Preparar fixtures clínicos mediante los flujos existentes cuando se evalúe P3 API.
4. Proporcionar al auditor un rol de lectura sobre el conjunto necesario. Para probar escrituras o restricciones, usar otra base desechable con permisos apropiados.
5. Mantener separadas la base de evaluación, la base exclusiva de pruebas automatizadas y producción. Reutilizar las guardas existentes; parametrizar pruebas solo si un impedimento concreto de reproducción lo requiere.
6. Adjuntar evidencia saneada de migraciones y del respaldo/restauración ya realizados. Una nueva restauración se hará únicamente sobre una base nueva aislada.

**Cierre:** otro integrante levanta el sistema o inspecciona el esquema, consulta ejemplos y explica las restricciones. No necesita contraseñas de producción, dumps sin sanear ni los archivos privados del computador de Esteban.

### I4. Limitar intentos de autenticación

**Por qué:** UserAuthenticationService autentica directamente y la documentación reconoce que la limitación se aplazó. Argon2id protege contraseñas almacenadas, pero no limita intentos automatizados ni su consumo de CPU.

**Diseño propuesto:** mantener POST /api/v1/auth/sessions y añadir admisión persistente en PostgreSQL, con contadores atómicos, vencimiento y limpieza. Reutilizar conceptos del control de recuperación manteniendo su configuración y responsabilidades separadas. PostgreSQL ya está disponible y evita introducir Redis o un servicio nuevo.

La política inicial propuesta para aprobar con el plan es **10 intentos en 15 minutos por combinación de identificador normalizado y origen confiable, y 60 por minuto por origen**. Son valores configurables iniciales del proyecto, no umbrales certificados. Contar admisiones de forma coherente para cuentas existentes, inexistentes e inactivas; no desactivar cuentas por intentos ajenos ni aplicar un bloqueo permanente por correo. Medir su efecto sobre usuarios legítimos y redes compartidas. Documentar que este control no elimina todos los ataques distribuidos.

**Pasos:**

1. Acreditar la procedencia del cliente en Cloudflare/Cloud Run y el acceso directo a la API. No confiar en cabeceras reenviadas arbitrarias. Si no puede acreditarse un origen fiable, detener únicamente esa parte del diseño y presentar la limitación antes de activar límites basados en IP.
2. Guardar identificadores derivados mediante HMAC cuando corresponda, sin correo o contraseña en las claves de contadores ni logs. Gestionar su clave como secreto externo.
3. Confirmar la transacción corta de admisión antes de Argon2; no mantener bloqueos de BD mientras se calcula el hash ni perder contadores porque después se rechace una contraseña.
4. Crear la siguiente migración disponible; previsiblemente V23, si sigue libre al implementar. Conservar V1–V22 y actualizar el inventario estricto de migraciones probado.
5. Responder con 429, Retry-After y Problem Details documentados, sin revelar existencia de cuenta. Adaptar el cliente para explicar la espera en español, conservar lo diligenciado y evitar reintentos automáticos.
6. Regenerar OpenAPI, ejecutar pruebas focalizadas y regresión pertinente, publicar por el flujo protegido y comprobar el resultado desplegado con una cuenta de ensayo.

**Verificación significativa:** umbrales y expiración, solicitud límite y siguiente, concurrencia, persistencia tras reinicio y almacén compartido, cabeceras falsas, cuentas conocidas/desconocidas/inactivas, aislamiento de otros usuarios y conservación de roles/revocación. Las ráfagas se prueban en el entorno aislado, no contra cuentas de compañeros.

**Cierre:** control e interfaz publicados, resultados técnicos y de aceptación asociados a la nueva versión, contrato actualizado y política documentada. Esta decisión sigue la orientación de [OWASP sobre throttling y riesgo de denegar acceso mediante bloqueos](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html); los valores concretos son una propuesta propia.

### I5. Completar aceptación integrada y mediciones

**Por qué:** mocks, tests de helpers y resultados backend no acreditan automáticamente que un usuario termine la tarea en la URL publicada. Grafana tampoco demuestra por sí solo las metas académicas.

**Cómo:**

1. Identificar qué evidencia ya existe y comprobar qué parte del harness/checkpoint de aceptación puede reutilizarse. Una ubicación privada no se considera prueba ausente ni resultado aprobado sin revisar su reporte saneado.
2. Definir antes de ejecutar cuentas, recursos ficticios, destinatarios autorizados, casos elegibles, negativos, oráculos y versión. Registrar cada intento, error y timeout sin eliminarlos por un reintento posterior.
3. Recorrer P1/P2 desde la web por rol: registro/acceso/perfil/foto, mascotas, recuperación/activación, personal/diplomas, disponibilidad, solicitud/confirmación, cambios e historial/avisos. Comprobar propiedad y permisos negativos.
4. Evaluar P3 mediante la API ya publicada: borrador, tratamiento, versiones, finalización, historia y recordatorios. Respetar el inicio real del turno y sus ventanas; registrar checkpoints cuando haya que esperar. No modificar relojes o datos de producción para saltar reglas.
5. Separar errores controlados en entornos aislados de aceptación remota. Concurrencia intensa, reinicios forzados, fallos SQL y SMTP se verificarán con los entornos de prueba existentes; no se inyectarán en el servicio compartido para completar una casilla.
6. Correlacionar respuesta y persistencia cuando el criterio lo exija; usar oráculos de lectura limitados a los IDs del ensayo. Separar SENT —aceptación SMTP— de recepción en buzón.

| Grupo de métricas | Evidencia y separación necesarias |
| --- | --- |
| M1–M2, registro y validaciones | Usuarios y mascotas por separado; intentos válidos/negativos predefinidos, filas correctas y ausencia de escrituras rechazadas. |
| M4–M5, citas y conflictos | Reserva y relaciones correctas; conflictos conocidos con ganador único y sin escrituras parciales; replays separados de altas. |
| M7–M8, clínica | Relaciones y autoría, versiones/eventos e invariantes FINALIZED/COMPLETED verificados. Solo API mientras falte interfaz. |
| M3/M6/M9, latencias | Operación, entorno, carga, muestra y período explícitos; errores/timeouts conservados. Separar cliente, servidor, espera humana y espera del turno. |

Usar la codificación y metas del Plan de Calidad vigente tras reconciliar los documentos. P95 se calculará por operación y muestra; no promediar percentiles. Un conjunto pequeño con todos sus casos favorables no demuestra fiabilidad global ≥99 %. Sin muestra u oráculo suficientes, informar No evaluado/No verificado.

**Cierre:** resultado por recorrido/caso, incidencias asociadas y evidencia reproducible de su versión. SCRUM-84/86 y las historias clínicas integrales conservan pendiente su parte de interfaz; la aceptación API no las cierra completas.

### I6. Validar accesibilidad y uso con personas mayores

**Por qué:** el público objetivo necesita poder completar las tareas; atributos accesibles y capturas estrechas aportan soporte, pero no sustituyen personas ni tecnologías de asistencia reales.

**Cómo:** revisar primero con el equipo los recorridos disponibles y después realizar sesiones por tareas con personas mayores disponibles. Propuesta inicial: **3–5 participantes por conveniencia**, sujeto a disponibilidad, sin llamarlos muestra representativa ni certificación. Registrar el número y condiciones reales. Utilizar datos ficticios; acordar separadamente cualquier grabación.

Evaluar registro/acceso, alta de mascota, solicitud y comprensión del estado de una cita. Observar especialmente entrada de nacimiento, prefijo telefónico, mensajes, fechas y confirmaciones. Anotar éxito autónomo, éxito con ayuda, abandono, errores y comentarios; no imponer una meta de satisfacción o tiempo inexistente en el RNF.

Complementar con teclado, foco, lector real, zoom al 200 %, reflujo, errores, contraste y controles. Registrar navegador, herramienta y pantallas evaluadas. Priorizar impedimentos de tarea, pérdida de información y acciones involuntarias. Cambios visuales o de entrada conservarán el contrato del backend; repetir las tareas afectadas después de corregir.

**Cierre:** acta saneada, resultados por tarea, defectos priorizados y revalidación de correcciones. Las pruebas clínicas de comprensión esperan a la interfaz clínica. Preferencias opcionales de SCRUM-88/C42 se resuelven como decisión de alcance; una función ausente no se marca Passed ni se declara N/A unilateralmente.

### I7. Hacer defendible la operación y la recuperación

**Por qué:** el respaldo restaurado y el monitoreo funcionan como mecanismos, pero requieren una operación que otro integrante pueda seguir. Hay una copia puntual programada y vigilancia dependiente del computador.

**Cómo:**

1. Consolidar un procedimiento vigente de arranque, comprobación y apagado del monitoreo local elegido. Explicar qué ocurre si el equipo está apagado o no hay scrape; conservar capturas/exportaciones saneadas para la defensa.
2. Comprobar configuración cloud real antes de reutilizar plantillas. Ampliar el generador actual para preservar monitoring, referencias a secretos, probes, CPU y límites; separar ValidateOnly de despliegue. No reemplazar un servicio actual con una plantilla histórica incompleta.
3. Mantener la configuración de escala aprobada vigente y documentar su efecto sobre tareas de fondo. Cambios locales sin publicar no constituyen configuración aplicada. Las pruebas de recordatorios requieren cómputo disponible durante toda su ventana.
4. Proponer durante la ventana académica respaldo diario cuando el entorno esté disponible, retención inicial de siete copias y revisión del resultado por un titular/suplente. Objetivos iniciales para aprobar: **RPO 24 h y RTO 2 h**. Son objetivos que deben contrastarse con el ensayo, no garantías existentes.
5. Reutilizar el helper y la restauración real del 25/10. Comprobar una copia reciente sobre base nueva aislada, medir tiempos y registrar límites. No copiar secretos por chat ni subir un dump sin sanear al expediente público.
6. Registrar fecha de vencimiento de Render —14/11 según la documentación disponible— y comprobarla antes de decidir continuidad. Conservar el respaldo puntual previsto para el 13/11; no confundirlo con una política diaria ya instalada.

**Cierre:** otro integrante sigue el procedimiento; responsables, frecuencia, retención y objetivos figuran acordados. Si la disponibilidad del computador impide cumplirlos, registrar el riesgo residual y la alternativa antes de prometer continuidad. Mantener la opción OSS local y el límite de gasto existente; este plan no introduce servicios de pago.

## 5. Actualización concreta de Jira después de la aprobación

Se reutilizarán las incidencias existentes. Antes de editar se releerán descripción, comentarios, criterios y cambios recientes para conservar contribuciones del equipo. No modificar responsables o fechas históricas como atajo para esconder atrasos. Las reasignaciones y nuevas fechas se propondrán según capacidad real.

| Incidencias actuales | Cambio previsto | Condición para cambiar estado o cerrar |
| --- | --- | --- |
| SCRUM-68 y SCRUM-69, En curso | Incorporar el corte remoto, enlaces oficiales, evidencias actuales y pendientes de versión candidata/entrega | Mantener abiertas mientras falten sus revisiones, aceptación y anexos. |
| SCRUM-24/28/31/37/40/41/44/45/48, Por iniciar; SCRUM-25/34/75, En curso | Registrar las pantallas y capacidades publicadas que correspondan a cada criterio, y el pendiente real de integración/accesibilidad | Revisar cada ficha; presencia de pantalla no acredita todos sus criterios. |
| SCRUM-26/29/32/35/38/42/46/49 y SCRUM-77/80 | Enlazar nuevas ejecuciones P1/P2, resultados por rol, permisos y correo | Cierre según criterios completos y evidencia, no por el solo número de tests. |
| SCRUM-50/53/56/63 y SCRUM-82/83/85, Por iniciar | Registrar API clínica publicada, contrato, pruebas y despliegue. Sustituir la descripción de falta de backend por límites actuales | Las tareas de contrato conservan la revisión humana requerida; las tareas backend se cierran al verificar todos sus criterios. |
| SCRUM-52/55/58/65 | Separar qué verifica la API y qué exige interfaz, comprensión o recorrido integral | No cerrar toda una validación con evidencia parcial de API. |
| SCRUM-51/54/57/64 y SCRUM-84/86 | Mantener visible la interfaz clínica pendiente y la dependencia de aceptación integral | Permanecen pendientes en su parte no implementada; no reducir sus criterios para declararlas terminadas. |
| SCRUM-87, Por iniciar y sin asignar | Incorporar CI/protección backend ya comprobados, plan de frontend y evidencia separada por repositorio | Conservar C84 y demostrar el bloqueo efectivo en el alcance que declare la tarea. |
| SCRUM-88, Por iniciar y sin asignar | Mantener decisión de alcance de preferencias y relación con C42 | No cerrar por accesibilidad general ni retirar el requisito sin acuerdo. |
| SCRUM-70 | Incorporar guion, acceso del auditor, reparto de defensa y comprobación de enlaces | Ensayo real del equipo y accesos comprobados. |

Buscar duplicados antes de crear incidencias para login, conservación de artefactos, base de evaluación o continuidad. Si no existe una adecuada, crear una tarea/defecto específico bajo la épica correspondiente, con descripción, responsable propuesto, dependencias, criterio y caso de aceptación. Jira asignará su clave; no se inventan claves nuevas en este documento.

Plantilla de actualización: corte/versión → avance comprobado → evidencia → criterio todavía pendiente → próxima acción. Conservar el criterio original y referencias históricas pertinentes. Las observaciones de usuarios o revisores se registrarán cuando ocurran, sin atribuir al equipo revisiones que no ha hecho.

## 6. Plan de TestRail limitado al alcance publicado

### 6.1. Reconciliar el catálogo antes de importar

1. Iniciar sesión por el mecanismo del usuario y exportar catálogo/ejecuciones actuales: IDs C/T, títulos, PV, referencias, secciones y campos.
2. Comparar con los 84 casos del corte local. Buscar duplicados por identificador y por operación + invariante + nivel de prueba; un caso API y uno de interacción pueden requerir evidencias diferentes.
3. Actualizar casos existentes por sus IDs oficiales verificados. Mantener los resultados históricos de R1 y sus límites; no reescribirlos como si evaluaran 2026-10-26.
4. Crear únicamente los escenarios diferenciales ausentes. Las etiquetas AUD-REM de abajo son propuestas del plan, no IDs C oficiales ni una numeración académica que sustituya PV/CP.
5. Preparar una ejecución nueva identificada por versión/entorno, o reutilizar una ejecución vigente adecuada tras revisar su alcance. Una nueva ejecución comienza sin resultados heredados; registrar Passed solo con todos sus esperados cumplidos, Failed con defecto y Blocked con causa actual.
6. Adjuntar resultado, esperado/obtenido, fecha, versión, entorno y evidencia saneada. Mantener en References los Jira/RNF/CP pertinentes; no sumar aserciones, métodos JUnit y casos TestRail como una misma unidad.

La separación entre diseño, ejecución y resultado sigue el [modelo de resultados documentado por TestRail](https://support.testrail.com/hc/en-us/articles/15813183376148-Submitting-test-results). La importación usará los campos existentes y CSV UTF-8 compatible; probar primero el mapeo y comprobar conteos/referencias antes de confirmar altas masivas.

### 6.2. Diez actualizaciones candidatas del catálogo existente

Los PV se localizaron en el catálogo local; verificar sus IDs C en el servidor. Preservar el alcance original: si un esperado exige interfaz, una variante API no lo sustituye. Separar variantes o añadir un subcaso cuando haga falta.

| Caso existente | Actualización y esperado esencial | Referencias Jira |
| --- | --- | --- |
| PV-RNF-02.02-01 | Zoom real al 200 % en formularios, agenda, errores y diálogos web; funciones y contenidos operables, sin recortes que impidan tarea | SCRUM-75/77/80 |
| PV-RNF-02.05-01 | Tareas P1/P2 solo con teclado, sin trampas ni controles inalcanzables | SCRUM-75/77/80 |
| PV-RNF-02.06-01 | Foco visible y pertinente después de errores, navegación y apertura/cierre de confirmaciones | SCRUM-75/77 |
| PV-RNF-02.07-01 | Lector de pantalla real con versión/plataforma identificadas; nombre, rol, estado, secuencia y errores comprensibles | SCRUM-75/77/80 |
| PV-RNF-02.24-01 | Personas mayores sobre tareas implementadas; registrar comprensión, ayuda, obstáculos y resultados reales sin inventar metas | SCRUM-77 y tarea de usabilidad si procede |
| PV-RNF-03.05-02 | Reprogramación hacia turno ocupado: conflicto previsto, reservas/versiones conservadas y sin evento exitoso del rechazo | SCRUM-13/46/81; CP-011 |
| PV-RNF-01.06-01 | Variante API P3: antes del turno rechazo sin efectos; después, FINALIZED/COMPLETED juntos; replay e inmutabilidad | SCRUM-14/50/52/82; CP-013/014/015 |
| PV-RNF-01.07-01 | Fórmula/controles API según contrato: relación correcta, dosis sin redondeo, fechas/listas/orden y límites; comprensión de UI separada | SCRUM-16/53/55/83 |
| PV-RNF-01.08-01 | Historia publicada: solo finalizados, propiedad, mascota archivada, orden/totales y paginación viva; adendas fuera | SCRUM-17/56/58/85 |
| PV-RNF-06.05-01, C84 | Evidencia de CI verde y fallo que impide integrar, diferenciada por repositorio. No heredar al frontend el resultado del backend | SCRUM-87 |

### 6.3. Quince candidatos a casos nuevos

Son candidatos a alta, sujetos a deduplicación con TestRail vivo. No se ejecutaron al redactar el plan. Precondiciones comunes: versión frontend y revisión backend identificadas; cuentas/datos ficticios y destinatarios autorizados; navegador y fecha; IDs/versiones originales guardados para reconciliar respuestas perdidas.

| Etiqueta propuesta | Preparación y recorrido | Esperado principal | Jira de referencia |
| --- | --- | --- | --- |
| AUD-REM-01 | Propietario nuevo: registro web → login → consulta/edición de teléfono → recarga | Una cuenta OWNER; sin login implícito; perfil y teléfono persistidos de la cuenta correcta | SCRUM-6/24/26/77 |
| AUD-REM-02 | Imagen ficticia: selección/cancelación, procesamiento, guardado y recarga de foto | Cancelar conserva foto; guardar persiste la imagen procesada; recurso protegido sin acceso anónimo | SCRUM-6/26/77 |
| AUD-REM-03 | Dos propietarios: demorar respuesta de A y entregarla después de salir/entrar como B | Respuesta de A descartada; sin datos, mensajes ni alteraciones de sesión de A visibles en B | SCRUM-25/26/77 |
| AUD-REM-04 | Restaurar sesión con red caída, recuperarla y comprobar otra sesión revocada | Estado privado oculto hasta validar; reintento/salida disponibles; revocación retira datos; vencimiento absoluto no se extiende | SCRUM-25/26/77 |
| AUD-REM-05 | Instalar PWA en navegador compatible; usarla, cerrar sesión y abrir offline | Shell accesible según alcance; caché sin API/tokens/datos privados; sin operaciones privadas offline | SCRUM-75/68 |
| AUD-REM-06 | Invitación autorizada: abrir/recargar enlace y completar o abandonar activación | Lectura no consume/activa; sin login automático o sustitución de sesión; token fuera de caché/sesión y retirado de URL al terminar | SCRUM-10/25/34/35/80 |
| AUD-REM-07 | Cita de ensayo: perder respuesta de un cambio administrativo después de enviarlo y consultar estado/historial | Incertidumbre visible, sin reenvío automático; reconciliación sin atribuir el estado a un intento no demostrado; replay conserva intención original | SCRUM-13/45/46/81 |
| AUD-REM-08 | Lote pequeño con una colisión conocida, incluida variante con turno bloqueado; lote válido de control | Rechazo completo sin turnos parciales; comparación de todas las fechas/páginas; control válido funciona | SCRUM-11/37/38/80 |
| AUD-REM-09 | Lote pequeño con respuesta perdida; consulta posterior sin filtro de estado y con todas las páginas | Bloqueo de nuevo envío mientras sea incierto; agenda vigente completa sin afirmar qué intento creó sus turnos | SCRUM-11/37/38/80 |
| AUD-REM-10 | Administrador autorizado y PDF ficticio: carga privada → descarga → revisión → publicación | Bytes descargados iguales al PDF almacenado; revisión/publicación según contrato; actor sin permiso denegado | SCRUM-10/34/35/80 |
| AUD-REM-11 | Título adicional con PDF publicado: editar academia y eliminar PDF/título por separado; controlar título base | Edición retira publicación conservando archivo; eliminar PDF conserva título; título base protegido | SCRUM-10/34/35/80 |
| AUD-REM-12 | Cambio de cita y un aviso esperado a buzón autorizado; consultar como propietario y administrador permitido | Correspondencia de cita/tipo/destinatario; recepción y contenido acreditados aparte de SENT; consultar no reenvía | SCRUM-15/48/49/81 |
| AUD-REM-13 | DRAFT P3: guardar textos y tratamiento, usar versión obsoleta y repetir/alterar intención | Versión compartida; conflicto de versión/intención según contrato; replay sin escrituras/auditoría adicionales | SCRUM-14/16/52/55/82/83 |
| AUD-REM-14 | Cita clínica de ensayo reasignada A→B y posterior finalización; actores propios/ajenos/administrativos | Acceso dinámico correcto; creador y eventos conservados; finalizador conserva sus atenciones, sin lectura permanente ajena; administración sin contenido clínico | SCRUM-14/17/52/58/82/85 |
| AUD-REM-15 | Atención ficticia con una dosis futura y un control, inicio real y buzón autorizado; observar hasta fin | Inicio/fin/ventana respetados; vencidos omitidos; recepción separada de SMTP; garantía de al menos una vez y duplicado incierto documentados | SCRUM-19/63/65/85 |

Los ensayos de respuesta perdida se controlarán en el cliente/gateway de evaluación sin alterar el backend compartido. Donde no pueda hacerse con seguridad en el entorno remoto, ejecutar la variante aislada y dejar la aceptación remota de esa condición pendiente; no sustituirla silenciosamente por un mock.

AUD-REM-14/15 requieren recursos dedicados y ventanas reales; si sus precondiciones faltan, conservar el bloqueo. PV-RNF-02.20-01 y las tareas de comprensión clínica por interfaz permanecen fuera de esta ejecución mientras falte el frontend P3.

### 6.4. Casos que se habilitarán después de publicar correcciones

El login limitado generará variantes de admisión normal, umbral, expiración/Retry-After, persistencia/concurrencia, origen falsificado y protección de otras cuentas. I2/I3/I7 aportarán variantes de retención/protección, reproducción segura y recuperación. Buscar primero su equivalencia en C84 y en los casos de mantenibilidad/fiabilidad existentes. No importar como funcionalidad actual pruebas cuyo comportamiento todavía no existe en remoto.

## 7. Reparto propuesto, riesgos y decisiones para aprobar

Las asignaciones actuales de Jira se conservarán. **Propuesta de coordinación:** Esteban lleva backend/contrato/seguridad/operación; Juan José y Juan Manuel validan los portales y manuales de sus tareas; cada responsable aporta evidencia de sus criterios. El coordinador documental consolida el expediente y otro integrante prueba enlaces y reproducción. Este plan no reasigna automáticamente trabajo ajeno ni da por registrada una revisión compartida.

| Riesgo/dependencia | Tratamiento previsto |
| --- | --- |
| Auditoría antes de terminar el plan | Paquete breve después de I0/I1 y cierres incrementales; explicar pendientes y acciones. |
| Nuevos commits durante ejecución | Revalidar el corte al iniciar cada incremento y registrar qué pruebas/evidencias requieren actualización. |
| Evidencia por vencer | Conservarla primero; ampliar retención para runs posteriores. |
| TestRail o Drive sin sesión/versión vigente | Continuar diseño local; verificar catálogo/documento remoto antes de modificar. No duplicar ni sobrescribir por asumir. |
| Falta de participantes o ventana de recordatorio | Calendarizar con disponibilidad real y checkpoint; mantener ese resultado pendiente. |
| IP del cliente no acreditable | No activar confianza genérica en cabeceras; resolver la procedencia y documentar limitación. |
| Computador o servicio inactivo | Documentar disponibilidad y efecto sobre scrape/tareas; titular/suplente y evidencia conservada. |
| Cambio de esquema o rollback incompatible | Nueva migración versionada, respaldo previo y estrategia compatible con datos; no alterar migraciones aplicadas. |
| Correcciones que excedan el tiempo disponible | Priorizar bloqueantes de seguridad/tarea; registrar el resto con causa, responsable y alcance. |

La aprobación puede aceptar o ajustar estas propuestas: retención CI de 30 días; política inicial configurable de login; base de evaluación con datos ficticios y acceso de lectura; muestra inicial de 3–5 participantes por disponibilidad; respaldo diario/siete copias y objetivos RPO 24 h/RTO 2 h sujetos a verificación. Los límites se documentarán como decisiones del proyecto, sin convertir estimaciones en resultados medidos.

No se requiere resolver decisiones rutinarias otra vez. Si aparece una necesidad material distinta —gasto, acceso nuevo, destinatario real adicional, cambio de alcance o uso de datos ajenos— se presentará esa decisión concreta antes de su ejecución dependiente.

## 8. Condición de cierre y defensa

El plan estará ejecutado cuando cada hallazgo tenga corrección verificada o limitación residual explícita, el expediente corresponda a una versión identificada, Jira refleje avances y aceptación reales, y TestRail tenga los casos pertinentes con resultados sustentados. No se declarará cumplimiento total de los tres procesos mientras falte su interfaz/aceptación clínica.

Antes de una posible visita: comprobar URL y salud; cuentas por rol; datos/turnos ficticios válidos; versión y estado del monitoreo; acceso del auditor a repositorios/documentos/TestRail; guion y reparto de exposición. Evitar cambios grandes durante la demostración. El modelo de datos y la evidencia saneada de restauración estarán disponibles sin compartir producción.

## 9. Fuentes principales y límites de esta preparación

- Guía académica: C:/Users/ASUS/Downloads/2.1 Auditoría.pdf, secciones 6.1–6.11.
- [Backend remoto del corte](https://github.com/Estebangmz666/grownupsvet-backend/tree/736805879fcb6ffe968242d0647439fbe0ddf3f6), especialmente README y docs de seguridad, CI, clínica, observabilidad y respaldo.
- [Frontend remoto del corte](https://github.com/Estebangmz666/grownupsvet-frontend/tree/492c71fc152ddeda55e34df808d0f132cd361dc7), especialmente README, workflow y verificaciones de accesibilidad/integración.
- [Seguimiento SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69), [SCRUM-87](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-87) y las incidencias enlazadas en las matrices.
- [Catálogo S1](https://grownupsvet.testrail.io/index.php?/suites/view/1) y [R1 histórica](https://grownupsvet.testrail.io/index.php?/runs/view/1); base exportada local: outputs/testrail-rnf-2026-10-14 y output/informe-testrail-2026-10-15.
- Evidencia local posterior de despliegue/monitoreo: output/backend-cierre-2026-10-25; frontend: output/frontend-compatibilidad-completed-2026-10-25. Se conservará solo su parte saneada en el expediente.

Se consultaron Jira y los remotos para preparar este documento. TestRail pidió autenticación; su estado vivo no se deduce del export histórico. Las copias locales de documentos académicos sirven para identificar contradicciones, y las versiones de Drive se contrastarán antes de editar. La preparación no repitió suites, carga, envíos de correo, sesiones de usuario ni aceptación remota.
