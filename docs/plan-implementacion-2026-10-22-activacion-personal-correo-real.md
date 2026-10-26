# Plan: publicación y aceptación real de la activación de personal

Fecha: **22 de octubre de 2026**, America/Bogota.

**Estado: pendiente de aprobación de Esteban.** Este documento es el único archivo de proyecto creado durante esta revisión. No se implementó código, no se ejecutaron pruebas, no se cambió Jira y no se publicaron servicios ni enviaron correos.

**Resultado buscado:** crear una cuenta de personal desde el portal, recibir su invitación en un buzón real, abrir el enlace público, establecer la contraseña e iniciar sesión en el portal correspondiente. Es la continuación del primer incremento administrativo, no la construcción de otros formularios.

## 1. Punto de partida y fuentes

Se revisaron el plan administrativo anterior, su informe de implementación y el código actual. La gestión de personal y la pantalla pública `/activate-account` ya existen localmente. El informe anterior registra **192 pruebas en 25 archivos**, exportación web y recorridos contra una simulación HTTP. Es evidencia anterior; no se repitió durante esta planificación y no demuestra correo real ni publicación.

Fuentes principales:

- [Plan del primer incremento administrativo](plan-implementacion-2026-10-22-frontend-administrativo-incremento-1.md).
- [Implementación y límites comprobados](C:/Users/ASUS/Documents/ChatGPT/grownupsvet-frontend/docs/verificacion-incremento-administrativo-1-2026-10-22.md).
- [Personal e invitaciones](personal-e-invitaciones.md), [usuarios y permisos](arquitectura-usuarios-y-permisos.md), [Google Cloud](google-cloud.md) y [verificación histórica de Gmail](verificacion-gmail-2026-10-16.md).
- Frontend activo: `src/screens/auth/StaffAccountActivationScreen.tsx`, `src/navigation/staffAccountActivationLink.ts`, `src/navigation/useStaffAccountActivationLink.ts`, `packages/api-client/src/staff.api.ts`, `proxy/worker.mjs`, `proxy/policy.mjs`, `pwa/policy.mjs`, `wrangler.jsonc` y `proxy/README.md`.
- Backend: controladores y servicios de `staff/invitation`, configuración productiva y `scripts/deploy-google-cloud.ps1`.
- Jira consultado en vivo mediante el conector Atlassian: descripciones, estados, responsables, vencimientos, padres y comentarios recientes de las tareas de continuidad. El conector respondió correctamente en esta revisión. No se verificó pertenencia a un sprint activo; no se atribuyen fechas de sprint a vencimientos.
- [OpenAPI de Cloud Run](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs), consultado nuevamente: **0.9.0**, con las operaciones de creación de personal, invitaciones, activación y login utilizadas aquí. Esto no acredita la configuración interna de envío.

Se ejecutó `git fetch --no-tags origin` correctamente en ambos repositorios, sin integrar cambios:

| Repositorio | Ruta y rama actual | HEAD / remoto actualizado | Diferencia de commits |
| --- | --- | --- | --- |
| Backend | `C:\Users\ASUS\Documents\ChatGPT\GrownupsVet\grownupsvet-backend`, `main` | `0bd38ed7bfe9a4cc715bb9f1e21dbcafc53c31cc`, igual a `origin/main` | 0 locales / 0 remotos |
| Frontend activo | `C:\Users\ASUS\Documents\ChatGPT\grownupsvet-frontend`, `codex/fix-web-cloudflare` | `a028b06c0a744483646768fa0da7e629d3074fd2`, igual a `origin/master` | 0 locales / 0 remotos |

La rama frontend no tiene seguimiento configurado; `origin/master` es la referencia pertinente ya identificada. Ambos árboles contienen trabajo sin commit. Antes de añadir este plan, el backend conservaba **21 archivos modificados y 11 nuevos**; el frontend mostraba **59 entradas** de estado, algunas correspondientes a directorios nuevos completos. Sus incrementos locales no están incluidos en los commits coincidentes con GitHub. No utilizar el clon antiguo anidado dentro de GrownupsVet ni publicar `master` suponiendo que contiene las pantallas nuevas.

