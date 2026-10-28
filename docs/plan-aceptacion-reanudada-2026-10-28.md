# Plan previo de aceptación reanudada P1–P3 — R2

28/10/2026, America/Bogota. Complementa el plan de reanudación R1–R4 y el I8 original. Esta entrega prepara código privado y pruebas locales; no ejecuta REST, BD ni SMTP.

## Base y conservación

Producción identificada por el agente principal: revisión `grownupsvet-backend-admission-960c8898`, contrato OpenAPI 0.11.0/47 rutas y V23. La estructura se verifica contra `origin/main`; no se despliega código anterior sobre la auditoría. Los scripts nuevos viven fuera del repositorio, en `output/backend-cierre-2026-10-28/acceptance` del directorio padre, y usan un RunDirectory privado nuevo del 28/10. Código, checkpoint y reporte del 25 quedan intactos, enlazados por SHA256/RunId/revisión.

La ejecución original terminó con cita confirmada y borrador v2 para el 26/10; no acredita su estado actual. Sus muestras, incluido el406 de negociación del cliente, permanecen históricas y fuera de los denominadores nuevos. No contar la atención existente como otra creación M7.

## Incrementos del harness

1. **Preflight actual:** validar revisión exacta, contrato estructural, sondas mínimas y acceso de máquina; exigir V23/read-only/TLS verify-full. Un run no puede cambiar de revisión silenciosamente. Guardas de rutas y métodos se derivan del contrato real.
2. **Reconcile:** sustituye Prepare. Lee recursos por sus IDs originales; exige CONFIRMED/ASSIGNED, DRAFTv2, relaciones/autoría/contenido/hijos exactos, participantes activos y destinatario Gmail previamente autorizado. Los cambios de auditoría detienen la escritura para reconciliar. No repetir finalización anticipada ni conflicto de un turno pasado.
3. **P1:** dos propietarios sintéticos @example.com y mascotas; persistencia, propiedad cruzada y validación de nacimiento futuro. Intenciones/checkpoints antes de cada envío; POST no idempotente incierto se reconcilia, sin duplicar ni cambiar contraseñas/OTP.
4. **Finalización:** revalidar inmediatamente el estado y destinatario; reemplazar tratamiento mientras DRAFT con una dosis ficticia futura (+10min, fin+20min, intervalo1h) y control futuro+45min. Finalizar con versiones e intención persistidas; comprobar FINALIZED/COMPLETED, eventos, autoría y dos programaciones. Replay sin nuevas escrituras clínicas ni duplicación de ocurrencias. Validar detalle, tratamiento e historia por roles.
5. **Recordatorios/reporte:** con mínimo1 mantenido por root, observar control inmediato y dosis con reloj real; después del fin no deben aparecer nuevas ocurrencias. SENT acredita aceptación SMTP; buzón/contenido requieren evidencia externa. Registrar tasas y P95 por operación/revisión/run, incluidos errores y metas incumplidas; N pequeño no acredita una población.

## Admisión, privacidad y revisión

El contrato actual documenta429 con Retry-After. El cliente persiste antes de POST un máximo conservador de5 logins por alias/15min, reutiliza JWT solo en memoria por proceso y termina ante429/credencial inválida/transporte incierto. Respeta Retry-After sin retry automático. No intenta agotar el límite.

Se mantienen DPAPI por stdin, origen HTTPS fijo, ausencia de proxies/redirecciones, mutex/ACL privados, secretos fuera de reportes y SQL exclusivamente SELECT en transacciones read-only con TLS verify-full. Único destinatario real: Gmail ya autorizado; cuentas nuevas solo @example.com. No cambiar staff, reloj, colas, estados por SQL ni configuración cloud desde el harness.

Antes de autorizar red: parser PowerShell, compile Python en memoria y pruebas con fixtures/HTTP/BD falsos, incluida la separación de históricos y el replay frente a cambios asíncronos del outbox. Revisar la copia congelada con audit_ci. Pendientes reales: reconciliar producción, ejecutar P1/finalización/historia y observar avisos; no se declaran completos con pruebas locales.

