# Verificación de Unicode en los correos — 28/10/2026

Los tres avisos del ensayo llegaron al usuario. Los textos dinámicos de control e instrucciones contenían mojibake, mientras que las etiquetas fijas con acentos se mostraban correctamente. La investigación identificó las cadenas incorrectas en la copia privada del fixture **antes de serializar JSON**. El [plan y diagnóstico previo](plan-codificacion-correos-2026-10-28.md) conserva la causa, procedencia y alcance.

Se corrigieron tres literales de futuros datos ficticios y seis mensajes del launcher, usando codificación UTF-8 explícita. La suite Python offline aprobó **28 pruebas**, cinco nuevas: comprueba las palabras canónicas, el payload antes del envío, JSON UTF-8 ida/vuelta y la conservación literal de intenciones anteriores al reanudarlas. No se modificaron los checkpoints, fingerprints, la atención finalizada o los correos ya emitidos.

## MIME real en memoria

`TransactionalEmailMimeEncodingTests` pasó **cinco pruebas**, cero fallos, errores u omisiones, dentro del focal de **149 pruebas y ocho suites**. Construye mensajes multipart con `MimeMessageHelper` UTF-8, los serializa a bytes mediante `writeTo`, los vuelve a interpretar con Jakarta Mail y compara Subject, charset y texto de las alternativas plain/HTML.

Los casos cubren instrucciones de prescripción, motivo de control, asunto y contenido de citas, invitación y recuperación. Incluyen `áéíóúñ`, nombres sintéticos y caracteres que requieren escape HTML. Un caso conserva una entrada ya dañada: el producto no debe adivinar ni reescribir datos profesionales mediante recodificaciones heurísticas.

Las pruebas no construyen transporte SMTP ni abren sockets. Acreditan el formato MIME con entrada Unicode correcta; no equivalen a un nuevo envío o a una segunda inspección del buzón. Las fuentes de plantillas y dispatchers no necesitaron cambios: usan UTF-8 y coinciden con las fuentes de la revisión que produjo los correos recibidos.

## Evidencia y límites

| Artefacto conservado | SHA256 |
| --- | --- |
| Fixture privado `acceptance.py` corregido | `46e64904b038335545c3b289965f9da3f6b26e70c65af4896fbc22b4b417e3be` |
| Launcher privado corregido | `2c02a98f021b67fe2776f25719a51f1cb852df78d17fdbb1de7d8bae66d9f604` |
| XML del focal MIME archivado | `4c85ff72acae3b545e3fdef8315e8dfc27529ef79f2c4f321f4505c4087a2d78` |

Los XML y su manifiesto están en `output/backend-cierre-2026-10-28/r5-c1-focal-reports`, fuera del repositorio. El histórico del 25/10 se conservó intacto. El corte de aceptación desplegada sigue registrando la incidencia observada: esta verificación posterior acredita la corrección del fixture y el roundtrip MIME, sin convertir retrospectivamente esos correos en mensajes distintos de los recibidos.
