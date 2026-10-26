# Plan I7a: observabilidad confinada y preparación de entrega

25 de octubre de 2026, America/Bogota. Este plan se escribe antes de modificar la implementación. Amplía I7a del plan general de cierre; no acredita despliegue ni resultados M1–M9.

## Alcance y decisiones

Se reutilizan Spring Boot 4.1.1, Actuator y su BOM. El registro Prometheus se añade sin fijar una versión independiente. Un perfil opcional `observability` sirve métricas en un segundo servidor ligado a `127.0.0.1:9091`; la API continúa en su puerto actual. La recolección no usa JWT de personas ni consulta de cuentas/revocaciones. No se añaden permisos clínicos ni un rol de operación.

La cadena de seguridad de observabilidad se limita al puerto real de gestión, incluso cuando los tests asignan un puerto aleatorio. Permite únicamente GET de `/actuator/prometheus` y probes mínimos; deniega el resto. La API deniega expresamente Prometheus. El perfil publica `/livez` y `/readyz` mínimos en el puerto API para conservar probes reales al separar Actuator. Sin perfil, las rutas/configuración de probes existentes permanecen.

Un validador de inicio impide activar el perfil con gestión fuera de loopback, sin puerto separado o con exposición de otros endpoints. Se verifica dirección y puerto antes de iniciar servidores y se comprueba aislamiento por HTTP real. Las etiquetas se limitan a aplicación/revisión y dimensiones HTTP estándar con URI parametrizada. Nunca contienen datos clínicos, identificadores de personas/mascotas, correo, tokens, URL cruda o parámetros.

## Secuencia de implementación

1. Añadir `micrometer-registry-prometheus` al POM. Crear `application-observability.yaml` con health/Prometheus, segundo puerto, etiquetas de aplicación/revisión y histogramas HTTP acotados, incluido bucket de 500 ms.
2. Crear configuración de seguridad y validación específicas del perfil. Añadir a la cadena API únicamente la denegación explícita de Prometheus. Las probes adicionales se permiten por la cadena opcional, exclusivamente GET exacto.
3. Crear prueba de integración con PostgreSQL real y dos puertos aleatorios: scrape sin JWT permitido solo en gestión; API sin token denegada y con cuenta real denegada; métodos alternativos y endpoints de gestión no expuestos denegados; API conserva autenticación; probes sin detalles. Verificar métricas después de tráfico HTTP real, bucket de 500 ms y URI de plantilla. Añadir pruebas de configuración insegura y matcher de puerto.
4. Preparar configuración `RunMonitoring` para sidecar, consultas/panel Grafana y guía operativa con las decisiones de infraestructura abiertas. No ejecutar gcloud, crear IAM/secretos, desplegar ni descargar credenciales.
5. Coordinar ejecución Maven con la raíz para evitar compilaciones/suites simultáneas. Ejecutar primero las pruebas focalizadas; la raíz decide regresión final. Validar archivos de configuración y revisar diferencias.

## Criterios de aceptación

- Sin perfil, Prometheus no es accesible y se conservan probes previas.
- Con perfil, dirección loopback y dos puertos comprobados; Prometheus devuelve formato válido por el puerto de gestión sin autenticar una persona.
- El puerto API no entrega métricas, aunque el usuario tenga sesión válida. Ningún endpoint administrativo adicional queda accesible.
- `/livez` y `/readyz` devuelven únicamente estado, sin conexiones/configuración/identidades.
- El scrape incluye histogramas HTTP y límite 500 ms, aplicación/revisión y rutas parametrizadas; no publica valores sensibles usados en solicitudes.
- Configuración que amplíe dirección/exposición o reutilice el puerto API falla de forma explícita.
- Panel y guía distinguen latencia aproximada por histogramas de P95 exacto y de los indicadores que requieren verificación persistida.

## Preparación de I7b y límites

El sidecar de Cloud Run podrá recolectar por instancia en la red compartida. Antes de activarlo se debe conservar la especificación actual del servicio, incorporar el perfil/probes, el colector y la configuración, revisar recursos y verificar IAM mínimo. El archivo de configuración no contiene credenciales; Secret Manager es el mecanismo documentado para entregarlo al colector administrado. El despliegue existente no se sustituye por una plantilla incompleta.