## Latencias correlacionadas

Se incorpora el plan de latencias del 28/10: cada intento envía un trace aleatorio técnico mediante X-Cloud-Trace-Context y traceparent, sin datos de usuarios ni querystrings. El checkpoint conserva trace, operación, revisión, método, estado, UTC y tiempo monotónico del cliente. Un colector separado correlacionará los request logs existentes de Cloud Run por esos traces; no se activa un exportador. La medida de plataforma y la del cliente permanecen separadas; sin correlación completa el P95 de backend queda no verificado, aunque el tiempo de Internet sea alto.

## Ampliación previa autorizada: P2Current para M4/M5/M7 actuales

La reconciliación de la cita antigua no aporta nuevas creaciones a la revisión vigente. Se añade fase P2Current independiente, antes de ejecutar código de negocio: con el propietario Gmail previo crear una sola mascota nueva marcada “Ensayo funcional; no asistencial”, usar veterinario2 y un turno legal de mañana06:00. Sus recursos/intenciones se guardan bajo currentScenario, sin reemplazar los IDs originales. Si vence la ventana o cambia la versión, detener; nunca mover la fecha/intención ya enviada implícitamente.

Registrar una cita elegible, comprobar persistencia/relaciones y replay con la misma intención; otra clave contra ese turno debe recibir APPOINTMENT_SLOT_OCCUPIED y no insertar filas/eventos parciales. Confirmación por administrador crea una notificación real extra al mismo Gmail permitido. Veterinario2 crea un borrador nuevo con cinco textos “Ensayo funcional; no asistencial”; comprobar201 original, persistencia, autoría, replay de creación/textos y versiones/eventos estables. No añadir fórmula/controles ni finalizar anticipadamente ni cancelar para aparentar una atención real. Queda explícitamente CONFIRMED+DRAFT futuro como dato de ensayo.

M4/M5/M6 y M7_creation/M8/M9 tendrán denominadores nuevos en esta fase; errores/timeouts se conservan y POST incierto no se acredita por un replay posterior. La nueva mascota es distinta de la antigua para no otorgar a veterinario2 acceso a la historia original ni contaminar la prueba del finalizador veterinario1. Presupuesto real nuevo máximo previsto: una confirmación futura + un control + una dosis = tres correos al Gmail ya autorizado; sin nuevas direcciones. La notificación clínica del borrador futuro no existe porque permanece DRAFT sin tratamiento. Root mantiene min1 durante la ventana activa. Aún no se autoriza REST/BD/SMTP en esta entrega.

## Preparación local acreditada

La copia candidata pasó18 pruebas offline con HTTP/BD falsos, compilación Python en memoria, parser PowerShell sin errores y ValidateOnly sin red. Los cinco archivos históricos cubiertos por SHA256 permanecen idénticos y el checkpoint real nuevo todavía no fue creado durante esta entrega. Los oráculos M8 verifican también actores y claves/versiones de las operaciones. P2Current exige menos de24h al confirmar para mantener el presupuesto de correo sin otro recordatorio P12. La ejecución real sigue pendiente de revisión y autorización del agente principal.

Corrección de revisión independiente antes de red: M2 guarda intención y denominador antes de POST. Los subcasos timeout/HTTP500 conservaron N1/numerador0 sin acreditar éxito; ambas pruebas son offline. Root autorizó luego exclusivamente Preflight y Reconcile, sin mutaciones de negocio.

## Corte real autorizado de lectura — 28/10, 09:23 Bogotá

Después del freeze corregido, root autorizó únicamente Preflight/Reconcile con logins nuevos. Ambos pasaron sobre admission-960c8898: contrato0.11.0/47rutas, V1–V23 exitosas exactas, TLSverify-full/SQLread-only, sondas mínimas y aislamiento máquina/humano. El oráculo actual confirma cita CONFIRMED/ASSIGNEDv1 y atención DRAFTv2, contenido/hijos/autoría intactos, participantes activos y destinatario previo válido. Hay3eventos/3operaciones clínicas,2eventos de cita y0finalizaciones/programaciones clínicas. Lecturas por roles no cambiaron el snapshot.

