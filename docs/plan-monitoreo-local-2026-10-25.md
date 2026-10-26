# Plan previo: Prometheus y Grafana locales sobre el backend desplegado

25 de octubre de 2026, America/Bogota. Cambio de alcance autorizado: presupuesto adicional de **US$0**, sin prueba de Grafana Cloud, sidecar ni nuevos servicios de nube. Este incremento prepara exclusivamente el backend; los procesos locales y sus scripts se implementan por separado.

## Base inspeccionada

- Spring Boot 4.1.1, Actuator y registro Prometheus ya presentes en Maven. No se agrega dependencia ni endpoint MVC.
- `application-observability.yaml` publica métricas únicamente por loopback/puerto separado. Esa alternativa se conserva.
- `SecurityConfiguration` protege la API con JWT humanos y deniega Prometheus en el puerto de aplicación. `ObservabilitySecurityConfiguration` tiene una cadena prioritaria limitada al puerto interno y sondas adicionales.
- `ApiSecurityErrorHandler` entrega errores JSON sin redirecciones y con no-store. Se reutilizará para los rechazos del token máquina.
- Las métricas HTTP ya tienen histogramas, umbral 500 ms y etiquetas acotadas de aplicación/revisión. No se cambian métricas clínicas, contratos ni las 47 rutas OpenAPI.

## Contrato operativo antes de implementar

El perfil nuevo `monitoring` expone health y prometheus en el mismo puerto de la aplicación, normalmente 8080. `GET /actuator/prometheus` requiere `Authorization: Bearer <token-máquina>`, con token aleatorio de 32 bytes representado como Base64URL sin padding, 43 caracteres. El token original queda únicamente en el equipo que ejecuta Prometheus. El backend recibe solo `MONITORING_TOKEN_SHA256`, 64 caracteres hexadecimales SHA256 de esa representación exacta.

Una cadena de Spring Security de mayor prioridad atiende exclusivamente `/actuator/prometheus` y su espacio de subrutas; solo el GET exacto puede llegar al endpoint. El filtro se construye dentro de esa cadena, sin registrarlo como bean/servlet filter global. No instala resource server JWT, no consulta cuentas y no utiliza sesiones humanas.

El filtro exige un único header Authorization, un token con la forma acordada y comparación de los hashes mediante `MessageDigest.isEqual`. Missing, token inválido, JWT humano y el propio hash usado como token producen 401 genérico. Otros métodos y subrutas se deniegan. Nunca se registra el token, su hash o el header. La autenticación interna contiene una identidad técnica sin credenciales; no modifica permisos humanos ni emite JWT.

El token máquina no autoriza ninguna ruta clínica o de usuarios: esas solicitudes siguen pasando exclusivamente por la cadena JWT existente. Las respuestas de métricas y errores se marcan no-store; la configuración de CORS no concede acceso de navegador al token operativo.

## Implementación prevista

1. Añadir `application-monitoring.yaml` con exposición exacta `health,prometheus`, salud mínima y referencia al hash de entorno. No definir un puerto de gestión distinto ni activar sondas públicas nuevas; mantener `/actuator/health/liveness` y `/actuator/health/readiness`.
2. Extraer únicamente las métricas comunes de `application-observability.yaml` a un YAML importado por ambos perfiles: etiquetas, límite de URI e histogramas/SLO. Conservar su puerto interno y sus reglas de seguridad.
3. Añadir un validador de configuración activo solo con `monitoring`: hash obligatorio y válido, incompatibilidad con `observability`, rechazo de puerto de gestión separado, base/context path alterados, exposición ampliada, detalles de salud o niveles de acceso distintos de los previstos.
4. Añadir cadena prioritaria y filtro dedicado. Mantener la denegación actual de Prometheus en la cadena general como defensa cuando el perfil no esté activo.
5. Probar mediante HTTP real y PostgreSQL el scrape autorizado, contenido e histograma de 500 ms, ausencia de datos personales, rechazo de claves/JWT/métodos/subrutas y aislamiento respecto del validador de cuentas. Validar el arranque negativo ante configuraciones incorrectas.
6. Congelar fuentes y coordinar Maven con el agente principal. No desplegar ni publicar durante este incremento; la imagen existente debe reconstruirse después porque se agrega comportamiento operativo nuevo.

## Verificación y límites

- Tests de configuración con contexto pequeño: hash ausente, blanco, longitud/formato incorrectos; ambos perfiles; puerto separado; exposición wildcard/extra y overrides de rutas/salud.
- Test de servidor real con perfil `test,monitoring`, hash/token ficticios, clave JWT efímera y PostgreSQL de pruebas. Nunca usar tokens o credenciales reales.
- Scrape válido: 200, formato Prometheus, no-store, histogramas con bucket 0.5 y métricas comunes. Tráfico con identificadores/query privados no debe introducirlos en etiquetas.
- Scrapes con JWT de OWNER/VETERINARIAN/ADMINISTRATOR/SUPER_ADMIN: 401 sin consultar el validador JWT de cuentas. El token máquina enviado a una ruta API protegida da 401.
- Sondas anteriores siguen disponibles sin JWT y sin detalles. No se modifican controladores clínicos, POM ni OpenAPI.
- La prueba local no acredita todavía el scrape de Cloud Run ni gráficas de métricas desplegadas; esos pasos requieren la revisión y ejecución del despliegue preparado por el agente principal.

## Decisiones técnicas

La separación por SecurityFilterChain evita depender de sesiones de personas y limita la credencial técnica a una sola operación de lectura. Guardar solo el hash permite rotar el token sin almacenar su valor original en el backend. Un filtro creado dentro de la cadena evita la doble ejecución que puede producir el autorregistro de filtros bean en Spring Boot. No se publica una API de configuración de tokens.

Fuentes primarias: [cadenas y filtros de Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html), [endpoints de Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [métricas Prometheus de Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) y [MessageDigest de Java](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html).

## Corrección posterior a revisión independiente — plan previo

La revisión detectó que los grupos health `liveness` y `readiness` pueden sobrescribir `show-details` y `show-components` globales. Aunque estos sean `never`, un override de grupo podría añadir información a las sondas públicas. Antes de modificar código se acuerda rechazar esos valores de grupo cuando sean distintos de `never`, y rechazar `additional-path` de grupo para conservar las rutas existentes. Se añadirán casos negativos de configuración y un control positivo con valores explícitos `never`; después se repetirán los tests de configuración y HTTP reales de monitoring. La regresión completa previa de 706 pruebas no se atribuye a esta corrección posterior.