El usuario aprobó el 25/10 utilizar servicios administrados, priorizando opciones gratuitas. Se investigarán la cuenta disponible y los límites reales antes de crear recursos o habilitar consumo. Continúan pendientes: cuenta/stack Grafana y lectores; presupuesto, retención y recursos; proyecto, identidad e IAM; ventana de evaluación y carga. La aprobación de herramientas administradas no demuestra elegibilidad o saldo gratuito de una cuenta.

M1/M2/M4/M5/M7/M8 necesitan escenarios y oráculo de persistencia; M3/M6/M9 requieren perfil de carga y distribución por operación. Sin muestra válida se informa `No evaluado`. Un estado 2xx no demuestra persistencia o integridad.

## Referencias técnicas consultadas

### Ampliación previa: alternativa administrada con cuota gratuita

Antes de preparar esa alternativa se verificaron precios oficiales: la ingesta de Google Managed Prometheus cuesta USD 0.06 por millón de muestras en el primer tramo y no tiene cuota gratuita aplicable; Grafana Cloud Free publica 10 000 series activas y retención de 14 días. La prioridad del usuario conduce a preparar Alloy como recolector de formato Prometheus y remote_write hacia Grafana Cloud Free, sin eliminar la configuración Google si se acuerda usar créditos allí.

La preparación añadirá únicamente un archivo Alloy sin valores de credenciales, con scrape loopback cada 60 segundos, etiquetas servicio/revisión y filtro de métricas necesarias para el panel. Endpoint, usuario técnico y token con alcance de escritura se resolverán en runtime desde variables/montajes secretos. No se crean cuentas, credenciales, contenedores o recursos. La guía explicará límites, coste potencial del sidecar/egreso y la falta de sesión conectada; no se declara coste total cero ni compatibilidad académica de un servidor Prometheus propio por equivalencia de protocolos.

La configuración Alloy requiere validación con la versión fijada antes de desplegar; preparar el archivo no acredita ejecutar ese recolector. Se mantendrán TLS y puerto de administración del recolector confinado. Verificar el número real de series antes de activar envío y mantener el plan Free, sin conversión automática a Pro.

- [Métricas de Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).
- [Puerto/dirección de gestión](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html).
- [Seguridad y probes adicionales](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
- [Histogramas de Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).
- [Sidecar Prometheus de Cloud Run](https://docs.cloud.google.com/run/docs/monitoring-managed-prometheus-sidecar).
- [Red compartida de contenedores](https://docs.cloud.google.com/run/docs/deploying).
- [Grafana y Prometheus administrado](https://docs.cloud.google.com/stackdriver/docs/managed-prometheus/query).

Las páginas actuales corresponden a la rama Boot 4.1 (publicada como 4.1.2 al consultar); la implementación se compila y prueba contra las dependencias 4.1.1 del proyecto.

## Resultado

Implementación y artefactos preparados: dependencia bajo BOM, perfil confinado, validación de inicio, cadena de seguridad y pruebas HTTP reales de dos puertos; configuración de colector local/Cloud Run, guía y dashboard Grafana de nueve paneles. Se validó sintaxis de los tres YAML con PyYAML y del dashboard con `json.loads`, comprobando IDs únicos y variables.

La ejecución focal coordinada por la raíz aprobó 99 pruebas. De ellas, I7a aporta **7: 3 de configuración y 4 HTTP reales, cero fallos/errores/omisiones**; los reportes están en `target/surefire-reports/TEST-*Observability*.xml`. Se comprobaron los dos puertos, scrape sin JWT/consulta de cuentas, denegación pública con roles reales, probes mínimas e histograma/privacidad tras tráfico HTTP.

`promtool` no estaba instalado y Docker Desktop no tiene un motor Linux disponible; no se ejecutaron Prometheus/Grafana ni se certifica su configuración en runtime. No se creó infraestructura ni hay evidencia productiva nueva.