Fueron15/15checks y29intentos HTTP con respuestas esperadas, sin timeout/429; cinco logins autorizados, uno por alias. La muestra M8 actual es1/1 y no acredita una población. Quedan pendientes P1/P2Current/finalización/recordatorios, recepción y correlación de latencias de plataforma. No se ejecutaron fases de mutación de negocio. El histórico del25 conserva su SHA y sus resultados independientes. Evidencia privada: run backend-2026-10-28 y corte reconcile-readonly-cut.json; no se publican credenciales, destinatarios ni textos clínicos.

## Corte real de negocio autorizado — 28/10, 09:28 Bogotá

Root autorizó después P1, P2Current y la finalización original. Pasaron las altas de2propietarios ficticios/3mascotas, persistencia y validación negativa; la cita nueva para29/10a06:00 quedó CONFIRMED con veterinario2 y DRAFTv1 ficticio sin recetas/controles. Sus IDs son independientes del caso original. El conflicto ocupado409 y los replays no añadieron escrituras.

La atención original se finalizó28/10a09:27:17Bogotá (14:27:17Z): FINALIZEDv4/COMPLETEDv2,5eventos/5operaciones clínicas,3eventos de cita y2programaciones. Se verificaron autoría, clave/versión, atomicidad, replay, inmutabilidad y accesos de detalle/tratamiento/historia por roles. La dosis única quedó para09:37:12Bogotá (14:37:12Z), con fin09:47:12(14:47:12Z); control para10:12:12(15:12:12Z), cuyo aviso inmediato ya constó SENTa09:27:20,un intento sin error y destinatario autorizado. SENT acredita aceptaciónSMTP; no se verificó buzón. La dosis todavía no tenía tarea en el corte09:28.

Total del run hasta ese corte:47/47checks,138intentos HTTP con respuestas esperadas,sin timeout/429/fallo inesperado;28intentos con elegibilidad explícita para latencias de plataforma. M1usuarios2/2,mascotas3/3,M2=1/1,M4=1/1,M5=1/1,M7creación/tratamiento/finalización1/1 cada uno;M8=3/3observaciones sobre2atenciones distintas. Son muestras funcionales acotadas, sin acreditación poblacional ni sustitución del P95 de backend por Internet. Permanecen pendientes la dosis, la observación posterior al fin, el buzón y el colector de latencias. Sin sondeos adicionales: root coordina las ventanas reales.

## Unidad M8 y lectura autorizada de09:38

El reporte conserva3/3 observaciones M8 de snapshots y agrega el denominador correcto:2/2 registros clínicos distintos, original finalizado y futuro borrador, con referencias a cortes y últimas comprobaciones. La observación prefinal permanece enlazada. El ajuste local pasó22pruebas offline y dejó checkpoint/requestSamples/traces anteriores intactos; no cambió elegibilidad ni esquema1 del colector.

A partir de la ventana09:37:40 autorizada, una sola ejecución de Reminders observó ambas tareas clínicas SENT: control14:27:20.687862Z y dosis14:37:16.689037Z, cada una con un intento, error nulo y destinatario autorizado. Exactamente una programación y una ocurrencia por fuente. La dosis tenía scheduled14:37:12.928076Z y expiry14:42:12.928076Z, anterior a su fin14:47:12.928076Z. La confirmación de la cita futura se contrastó mediante GET de notificaciones propias ya existente en el contrato: OWNER_CONFIRMED,SENT,un intento,sentAt14:26:21.429949Z. Son los tres avisos previstos; no se provocaron envíos manuales ni se consultaron cuerpos/direcciones.

Corte privado dose-and-confirmation-readonly-cut.json:50/50checks,147intentos HTTP totales,28elegibles para latencia de plataforma y ningún fallo/timeout/429. La recepción en buzón sigue no verificada. Se detuvieron las lecturas hasta la comprobación posterior al fin autorizada para14:47:28Z; ese requisito aún no se acredita en este corte.