La verificación de Gmail del 16/10 acredita recepción real de un correo de **recuperación**, no de invitaciones de personal ni la configuración actual de Cloud Run. No se leyó la configuración privada de Cloud Run o Cloudflare en esta planificación. `gcloud` no está disponible en el PATH consultado. La causa del 404 público anterior continúa sin diagnóstico remoto confirmado.

## 2. Continuidad seleccionada desde Jira

Estas tareas siguen en **En curso**, según la consulta actual. Las fechas son las registradas; todas están vencidas al 22/10. No se reasignan ni reprograman.

| Tarea | Responsable registrado | Vence | Relación con este plan |
| --- | --- | --- | --- |
| [SCRUM-34 — Gestión de personal y cambios de estado](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-34) | Juan Manuel Peña Hernandez | 26/09 | Principal: completar publicación y aceptación del flujo implementado. |
| [SCRUM-35 — Privilegios y desactivación](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-35) | Juan José Carvajal Gómez | 27/09 | Validar cuentas reales, invitaciones y efectos de desactivación. |
| [SCRUM-25 — Acceso y sesión del personal](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-25) | Juan Manuel Peña Hernandez | 15/09 | Comprobar acceso después de activar y conservación de otras sesiones. |
| [SCRUM-26 — Validación de ambos accesos](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-26) | Juan Manuel Peña Hernandez | 16/09 | Regresión de login, expiración y denegación. El perfil completo del propietario conserva sus pendientes. |
| [SCRUM-75 — Base y convenciones de ambos frontends](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-75) | Juan José Carvajal Gómez | 10/09 | Instrucciones reproducibles y comportamiento de formularios. El acuerdo compartido de interfaz sigue siendo otro criterio de cierre. |
| [SCRUM-10 — Historia de personal](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-10) | Juan Manuel Peña Hernandez | 27/09 | Historia padre; no se cierra por este recorrido solamente. |
| [SCRUM-80 — Recuperación, personal y horarios](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-80) | Juan José Carvajal Gómez | 04/10 | Recibirá evidencia parcial de personal; recuperación y horarios completos quedan pendientes. |

Dependencias backend [SCRUM-33](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-33) y contrato [SCRUM-78](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-78): **Finalizado** en Jira actual. Se registra el estado; no se revalidan todos sus criterios por inferencia.

SCRUM-34 no tiene descripción ni comentarios propios. SCRUM-35 y su comentario mantienen la revisión compartida de permisos y separan las pruebas clínicas posteriores P08–P11. No se inventa acceso clínico para ADMINISTRATOR por administrar cuentas. SCRUM-24, registro/perfil del propietario, continúa **Por iniciar**: la existencia de sus pantallas no autoriza cambiar ese estado ni tratar todo su alcance como aceptado.

### Vencimientos del 22 al 29 de octubre

La consulta de actividades sin finalizar devolvió **14 tareas**, todas **Por iniciar**. Se agrupan para lectura; no constituyen un cronograma nuevo.

| Fecha | Tareas, responsable y dependencia |
| --- | --- |
| 22/10 | [53](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-53), fórmula backend — Esteban; [54](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-54), formulario de fórmula — Juan Manuel; [85](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-85), contrato de historial/recordatorios — Esteban. Dependen de la base clínica y sus contratos. |
| 23/10 | [16](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-16), historia de fórmula — Juan Manuel; [55](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-55), validación — Juan José. Dependen del recorrido clínico y fórmula. |
| 25/10 | [56](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-56), historial backend — Esteban; [57](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-57), historial propietario — Juan José. Dependen de atenciones y tratamientos. |
| 26/10 | [17](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-17), historia de historial — Juan José; [58](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-58), validación — Juan Manuel. Dependen del historial integrado. |
| 28/10 | [59](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-59), adendas backend — Esteban; [60](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-60), adendas gestión — Juan Manuel; [61](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-61), lectura propietario — Juan José. Dependen de contenido clínico original y permisos definidos. |
| 29/10 | [18](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-18), historia de adendas — Esteban; [62](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-62), validación — Juan José. Dependen de las adendas integradas. |

