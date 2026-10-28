# Plan previo de aceptación desplegada P1–P3 — I8

25 de octubre de 2026, America/Bogota. **Plan e inspección únicamente**: no se ejecutaron mutaciones HTTP/SQL, no se abrieron contraseñas/DPAPI ni se alteraron relojes, cuentas o despliegues para redactarlo.

## Corte y condición de entrada

El contrato local inspeccionado es OpenAPI 0.10.0 con 47 rutas. La producción sigue siendo la revisión anterior hasta que el agente principal publique y compruebe la nueva imagen. No iniciar la aceptación clínica antes de acreditar revisión/digest, 47 rutas clínicas, migraciones V1–V22 satisfactorias, sondas mínimas y perfil `monitoring` correcto.

Se comprobó solo la existencia de las credenciales privadas `cloud-runtime/demo-accounts.clixml`, `cloud-runtime/render-connection.clixml` y `gmail/cloud-test-owner.clixml`, bajo `%USERPROFILE%/.grownupsvet`. No se leyó su contenido. Las siete cuentas preparadas y el propietario Gmail se reutilizan mediante nuevos logins; no se crean profesionales ni se cambian sus estados. El propietario Gmail es el único destinatario real ya autorizado para este recorrido.

## Restricción temporal que determina el cierre

El reloj consultado durante la planificación fue `2026-10-26T04:02:13Z`, **25/10 a las 23:02:13 de Bogotá**.

- Para reservar **26/10 a las 06:00 de Bogotá (`11:00Z`)**, el propietario debe solicitar la cita **antes de medianoche del 25/10**. Desde 26/10 a las 00:00, la regla «desde mañana» exige 27/10, aunque todavía falten horas para el turno del 26.
- La solicitud existente debe confirmarse antes del corte de las 06:00 y antes del inicio. Dejarla pendiente puede activar su cancelación automática; el harness no modifica esa tarea.
- Hoy se pueden validar borrador, tratamiento y rechazo de finalización anticipada. **La finalización válida solo se reanuda desde el inicio real del turno**, sin SQL, configuración o reloj que eludan la regla.
- Si se pierde la ventana de reserva, se utiliza la siguiente fecha legal y se mueve el checkpoint. No se retrofecha una solicitud ni se declara terminado el bloque dependiente del tiempo.

## Harness privado y reanudable previsto

La implementación posterior tendrá un RunId, etapas y un manifiesto privado fuera de Git. Guardará inmediatamente IDs, versiones, intenciones idempotentes, tiempos UTC/Bogotá y resultados sanitizados. Los JWT permanecerán solo en memoria; las contraseñas nuevas ficticias, si se necesitan, se guardarán exclusivamente con DPAPI. No habrá headers Authorization, contraseñas, OTP, cuerpos de login, tokens de monitorización ni payloads SMTP en logs/reportes.

El cliente REST usará HTTPS, validación normal del certificado, origen fijo del backend y redirecciones deshabilitadas. Cada ejecución/reanudación obtiene tokens nuevos mediante `POST /api/v1/auth/sessions`; no reutiliza JWT anteriores al cambio de permisos P3.

