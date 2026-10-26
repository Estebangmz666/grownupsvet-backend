# Plan I7a.1: métricas de escrituras confirmadas

25 de octubre de 2026, America/Bogota. Este plan se escribe antes de implementar el incremento. Amplía la observabilidad local sin cambiar rutas, DTOs, permisos, estados ni reglas de negocio de las APIs.

## Objetivo y contrato de la métrica

Conservar la latencia HTTP existente por método, URI parametrizada, estado y revisión. Añadir únicamente un contador operativo de mutaciones confirmadas en PostgreSQL: `grownupsvet.business.commits`, exportado como `grownupsvet_business_commits_total`.

La única etiqueta propia será `operation`, con siete valores cerrados: `owner_signup`, `pet_create`, `appointment_create`, `clinical_draft_create`, `clinical_draft_save`, `clinical_treatment_save` y `clinical_finalize`. Las etiquetas comunes actuales añaden aplicación/revisión. El evento interno solo contiene el enum de operación; nunca identificadores, correos, tokens, parámetros ni contenido clínico.

## Implementación prevista

1. Crear en `shared/observability` el enum cerrado, el evento interno sin datos de recursos y un listener con contadores registrados al construir el componente. El listener usará `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=false)`, sin I/O ni nuevas escrituras de BD. Aislar cualquier `RuntimeException` al registrar o incrementar una métrica: no convertirá una escritura confirmada en respuesta 500 ni afectará negocio. El log contendrá únicamente el tipo de excepción, sin payload ni mensaje original; una prueba específica simulará un contador averiado.
2. Revisar constructores y fixtures directos antes de añadir dependencias. Usar `ApplicationEventPublisher` existente donde esté disponible y añadirlo a los servicios restantes mediante constructor. Publicar únicamente tras las escrituras efectivas de registro, mascota, cita, borrador, guardado, tratamiento y finalización. Ninguna rama de replay emitirá otro evento.
3. Mantener límites transaccionales actuales. La publicación dentro de una transacción registra una notificación; el contador se incrementa después de que el commit tenga éxito, incluyendo la evaluación de constraints diferidos. El rollback exterior y los fallos tardíos no incrementan la métrica. Eventos publicados sin transacción no se cuentan.
4. Añadir pruebas de integración en un esquema PostgreSQL exclusivo con fixtures confirmados y sin transacción exterior automática: comprobar contador antes/dentro/después de commit, rollback explícito, fallo diferido al commit, escrituras reales de los siete servicios y replays sin duplicados. Usar conexiones independientes cuando haya carreras.
5. Verificar mediante HTTP real del puerto de gestión que el scrape incluye los siete valores y no datos privados. Conservar el confinamiento del puerto y el histograma de 500 ms.
6. Coordinar archivos de observabilidad con su agente propietario. Ampliar la allowlist Alloy para incluir la nueva familia y añadir al dashboard una tasa de commits por operación, sin porcentaje de aceptación. Documentar las limitaciones y ejemplos de consulta.
7. No ejecutar Maven en paralelo. Avisar a la raíz cuando todos los archivos estén estables para la ejecución focal y después la regresión completa.

## Garantías y límites

- Se cuenta una mutación efectiva por emisión confirmada; las solicitudes rechazadas, las transacciones revertidas y los replays no crean éxitos ficticios.
- El contador es observabilidad en memoria, no un registro durable ni un mecanismo de entrega exactamente una vez. Un proceso puede caer después del commit y antes de incrementar o antes del scrape. Los reinicios reinician el contador; Prometheus debe usar `rate`/`increase`.
- No se calculan M1/M4/M7 dividiendo commits por HTTP: esas peticiones incluyen inválidas, replays y fallos de transporte. M2/M5/M8 requieren escenarios conocidos y comprobación persistida; M3/M6/M9 conservan muestra, carga y latencias separadas por operación. Sin muestra y denominador válidos, se informa `No evaluado`.
- El contador de finalizaciones no equivale a correos entregados. La aceptación SMTP y la recepción tienen evidencias distintas.
- No se crea infraestructura, cambia IAM, despliega ni introduce consumo remoto en este incremento.

## Criterios de aceptación

Los contadores avanzan solo al commit real; cada operación tiene una prueba persistida; replay y rollback mantienen el valor; un fallo diferido posterior a emitir el evento no lo incrementa; el scrape expone exclusivamente etiquetas acotadas; las APIs y reglas actuales conservan comportamiento. Alloy y el panel admiten la nueva familia sin convertirla en un resultado académico.

## Fuentes técnicas verificadas

- [Eventos vinculados a transacciones de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): fase de commit y omisión sin transacción cuando no se activa fallback.
- [Contadores Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/counters.html): contador de eventos y uso de tasas ante reinicios.
- [Métricas Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html): registro y etiquetas comunes bajo las dependencias del proyecto.

## Resultado

Implementación preparada: enum/evento/listener, siete emisiones efectivas en seis servicios, seis pruebas PostgreSQL adicionales en la suite transaccional, dos pruebas de aislamiento de fallos del observador y una prueba de scrape tras registro real. Se amplió la allowlist Alloy y el dashboard conserva los nueve paneles previos más el panel de commits. JSON del dashboard válido, diez IDs únicos y `git diff --check` sin errores. Pendiente de ejecución Maven focal coordinada; no se ha ejecutado una suite en paralelo ni se presenta evidencia de producción.