Se prioriza terminar el circuito de personal ya implementado y solicitado por Esteban. No se amplía el plan a clínica por la proximidad de sus fechas. No se revisaron exhaustivamente los criterios de esas 14 tareas porque no son candidatas de implementación de esta continuación.

## 3. Alcance y decisiones conservadas

**Incluido:** publicar la versión web existente, diagnosticar el 404 con evidencia, comprobar el gateway, conectar la URL de activación, verificar el envío existente y ejecutar los dos recorridos reales de personal. Corregir únicamente defectos reproducidos dentro de ese circuito y añadir las comprobaciones necesarias para esos defectos.

**Fuera:** crear otra aplicación, cambiar de proveedor de correo, abrir otra cuenta de hosting, crear dominios pagos, reconstruir formularios que ya existen, nuevos endpoints o migraciones, selector público de rol, creación de SUPER_ADMIN, edición de correo/rol, disponibilidad/citas/clínica, diplomas/fotos y aceptación completa del propietario. El registro público OWNER es independiente; no se le añade activación por invitación. Commit, push, merge y reorganización del despliegue continuo no forman parte de este incremento propuesto.

Se conserva la decisión de Esteban de **esperar acceso al Cloudflare del compañero**. Este plan no autoriza crear un reemplazo. La futura aprobación habilitaría los pasos descritos, incluidos la publicación en ese proyecto y los ajustes puntuales de Cloud Run; los pasos que requieren acceso o destinatarios quedan condicionados a disponer de ellos.

| Actor | Cuenta que puede crear | Resultado tras activación y login |
| --- | --- | --- |
| SUPER_ADMIN con `ADMINISTRATOR_MANAGE` | ADMINISTRATOR | Portal de veterinarios. |
| ADMINISTRATOR con `VETERINARIAN_MANAGE` | VETERINARIAN | Su propia agenda; no gestión de personal. |
| OWNER / VETERINARIAN | Ninguna cuenta de personal | No acceden a esas operaciones. |

El alta deja `PENDING_ACTIVATION`, sin contraseña ni acceso. Activar establece la contraseña y pasa a `ACTIVE`, sin iniciar sesión automáticamente. Cancelar invalida el enlace y deja la cuenta aplicable `DISABLED`; no la borra. Deshabilitar revoca sesiones e invitaciones; rehabilitar no revive sesiones anteriores. Se conserva la autorización definitiva del backend.

## 4. Contratos existentes y diseño previsto

Prefijo `/api/v1`, verificado en código y OpenAPI vivo:

| Operación | Resultado esperado |
| --- | --- |
| `POST /administrators` / `POST /veterinarians` | 201, cuenta/perfil/invitación/tarea persistidos. No acredita llegada del correo. |
| `POST /staff/{userId}/invitations` | 202; nueva invitación y enlace anterior invalidado. No acredita entrega. |
| `DELETE /staff/{userId}/invitations` | 204 y cancelación aplicable. |
| `POST /auth/account-activations` | Público, `token`, `password`, `confirmPassword`; 204, sin JWT de sesión. |
| `POST /auth/sessions` | Login normal, con rol y permisos devueltos por el backend. |

El enlace contiene un token opaco de 43 caracteres Base64URL, válido por 48 horas y de un uso. Abrir o recargar no lo consume; no existe un GET de validación que deba inventarse. El reenvío tiene espera mínima de 60 segundos y máximo cinco invitaciones en 24 horas, incluida la inicial. Contraseña de 15–128 puntos de código Unicode, sin recortar ni transformar, con confirmación exacta. Se conservan los formularios y mensajes del incremento anterior.