El oráculo PostgreSQL reutilizará la conexión Render privada con TLS `verify-full` y confianza verificada. Cada consulta se ejecutará en una transacción explícitamente de solo lectura, limitada al RunId/IDs del ensayo y terminada con rollback. No se leerán hashes de contraseña, OTP, tokens, cuerpos cifrados ni valores de secretos. No se sembrarán datos ni crearán roles de BD. Si TLS o el oráculo fallan, la comprobación queda **No verificada**, sin degradar la verificación del certificado. [TLS de PostgreSQL](https://www.postgresql.org/docs/18/libpq-ssl.html).

Estados de reanudación: `PREFLIGHT_DONE`, `P1_DONE`, `APPOINTMENT_CONFIRMED`, `CLINICAL_DRAFT_READY`, `WAITING_FOR_APPOINTMENT_START`, `FINALIZED`, `REMINDERS_VERIFIED` y `REPORT_DONE`. Persistir el checkpoint mediante escritura temporal y sustitución en el mismo directorio privado; no borrar registros de negocio para repetir una etapa.

Ante timeout de una mutación idempotente, consultar API/BD y repetir únicamente la misma clave e intención. No reemplazar expectedVersion bajo una clave ya utilizada. Registro de usuario y creación de mascota no tienen la misma garantía: una respuesta perdida exige reconciliar su marcador único antes de volver a crear; si continúa siendo incierta, conservar UNKNOWN y detener esa etapa. Mantener el timeout/error en la muestra aunque después se encuentre el recurso persistido.

## Etapa A — Hoy, después de publicar la revisión correcta

1. **Preflight**: guardar commit, revisión, digest, contrato y período; comprobar liveness/readiness. Validar scrape máquina 200, anónimo/JWT humano/hash literal 401 y token máquina en API protegida 401. Iniciar Prometheus/Grafana locales sobre el backend desplegado, sin Grafana Cloud, sidecar ni servicios nuevos de nube.
2. **P1**: registrar un conjunto pequeño de propietarios con correos únicos `@example.com` y datos ficticios; crear/leer sus mascotas y comprobar propiedad cruzada. El registro no genera correo. Ejecutar inválidos predefinidos —campos obligatorios, teléfono, fecha futura, tipos JSON y mascota ajena— y contrastar ausencia de escrituras. No solicitar recuperación, leer códigos ni cambiar contraseñas.
3. **Cita principal**: con el administrador y un veterinario existente activo, consultar/publicar un turno libre para la primera fecha legal a las 06:00. El propietario Gmail registra una mascota ficticia y solicita esa cita mediante `POST /api/v1/appointments`; conservar clave, versión y UUID. Repetir el mismo cuerpo comprueba replay sin otro registro; propietario/veterinario no pueden confirmar. El administrador confirma con la versión actual, antes del corte.
4. **Conflicto conocido**: intentar reservar el mismo turno ya ocupado con otra intención válida salvo la ocupación; esperar 409 y verificar un único ganador, ninguna ocupación incompatible ni escritura parcial. Un control válido previo demuestra que el servicio no rechaza indiscriminadamente. El conjunto M5 se define antes de ejecutar y sus conflictos esperados no se mezclan con el denominador M4 de solicitudes elegibles sin conflicto. La competencia simultánea conserva su evidencia aislada, sin crear más citas productivas innecesarias para esta aceptación.
5. **Borrador P3**: el veterinario asignado crea la atención y verifica su replay. Guarda motivo, anamnesis, examen, diagnóstico y plan, todos marcados como ensayo ficticio sin finalidad asistencial. Guarda una fórmula ficticia y un control; conserva las versiones compartidas. El veterinario ajeno y propietarios no leen borradores; administración/superadministración no reciben clínica. Comprobar una versión obsoleta, una clave con otra intención y los límites de validación sin modificar el estado esperado.
6. **Barrera temporal**: intentar la finalización antes del inicio con contenido/versiones válidos; esperar 409 `CLINICAL_APPOINTMENT_NOT_STARTED` y confirmar DRAFT, cita CONFIRMED, auditorías sin incremento y cero programaciones clínicas. Persistir `WAITING_FOR_APPOINTMENT_START` con el instante mínimo real y cerrar esta ejecución de forma reanudable.

No se cambia el estado de profesionales para simular permisos. Reasignaciones, reinicios forzados, SMTP fallido y fallos SQL tardíos ya tienen pruebas aisladas; no se inyectan esas fallas en producción para completar una lista.

## Etapa B — Desde el inicio real del turno

1. Repetir preflight, login y lectura de cita/atención. Si la revisión o relaciones cambiaron, reconciliar antes de escribir. Una cita cancelada o sin asignación no se reabre.
2. **Preparar una sola dosis futura** antes de finalizar: fijar startsAt aproximadamente a `ahora + 10 minutos`, endsAt a `ahora + 20 minutos` e intervalo de una hora. Así existe exactamente una ocurrencia. El control tendrá scheduledAt aproximadamente a `ahora + 45 minutos`, por lo que su aviso será inmediato al faltar menos de 24 horas. Usar nombres/instrucciones inequívocamente ficticios, como «ensayo funcional, no administrar»; no registrar una recomendación médica real.
3. Si los tiempos guardados hoy ya vencieron, reemplazar el tratamiento mientras siga DRAFT con una nueva intención y su versión vigente. No intentar editarlo después de finalizar ni recuperar dosis pasadas.
4. **Finalización atómica**: POST a `/api/v1/clinical-encounters/{encounterId}/finalizations` con clientRequestId, expectedVersion y expectedAppointmentVersion vigentes. Verificar atención FINALIZED/cita COMPLETED, mismo propietario/mascota, creador conservado, responsable final correcto y eventos/operación únicos. La BD debe mostrar ambas transiciones juntas; un 200 solo no demuestra atomicidad.
5. Repetir la misma finalización: mismo recurso, sin nuevas versiones, eventos, programaciones o correos. Intentar otra intención obsoleta y escritura del finalizado: 409 y ausencia de cambios. No probar inmutabilidad mediante UPDATE/DELETE SQL en producción.
6. **Historia**: OWNER propio lee detalle, tratamiento e historial; OWNER ajeno obtiene 404; administrador y superadministrador 403. Veterinario ajeno sin cita vigente no accede; el finalizador conserva únicamente sus atenciones finalizadas al quedar sin cita CONFIRMED. Contrastar búsqueda por cita, paginación y contenido publicado; nunca debe aparecer el borrador como historia.
7. **Avisos con reloj real**: observar la programación del control y su tarea inmediata. Al llegar la única dosis futura, observar una tarea con scheduledAt real y expiresAt no posterior a gracia/siguiente dosis/fin. Esperar mediante checkpoints acotados, sin alterar propiedades ni relojes. Tras el fin comprobar que no aparecen nuevas ocurrencias. `SENT` acredita aceptación SMTP; la llegada al buzón autorizado y el contenido correcto requieren evidencia aparte.

Presupuesto de correo esperado para la cita principal: una confirmación, un control y una dosis. Confirmar hoy un turno de mañana con menos de 24 horas no debe crear el recordatorio P12 de 24 horas ya vencido. No provocar cancelaciones/reprogramaciones innecesarias ni ráfagas para generar más muestras. Cualquier tarea pendiente dirigida a una dirección inesperada se investiga antes de continuar, sin editar colas por SQL.

## Indicadores M1–M9 y oráculos

Se conserva el protocolo de la sección 3.4 del Plan de Calidad de octubre, copia `output/metricas-rnf-2026-10-14/plan-calidad-despues.md` del directorio padre. Definir antes de ejecutar los casos elegibles/negativos y registrar numerador, denominador, operación, período, revisión y perfil de carga. Empezar secuencialmente, con muestras pequeñas y acotadas; no presentar este smoke test como prueba de capacidad o disponibilidad prolongada.

| Indicador | Resultado y oráculo necesario |
| --- | --- |
| M1, meta ≥99% | Separar altas de usuarios y mascotas. Éxito exige respuesta correcta y fila única persistida con rol/propiedad esperados; contar todos los intentos válidos, errores y timeouts. |
| M2, meta 100% del conjunto | Cada inválido predefinido recibe el rechazo esperado y no deja filas/cambios; incluir controles válidos. |
| M3, meta P95 <500 ms | Medir por separado registro de usuario, mascota y consultas; no incluir login, tiempo humano ni espera del turno en registro. |
| M4, meta ≥99% | Citas elegibles creadas correctamente, con propietario/mascota/turno y reserva única verificados. Replays no incrementan el total de citas. |
| M5, meta 100% del conjunto | Ocupación/conflictos conocidos efectivamente impedidos: confirmar ganador, ausencia de reservas incompatibles y estado/auditoría sin escrituras parciales. |
| M6, meta P95 <500 ms | Separar disponibilidad, solicitud y gestión administrativa. La espera hasta confirmación humana no forma parte de la latencia API. |
| M7, meta ≥99% | Atenciones válidas registradas y persistidas, con actualizaciones/finalización reportadas por operación; no contar cada lectura o replay como una atención nueva. |
| M8, meta 100% | Relaciones de cada atención evaluada con appointment, pet, owner, creador y finalizador; origen de hijos, versiones/eventos y correspondencia FINALIZED/COMPLETED. Evaluar también que los rechazos no alteran esos invariantes. |
| M9, meta P95 <500 ms | Separar guardado de textos, tratamiento, finalización, detalle e historial. No mezclar la espera nocturna ni el intervalo del recordatorio con el tiempo de respuesta. |

El oráculo leerá únicamente tablas y campos pertinentes: `users/owner_profiles`, `pets`, `appointments/appointment_events`, `clinical_encounters`, sus eventos/operaciones, `clinical_prescriptions`, `clinical_controls` y programaciones/tareas clínicas. Comparar conteos y valores previstos por IDs, sin dumps generales ni SELECT de secretos. Para atomicidad, una instantánea read-only consistente debe ver la pareja atención/cita y su trazabilidad del mismo commit.

Medir HTTP mediante reloj monotónico del cliente, conservando también códigos/errores/timeouts; etiquetar estas latencias como extremo a extremo desde el equipo local. Para N observaciones completas, P95 exacto es la posición `techo(0,95×N)` ordenada, por operación. El histograma Prometheus aporta latencia del servidor y cuantiles aproximados; se reporta aparte, sin promediar percentiles ni sustituir silenciosamente una medida por otra. Si se exige un P95 puro de servidor con muestras individuales, registrar esa limitación y no certificarlo con tiempos del cliente. Un valor igual a 500 ms no cumple `<500 ms`.

Sin muestra válida o con oráculo incompleto: **No evaluado/No verificado**, con motivo. Una muestra pequeña de 100% describe esos casos; no acredita un 99% global ni seguridad/fiabilidad de todo el sistema. Los contadores de commits y HTTP apoyan el contraste, pero no sustituyen persistencia, integridad o recepción de correo.

## Evidencias y pendientes reales

El resultado debe enlazar commit/digest/revisión, contrato, ventana de ensayo, checkpoint, resultados sanitizados, conteos/oráculos, métricas y panel local de la revisión desplegada. Las capturas de buzón deben ocultar dirección real, códigos y datos innecesarios. No leer OTP ni solicitar un cambio de contraseña para completar la evidencia.

Resuelto por autorización y contexto existentes: reutilizar las cuentas y el destinatario Gmail previo, crear datos ficticios mediante REST, obtener JWT nuevos, efectuar consultas de solo lectura TLS y ejecutar Prometheus/Grafana OSS locales sin gasto adicional. No se pide nueva confirmación para decisiones rutinarias del harness.

Pendientes críticos: publicación de la revisión correcta; solicitar la cita dentro de su ventana legal; reanudar desde el inicio real; recibir los dos avisos clínicos en el destinatario autorizado y correlacionarlos con sus tareas; completar muestra/oráculos/latencias y documentar resultados que no cumplan la meta. Si se necesita otro destinatario real, nuevas credenciales/permisos, gasto, alterar datos ajenos o eludir una regla temporal, se detiene ese bloque y se consulta al usuario. Este plan no programa una automatización ni declara esos pendientes resueltos.

## Implementación privada preparada después del plan

El harness se implementó fuera de Git en `output/backend-cierre-2026-10-25/acceptance` del directorio padre: launcher PowerShell 7.2 con DPAPI/ACL/mutex por RunDirectory, cliente Python estándar por stdin y oráculos psql de solo lectura con raíces públicas de Windows exportadas a PEM temporal. El modo por defecto y ValidateOnly ejecutan únicamente guardas locales. No se ejecuta ninguna fase real antes de revisión y autorización postdeploy del agente principal.

Delta previo al primer reintento: una entrada `.git` residual en el directorio del proyecto activó la guarda local antes de cualquier HTTP/BD. Para conservar su defensa sin excepciones, los scripts permanecen en output y el checkpoint, credencial DPAPI de fixtures y CA temporal se guardan por defecto en `%USERPROFILE%/.grownupsvet/acceptance/backend-2026-10-25`. Solo se permite un RunDirectory bajo ese directorio privado, sin enlaces ni ancestros Git. El primer rechazo fue exclusivamente local y no envió una intención de negocio.

Delta previo al siguiente preflight: el primer scrape autenticado recibió 406 porque el cliente solicitaba JSON. Se ajusta únicamente la negociación de `/actuator/prometheus` a text/plain y se mantiene JSON para la API; el 406 se conserva como evidencia del cliente, sin atribuirlo a una operación de negocio ni eliminarlo del registro.

Delta local de reanudación: reaplicar Set-Acl sobre el directorio ya protegido solicitó SeSecurityPrivilege. No se eleva el proceso: se comprueba que la DACL existente está protegida y contiene únicamente FullControl para usuario actual y SYSTEM; si cumple, se reutiliza. La protección de directorios nuevos se mantiene y cualquier ACL inesperada se detiene sin ampliar permisos.

Para respetar la ventana de medianoche, `Prepare` prioriza la única cita Gmail, confirmación, borrador y negativos; `P1` puede completarse después. M5 se limita inicialmente al conflicto conocido de turno ocupado. El inválido P1 implementado inicialmente es nacimiento futuro, además de propiedad cruzada; otros inválidos conservan evidencia local y no se declaran ejecutados en producción. Las tasas que requieren un 201 original quedan sin verificación cuando solo pudo reconciliarse la persistencia después de un timeout. Las latencias censuradas se identifican como tales. La recepción de buzón no se deduce de las tareas SMTP.
