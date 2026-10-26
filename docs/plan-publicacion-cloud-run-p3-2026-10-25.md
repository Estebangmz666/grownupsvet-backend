# Plan previo de publicación de P3 en Cloud Run

25 de octubre de 2026, America/Bogota.

## Estado y alcance autorizado

El CORE `4bdaf2bcec024dc69b07b6f5665ffff36e89904f` pasó 695 pruebas y sus dos ejecuciones remotas de CI. El frontend compatible con COMPLETED ya se integró en su PR 1 y está publicado en Cloudflare Pages con el commit `492c71fc152ddeda55e34df808d0f132cd361dc7`. La configuración real de Cloud Run se exportó privadamente; la revisión inicial es `grownupsvet-backend-00004-mzr`, con PostgreSQL de Render, CPU siempre asignada, 1 CPU, 2 GiB y concurrencia 8. La cuenta sigue en Free Trial y existe saldo dentro del límite autorizado de US$300 sin presupuesto adicional. No actualizar facturación a pago.

Un respaldo completo de Render anterior a las migraciones P3 ya se obtuvo y se restauró en una base local aislada. Allí pasaron las cuatro migraciones V19–V22, validación estricta de Flyway y arranque JPA, conservando los conteos iniciales. Sus datos, conexión y artefactos permanecen fuera de Git. La restauración sintética del mecanismo se prueba por separado.

## Incremento de imagen: preparación antes de desplegar

1. Confirmar la cuenta autenticada de la CLI y fijar proyecto/región en cada comando, sin cambiar defaults globales.
2. Reutilizar la cuenta de construcción dedicada y el bucket privado de fuentes confirmados en el build histórico exitoso. No crear identidades ni ampliar permisos para compilar.
3. Enumerar `gcloud meta list-files-for-upload`. Admitir exclusivamente Dockerfile, pom, Maven Wrapper y src/main. Rechazar credenciales, tests, scripts, output, respaldos o material criptográfico; comprobar que cada archivo coincide con su blob de HEAD mediante los filtros de normalización de Git (CRLF/LF de Windows). Conservar además SHA256 de los bytes exactos enviados.
4. Guardar manifiesto SHA256 de fuentes y enviar `scripts/cloudbuild.yaml` con `_IMAGE_URI` apuntando al tag único `backend:p3-4bdaf2b` del Artifact Registry existente. Este paso construye una imagen, sin modificar el servicio ni su tráfico.
5. Esperar SUCCESS y conservar build ID, logs de compilación y digest inmutable de la imagen. Usar el digest para la posterior revisión, no resolver de nuevo un tag mutable al desplegar.

## Incremento de despliegue: condiciones y secuencia

1. Integrar y verificar primero la compatibilidad frontend, manteniendo los pendientes locales del usuario. Se revisó el gateway existente de Cloudflare Pages: el navegador usa /api/v1 del mismo origen y el worker elimina Origin al reenviar. El rechazo del preflight directo de otro origen es esperado y no exige abrir CORS. Verificar el recorrido real del gateway y conservar CORS vacío para producción; preparar los enlaces públicos de la aplicación desde la ruta de activación validada en el cliente.
2. Preparar una revisión basada en el manifiesto real, conservando referencias versionadas de secretos, montaje JWT, identidad, conexión TLS, límites y configuración funcional. Usar `prod,external-postgres,monitoring` con el hash del token operativo generado en el equipo; conservar las probes originales. Publicar primero una revisión etiquetada sin tráfico, verificar salud, OpenAPI y rechazos de seguridad, y comparar el manifiesto final con el original antes de asignar el 100 %.
3. Aplicar migraciones aditivas V19–V22 con el respaldo previo conservado. Comprobar arranque, salud y contrato antes de habilitar recorridos clínicos. Evitar crear COMPLETED mientras una revisión antigua todavía atienda a los consumidores.
4. Asignar el tráfico de forma coherente; no mezclar validadores de permisos viejos/nuevos. OWNER y VETERINARIAN deben iniciar sesión otra vez.
5. Verificar P1–P3 con datos ficticios y destinatarios de prueba autorizados; registrar commit, build, digest, revisión y evidencias. No enviar correos a terceros no autorizados ni interpretar SENT como recepción confirmada.

## Observabilidad del despliegue

El usuario descartó la prueba de Grafana Cloud y reiteró presupuesto efectivo de US$0. La opción elegida es Grafana OSS y Prometheus en Windows, sin suscripción, sidecar, almacenamiento administrado ni permisos IAM nuevos. El perfil `monitoring` permite únicamente el scrape HTTPS autenticado por una clave máquina cuyo valor original queda en el equipo; Cloud Run recibe solo su SHA256. El [plan de seguridad](plan-monitoreo-local-2026-10-25.md) precede al código y el runtime local se prepara por separado.

La imagen CORE construida en Cloud Build `721f0c17-82d2-4b04-ab97-8a40ea47721d` no incluye este perfil nuevo. Repetir los gates y construir una imagen del commit final antes de desplegar; no presentar la imagen CORE como la entrega de monitoreo. El perfil `observability` y los ejemplos de colectores administrados quedan como alternativas preparadas, sin activarlas.

Con mínimo/máximo de una instancia, Prometheus consulta la URL del servicio cada 60 segundos y Grafana usa la fuente local. Si se permite escalar a varias instancias, esta topología necesita recolección por instancia antes de sumar o comparar contadores; no ampliar el máximo sin revisar ese cambio. El histórico local solo crece mientras el computador y los procesos estén encendidos.

Comprobar scrape, revisión e identidad única; después ejecutar el protocolo de M1–M9 con muestra y oráculo de persistencia. Un panel, contador o respuesta 2xx no acredita por sí solo esos indicadores. No declarar terminado el backend desplegado con evidencia exclusivamente local.

## Recuperación

Conservar el manifiesto y digest iniciales. Tras almacenar estados COMPLETED, la imagen anterior no es una recuperación segura de aplicación sin evaluar los datos evolucionados. Preferir una revisión correctiva compatible con el nuevo esquema; restaurar datos solamente en un destino aislado y mediante un procedimiento revisado. No ejecutar downgrade de Flyway ni restauración destructiva sobre Render durante estas comprobaciones.