No se prevén módulos nuevos ni cambios de persistencia/contrato. Si aparece un defecto frontend, se revisarán solo la pantalla pública, navegación/enlaces, cliente de activación o gateway/exportador que lo produzcan. Antes de escribir código Expo se releerá la documentación exacta SDK 57 requerida por su `AGENTS.md`. Si la corrección exige una regla nueva, permisos distintos o modificar el backend funcional, se documentará y consultará antes de implementarla.

### Publicación

Se usará el proyecto existente, identificado en la configuración local como `grownupsvet-pwa`, después de confirmar su cuenta, nombre real y URL estable. No se asumirá que el nombre local demuestra acceso ni que la URL con hash de un despliegue es la dirección permanente.

La vía preferida para este incremento es **Wrangler con la exportación revisada**, registrando una huella del código y del artefacto porque los cambios siguen sin commit. No se disparará una compilación de `master` remoto como si incluyera el trabajo local. Se comprobarán la integración Git y GitHub Actions para evitar publicaciones competidoras; no se alterará su configuración silenciosamente. Cloudflare permite publicaciones manuales con Wrangler en proyectos existentes integrados con Git. [Carga con Wrangler](https://developers.cloudflare.com/pages/get-started/direct-upload/), [integración Git](https://developers.cloudflare.com/pages/configuration/git-integration/).

La exportación debe contener `index.html`, recursos Expo, `_worker.js`, `policy.mjs` y recursos PWA. La configuración local señala `npm run web:build` y salida `dist`; debe contrastarse con el proyecto remoto. Se conserva el gateway del mismo origen: navegador → Pages `/api/v1` → Cloud Run. CORS solo se ajustará si la arquitectura efectiva lo exige, con orígenes concretos; no se añadirá `*` para resolver errores.

La publicación fijará explícitamente **proyecto y rama de producción confirmados**; la referencia local actual indica `master`. No se usará el script `web:deploy` sin revisar sus parámetros: omite la rama y Wrangler podría inferir `codex/fix-web-cloudflare`, creando una vista previa en lugar de actualizar la URL estable. El despliegue de prueba y el de producción se identificarán por separado.

Con `_worker.js`, el worker controla el enrutamiento y debe servir también los recursos. La ruta de activación y su recarga se probarán realmente; no basta inferirlas de las reglas SPA generales, especialmente porque hay un `404.html` propio. [Modo avanzado](https://developers.cloudflare.com/pages/functions/advanced-mode/), [servicio de páginas y rutas](https://developers.cloudflare.com/pages/configuration/serving-pages/).

### Ajuste puntual de Cloud Run

Primero se inspeccionará la revisión activa y sus referencias, saneando cualquier salida. Se conservarán el digest de imagen vigente, base de datos/TLS, perfiles productivos, JWT, recuperación, SMTP, cuenta de servicio, recursos y política de CPU/instancias. No se reconstruirá ni desplegará el árbol backend con sus cambios locales para cambiar una URL.

| Configuración por comprobar | Tratamiento previsto |
| --- | --- |
| `STAFF_ACTIVATION_URL` | URL HTTPS estable del formulario, terminada en `/activate-account`, sin query ni fragmento. El backend ya añade `?token=…`. |
| `STAFF_INVITATIONS_ENABLED` | Verificar su valor actual; habilitar solo cuando pantalla y envío estén preparados. En `false` también bloquea la activación con 503 y pausa la entrega, no solo nuevas invitaciones. No afirmar que hoy está deshabilitado. |
| `STAFF_INVITATION_ENCRYPTION_KEY` | Comprobar binding de Secret Manager con versión numérica, sin `latest`; conservar clave estable. Si falta, preparar una clave propia de 32 bytes en Base64 mediante el mecanismo de secretos, sin publicarla. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`; referencias `MAIL_USERNAME`/`MAIL_PASSWORD` | Verificar proveedor y configuración existentes. Conservar Gmail si sigue siendo el proveedor activo, sin rotar credenciales por rutina. |
| `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `BACKEND_BASE_URL` | Revisar necesidad y alcance antes de cambiarlos; no alterar otros enlaces o consumidores incidentalmente. |

Se preferirá actualizar solo las variables/bindings necesarios, comparando antes/después. `scripts/deploy-google-cloud.ps1` recibe configuración completa y tiene defaults Mailtrap e invitaciones deshabilitadas: no se invocará sin todos sus parámetros para un cambio puntual. Google documenta que la actualización parcial conserva las variables ajenas y crea una revisión nueva. [Variables de Cloud Run](https://docs.cloud.google.com/run/docs/configuring/services/environment-variables).

La entrega durable usa PostgreSQL, un disparo después del commit y sondeo periódico. Se conservará la CPU fuera de peticiones y la política actual de instancias; este incremento no introduce escalado a cero ni cambia facturación/proveedor. La configuración real se verificará antes de tocarla, sin tomar el informe del 16/10 como estado actual.

## 5. Pasos después de la aprobación

1. **Preparar accesos y prueba acotada.** Confirmar acceso al proyecto Cloudflare acordado y a Cloud Run. Esteban identificará destinatarios autorizados y quién establecerá las contraseñas/confirmará recepción. No se pedirán claves por chat. Reservar dos direcciones de personal distintas y libres en el sistema; no reutilizar una dirección que ya pertenece a OWNER u otro usuario.
2. **Capturar el estado de partida.** Registrar revisión/digest, URL, variables no secretas pertinentes, nombres/versiones de bindings, política de CPU/instancias y despliegue frontend anterior recuperable. Revisar metadatos agregados de colas si se dispone del acceso adecuado, sin leer payloads/tokens. Esto define la diferencia prevista y la reversión.
3. **Revalidar y preparar el frontend.** Ejecutar `npm run check` y `npm run web:build`, registrar la huella del artefacto y verificar la exportación completa. Corregir solo fallos dentro del circuito. No modificar el backend, el lockfile o dependencias por rutina.
4. **Publicar y comprobar HTTPS.** Verificar primero un despliegue de prueba, cuando el proyecto lo permita, y publicar la misma exportación revisada en la URL estable. Probar login y `/activate-account` con token ficticio de formato válido, entrada directa y recarga. Verificar `/api/v1`, recursos, errores y cabeceras desde la publicación real antes de emitir invitaciones reales.
5. **Conectar y verificar invitaciones.** Ajustar URL/habilitación/binding necesarios en Cloud Run, con la imagen vigente. Comprobar diferencia saneada, revisión lista, salud y rutas públicas/privadas. Mantener referencias SMTP y claves existentes. Si la imagen no admite la configuración o falla la salud, detener la transición y revisar el defecto; no construir otra imagen silenciosamente.
6. **Recorrido real de administrador.** SUPER_ADMIN crea una cuenta de prueba → queda pendiente → se comprueba envío y recepción → la persona abre/recarga sin activarse → establece contraseña → activación 204 → login normal → portal de veterinarios.
7. **Recorrido real de veterinario.** El administrador activado crea un veterinario con datos profesionales ficticios identificados como prueba → correo recibido → activación → login → agenda propia, sin gestión de personal. No se publican turnos ni se crean citas para probar este circuito.
8. **Controles y regresión.** Ejecutar la matriz siguiente, corregir defectos reproducidos y repetir solo las comprobaciones afectadas. No forzar caídas de Gmail ni degradar Cloud Run para ensayar errores. Completar cierre de sesiones y dejar las cuentas de prueba deshabilitadas, salvo que Esteban las conserve para la demostración; no borrar filas ni inventar un endpoint de eliminación.
9. **Documentar y proponer seguimiento.** Entregar URL, versiones/huellas, pruebas y limitaciones. Actualizar guías e informe de verificación y proponer evidencia/estado para Jira según criterios demostrados. No enviar mensajes a integrantes ni cerrar tareas automáticamente por terminar el recorrido.

Si no llega el acceso a Cloudflare, podrán completarse las preparaciones autorizadas que no dependan de él. La publicación y el correo integrado seguirán pendientes; no se sustituirá esa prueba por un enlace local ni se creará otra cuenta por iniciativa propia.

## 6. Matriz de aceptación y evidencia

| Escenario | Comprobación prevista | Evidencia y límite |
| --- | --- | --- |
| Publicación | HTTPS, login, recursos y `/activate-account` abren por enlace directo y al recargar, sin 404. | URL estable e identificador/huella de despliegue; no incluir query con token real. |
| Creación y correo | Dos altas autorizadas, pendiente antes de activar y mensaje recibido por cada destinatario. | Separar persistencia 201/202, aceptación SMTP y recepción confirmada en buzón, incluyendo carpeta. Ninguna prueba sustituye a la otra. |
| Activación y login | Abrir/recargar no consume; contraseña válida consume una vez; nuevo login entra al área correcta. | Métodos/rutas/estados y capturas sin tokens, contraseñas o correos personales. |
| Enlace usado, reenviado o cancelado | Reutilizar falla; tras reenvío falla el anterior y funciona el vigente; cancelación impide activar. | Casos acotados con cuentas autorizadas; respetar espera y cupo. Cancelar/reenviar no se ensaya sobre personal real ajeno a la prueba. |
| Enlace ausente/malformado y errores de formulario | Mensaje comprensible, confirmación exacta y foco útil, sin activar ni perder datos indebidamente. | Escritorio y celular, teclado y ampliación. No equivale a auditoría formal de accesibilidad. |
| Otra sesión abierta | Activar no autentica ni reemplaza la sesión existente; respuestas tardías no afectan a otra cuenta. | Recorrido real del caso principal y pruebas automatizadas existentes para carreras controladas. |
| Permisos y desactivación | OWNER/VETERINARIAN no gestionan personal; administrador no crea administradores. Deshabilitar la cuenta de prueba invalida su sesión; rehabilitar no revive la sesión anterior. | API sigue siendo autoridad. No probar permisos clínicos que todavía no están definidos/implementados. |
| Espera, cuota, vencimiento, concurrencia y fallos | Conservar cobertura automatizada de 60 s, cinco/24 h, 48 h, consumo concurrente y errores SMTP/503. Añadir prueba solo si una corrección lo requiere. | Entorno controlado. No agotar cupos reales, esperar 48 h artificialmente en producción ni afirmar vencimiento con reloj real si no se observó. |
| Regresión | OWNER conserva acceso/mascotas y VETERINARIAN su agenda; cierre/restauración de sesión no cambia de identidad. | No acredita todas las escrituras, perfil/foto o recuperación del propietario. |
| Caché y datos sensibles | API y activación no quedan cacheadas como datos privados; token retirado al terminar/salir y no enviado en Referer. | Verificar cabeceras y comportamiento de la publicación; evitar tokens reales en capturas, HAR, logs o informes compartidos. |

La consulta del estado del envío utilizará registros o metadatos existentes con acceso autorizado, sin inventar una API pública de entrega. Si no se consigue evidencia SMTP, se indicará ese límite aunque exista confirmación del buzón. Si SMTP acepta pero el usuario no recibe, el recorrido queda pendiente de diagnóstico; no se crea otra cuenta ni se reenvía repetidamente por automatismo.

No se regenera OpenAPI por un cambio de URL/configuración. Si aparece un defecto que exige cambiar el contrato, se detendrá esa ampliación para revisar alcance y mantener el contrato desde Java, nunca editando el JSON generado a mano. Maven solo será necesario si se autoriza y modifica backend; su suite no se ejecuta por defecto para publicar un frontend ya existente.

## 7. Riesgos, dependencias y reversión

- **Acceso pendiente:** se conserva Cloudflare del compañero. Su ausencia bloquea publicación, no la redacción del plan. El permiso de colaboración en GitHub no demuestra acceso a Cloudflare.
- **Destinatarios y contraseñas:** hay que acordar las cuentas de prueba antes de crear perfiles o enviar invitaciones. No leer secretos de Windows, archivos privados o sesiones para deducir direcciones/contraseñas. El consentimiento de una prueba anterior de recuperación no se extiende automáticamente a cualquier destinatario.
- **Fuente sin commit:** publicar el artefacto actual con huella y copia reproducible de sus fuentes autorizadas; no describirlo como commit remoto. No incluir secretos, `node_modules`, credenciales o datos de usuarios en esa copia. No publicar el backend local incidentalmente.
- **Configuración real desconocida:** se inspeccionará antes de modificar. Si no hay acceso para verificar imagen, referencias o política de ejecución, dejar pendiente el ajuste remoto y explicar la limitación.
- **Entregas pendientes y claves:** no rotar claves de cifrado/HMAC/JWT ni cambiar SMTP por rutina. Una clave distinta puede volver ilegibles tareas ya persistidas. No reenviar automáticamente trabajos antiguos.
- **Retorno de versión:** registrar el despliegue frontend anterior y revisión Cloud Run compatible. Si hay fallo, detener nuevas pruebas y restaurar solo la diferencia pertinente, preservando claves y esquema. La versión frontend anterior no es candidata válida si vuelve al 404 o carece de activación: conservar una versión funcional de esa ruta y su gateway aunque se revierta el resto. No degradar a una imagen antigua incompatible con colas/esquema 0.9.
- **Enlaces ya emitidos:** conservar el dominio y la ruta de activación durante las 48 h desde la última emisión que siga vigente; no borrar una publicación ni redirigir a un destino que pierda el token. Si se necesita invalidar y reenviar, hacerlo con destinatarios identificados y límites vigentes. `STAFF_INVITATIONS_ENABLED=false` hace que la activación devuelva 503 y pausa la entrega; no es una reversión inocua ni se aplicará automáticamente mientras se prometen enlaces operativos.
- **Ampliación de alcance:** si aparece una decisión de negocio, cambio de proveedor/cuenta, nueva infraestructura o defecto fuera del circuito aprobado, se explica y consulta antes de implementarlo. No se abren reglas clínicas para resolver este flujo.

## 8. Entregables y condición de cierre

En la implementación autorizada se actualizarían la guía frontend/proxy, la guía de configuración Cloud Run cuando corresponda y un informe de aceptación con URL estable, huella del código/artefacto, revisión/digest, diferencia de configuración saneada, tabla de escenarios y evidencias sin secretos. Las pruebas anteriores simuladas y las nuevas reales se mantendrían separadas. No se publicará evidencia de credenciales ni contenido privado de correo.

Este circuito se considerará completo cuando **ambos tipos de personal reciban su invitación real, activen y entren a su área desde la publicación HTTPS**, los controles esenciales pasen y la configuración ajena se conserve. Un build verde, un 201/202 o SMTP aceptado por sí solos no bastan. Si falta acceso, recepción o revisión, el informe conservará ese pendiente.

La finalización técnica del circuito no cierra automáticamente SCRUM-10/26/35/75/80: cada una conserva sus otros criterios, revisión compartida y, cuando aplica, permisos clínicos diferidos. Se propondrán actualizaciones respaldadas por evidencia sin modificar fechas, responsables ni sprints por inferencia.

**Parada actual:** plan entregado. No se ejecuta ninguno de los pasos de implementación hasta recibir la aprobación de Esteban, tal como solicitó expresamente.
