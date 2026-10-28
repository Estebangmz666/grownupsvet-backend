# Plan previo: codificación del contenido de correo

28/10/2026. El usuario confirma recepción de los tres correos y reporta caracteres corruptos en instrucciones y motivo del control. El texto fijo —«prescripción», «Vía», «Fecha»— se ve correctamente. La investigación inicial fue solo lectura de fuentes/bytes/tests; no ejecutó SMTP, API, BD o Maven.

## Causa evidenciada antes de modificar

1. Los bytes de `output/backend-cierre-2026-10-28/acceptance/acceptance.py` decodifican con UTF-8 estricto, pero **sus literales ya contienen Unicode incorrecto**. El AST Python obtiene, antes del JSON:
   - instructions, línea542 en el corte inspeccionado: el segmento de «clínico» contiene42 caracteres no ASCII en vez del único `í` esperado; aparecen U+00C3, U+0192, U+00C6 y otras secuencias de recodificación.
   - control.reason, línea543: el segmento de «aceptación» presenta la misma corrupción acumulada en vez de `ó`.
   - también se detectó mojibake en el motivo estático de la cita futura de P2Current.
2. La serialización `json.dumps(..., ensure_ascii=False).encode('utf-8')` y su parse de vuelta preservan exactamente esos valores ya corruptos. No los generan ni corrigen. La comparación privada de API/BD contra la intención esperaba esa misma cadena, por lo que podía aprobar persistencia sin comprobar su ortografía Unicode.
3. La copia histórica del25 contiene los dos literales correctos: «Prueba del sistema sin uso clínico real; no administrar» y «Control ficticio de aceptación de software». Se verificó por bytes y AST, sin ejecutarla ni editarla. SHA256 histórico observado: `47e3a246172a407af3a58400574524bb2df6df997bfe62d96961d2191c2d6a40`.
4. Los fuentes actuales `TransactionalEmailTemplates.java`, `AppointmentEmailDispatcher.java` y `ClinicalReminderEmailDispatcher.java` son UTF-8 estricto sin esos marcadores corruptos; sus bytes coinciden con el commit fuente desplegado `960c8898`. Los textos de prescripción/Vía están correctos. Ambos dispatchers usan `MimeMessageHelper(message,true,"UTF-8")` y plain+HTML. Invitación y recuperación también seleccionan UTF-8 explícitamente.
5. El parent Maven Spring Boot4.1.1 local declara `project.build.sourceEncoding=UTF-8` y `project.reporting.outputEncoding=UTF-8`; Docker usa ese Maven/Java25. No se encontró un override de compilación que explique esta corrupción localizada en campos dinámicos.

La causa primaria comprobada es **entrada de ensayo mal recodificada en la copia privada del28 antes de JSON/persistencia**, coherente con la muestra del usuario. No se dispone de EML original; no se atribuye al transporte un defecto que esta evidencia no demuestra. No se usa la apariencia de Get-Content/consola como prueba.

## Alcance autorizado y secuencia de implementación

El agente principal autorizó los siguientes pasos después de este plan:

1. Guardar hashes de procedencia del privado28 y del histórico25. Examinar mediante UTF-8 estricto/AST todos los literales estáticos de los fixtures28 relevantes, incluidos mensajes del launcher, y corregir únicamente sus valores canónicos conocidos. Usar escritura UTF-8 explícita y, donde facilite revisar diferencias en herramientas Windows, escapes Unicode de código fuente para `í/ó/ñ`.
2. No editar el histórico25, checkpoints, payloads guardados, fingerprints, registros finalizados o correos ya emitidos. Mantener intacta la intención existente al reanudar: los literales corregidos se usan solo cuando se construye una intención futura nueva. No reutilizar una clave anterior con payload corregido ni aplicar reparación automática a datos clínicos.
3. Coordinar con el agente dueño de R2: su actualización de receipt/report es independiente; no sobrescribir report() o estado privado. Cambios puntuales a literales y nuevas pruebas offline, sin fases de aceptación reales.
4. Añadir una suite Python offline propia que capture el payload generado antes del envío, compare las frases correctas por igualdad Unicode, compruebe JSON UTF-8 ida/vuelta y conserve literalmente una intención antigua ya guardada, incluso si contiene mojibake. Probar también el motivo de cita P2Current y verificar que no se ejecuten HTTP/BD/SMTP.
5. Añadir un test Java nuevo, independiente de clases de tests compartidas por otros agentes, que componga el MIME real en memoria usando `TransactionalEmailTemplates` y `MimeMessageHelper` UTF-8. Serializar con `writeTo`, reparsar con `MimeMessage(Session,InputStream)` y recorrer las alternativas plain/HTML. No abrir sockets ni usar un transport SMTP.
6. Ejercitar citas, prescripción, control, invitación y recuperación con nombres/textos sintéticos con `áéíóúñ`, puntuación y caracteres que requieran escape HTML. Comprobar Subject decodificado, charset UTF-8 de ambas partes, contenido esperado y ausencia de doble escape. Un caso de entrada corrupta debe preservarse como entrada, evidenciando que la plantilla no «adivina» cómo modificar datos del profesional.
7. Ejecutar pruebas Python en aislamiento. La prueba Java puede compilarse/ejecutarse con dependencias locales ya disponibles en un runner privado, sin Maven; el test normal se ejecutará con Maven solo cuando lo coordine root. Registrar hashes posteriores y alcance de cada verificación.

## Criterio para tocar producto

No cambiar templates, senders, charset global, API o BD por esta incidencia. No añadir un «sanitizador» de mojibake: cadenas válidas de usuarios no deben recodificarse heurísticamente. Si la prueba MIME en memoria demuestra un defecto real con entrada Unicode correcta, detenerse, registrar el fallo y presentar un delta de producto antes de implementarlo.

## Cobertura existente y evidencia pendiente

- `TransactionalEmailTemplatesTests` ya verifica textos españoles y escape HTML, pero sus entradas canónicas no detectan corrupción previa del fixture Python.
- `SmtpStaffInvitationEmailSenderTests` construye MIME UTF-8 en memoria; recuperación incluye pruebas SMTP local, que no se ejecutarán para esta tarea.
- Los tests de dispatchers de citas y clínico capturan mensajes y validan entrega/lease/expiración; el nuevo test añade explícitamente serialización/reparse de bytes sin transporte.
- Las comparaciones del checker privado siguen acreditando igualdad e idempotencia de la intención original; la prueba nueva de fixture acredita además que las futuras intenciones parten de las palabras Unicode correctas. Son dos propiedades distintas.

Los tres correos recibidos y sus datos finalizados permanecen como evidencia del ensayo con incidencia de contenido. No se reenviarán para comprobar esta corrección. El resultado final distinguirá recepción reportada por el usuario, origen del contenido y prueba MIME offline.

## Addendum de resultado, posterior al plan

Se corrigieron únicamente tres literales de futuras intenciones Python28 y seis mensajes estáticos del launcher28. El histórico25, los checkpoints/intenciones guardados y los registros finalizados permanecen intactos. Cinco tests offline nuevos comprobaron payload antes de JSON/envío, roundtrip UTF-8 y conservación literal de una intención antigua con su clave y texto corrupto; la ejecución combinada fue de28 casos satisfactorios. Parser PowerShell: cero errores.

La clase nueva `TransactionalEmailMimeEncodingTests` ejecutó sus cinco casos satisfactoriamente dentro del focal coordinado por root de149 tests/8 suites, sin fallos, errores o skips. Se verificaron MIME real serializado/reparseado en memoria, subject y alternativas UTF-8 para ambos canales y plantillas compartidas. No se realizaron envíos ni cambios de Java productivo. El resultado confirma que entrada Unicode correcta conserva sus caracteres; una entrada ya corrupta se conserva como dato, sin reparación heurística.

Los resultados previos no se sobrescriben. Se agrega evidencia posterior en `output/backend-cierre-2026-10-28/encoding-validation-addendum.json`; el reporte offline previo mantiene su estado histórico de MIME pendiente.
