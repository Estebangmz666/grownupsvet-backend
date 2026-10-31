# Cierre funcional del backend — 31/10/2026

El alcance backend aprobado está implementado y su entrega final quedó verificada. La aceptación es **funcional, con desviaciones de latencia documentadas**: no se declara cumplido el SLO general de 500 ms. Por instrucción del usuario del 31/10, las mejoras adicionales de rendimiento quedan como diagnóstico y plan futuro sin implementar.

## Entrega identificada

| Elemento | Valor verificado |
| --- | --- |
| Fuente de aplicación | `222131c5704652aa85cf79f2548d8c225f590e54` |
| Integración | [PR9](https://github.com/Estebangmz666/grownupsvet-backend/pull/9), HEAD revisado `001dcafe95e8a8a1fc07b2fb0ee4bd0f55aec2d4` |
| CI del merge | [Backend verification](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/41243803307), app 15368, SUCCESS |
| Fuentes enviadas a build | 292 archivos, 974684 bytes; whitelist y blobs Git comprobados |
| Cloud Build | `3cd9a090-a20b-4681-b04e-4cf48c5d590d`, SUCCESS |
| Imagen inmutable | `sha256:3f1ed36f60bdaa245d529cc1fb56e59d29255f9d3c94446d1c46a88f7e06ca11` |
| Revisión de entrega | `grownupsvet-backend-00009-vmd`, 100% del tráfico, sin tags |
| Contrato y esquema | OpenAPI 0.11.0, 47 rutas, migraciones V1–V23 |
| API | `https://grownupsvet-backend-w5glrcsyda-uc.a.run.app` |
| Estado operativo final | Mínimo0/máximo1, monitoreo local apagado, datos conservados |

El contenido de aplicación de esta entrega corresponde al commit anterior. Los cambios posteriores de documentación no requieren otra imagen: no modifican los archivos enviados por `.gcloudignore`. No se confunde el commit documental con una nueva versión de aplicación.

## Matriz de los nueve incrementos

| Incremento | Criterio y evidencia | Resultado y límite |
| --- | --- | --- |
| I0 Base de entrega | PR9 y CI exacto; `main` estricto con check 15368 obligatorio y aplicado a administradores, sin force push; [769 pruebas/65 suites](verificacion-rendimiento-r7-2026-10-28.md), contrato/snapshot/51 ejemplos/gates negativos; build e imagen identificados | Implementado/verificado. El envío clínico y la recepción de esta entrega se acreditan aparte. No se afirma porcentaje de cobertura de código. |
| I1 Acuerdos y contrato | Decisiones clínicas aprobadas, contrato público completo coincidente, roles revisados; compatibilidad del consumidor con COMPLETED en [commit frontend 0ed0232](https://github.com/Estebangmz666/grownupsvet-frontend/commit/0ed02323c0b4f4056a9e6edc419c226c1c0e3ba4), incluido en main 9cdcb03 | Cumplido en alcance backend/contrato. Las pantallas clínicas no están incluidas. |
| I2 Borrador | Creación y lectura en [fa2](aceptacion-funcional-fa2-2026-10-31.md); regresión PostgreSQL; borrador heredado reconocido con datos, autor y versiones exactos en R7 | Implementado. Creación R7 N=0; no se presenta como otra atención creada. |
| I3 Fórmula y controles | Sustitución, replay, Unicode, orden/precisión y 0/1/50 hijos en regresión; guardado 1+1 y lectura íntegra en R7 | Verificado; una receta ficticia y un control, sin uso clínico real. |
| I4 Finalización | R7 FINALIZED v3/COMPLETED v2, auditoría y programación atómicas, replay sin efectos; pruebas de carreras, rollback e inmutabilidad | Verificado. Sin adendas por decisión aprobada; no se sobrescribe una atención finalizada. |
| I5 Historia | Lecturas de propietario y responsable, detalle/discovery/tratamiento e historia; rechazos de propietario ajeno, otro veterinario y administración; regresión de paginación y snapshots | Verificado en los casos declarados. Acceso completo durante asignación confirmada; después solo atenciones que el veterinario finalizó. |
| I6 Recordatorios | Dos fuentes FINISHED, dos tareas SENT/1 intento, destinatario autorizado y corte posterior al fin sin nueva dosis; recepción USER_REPORTED 2/2 con tildes correctas | Verificado. Los reintentos pueden duplicar avisos; no se garantiza exactly-once ni entrega fuera de la ventana. Con cero instancias no corren jobs y se omiten dosis vencidas al reanudar, según operación aprobada. |
| I7 Observabilidad | Prometheus/Grafana locales, revisión 009 fresca, siete familias/siete operaciones, bucket 500 ms, nueve consultas; 18/18 traces Cloud Run correlacionados | Medición verificada. NaN del panel no se toma como P95 finito; el incumplimiento numérico no se oculta. |
| I8 Cierre integrado | [Aceptación R7](aceptacion-backend-r7-desplegado-2026-10-31.md), continuidad de registro fa2→R7, permisos, contrato, SMTP/recepción, corte terminal y ahorro; evidencia previa de P1/P2 y recuperación/restauración conservada | Cierre funcional del alcance aprobado, con las limitaciones siguientes. |

La revisión de cierre contrastó el plan del 23/10, decisiones posteriores, fuentes y evidencias; dos revisores independientes no encontraron funcionalidad clínica aprobada pendiente. No se impusieron Jenkins, adendas, UI clínica, nuevos canales de mensajería o una nueva política de disponibilidad.

## Evidencia de aceptación y métricas

- [fa2/008: Window A+B](aceptacion-funcional-fa2-2026-10-31.md): 138 HTTP registrados esperados y una lectura de preflight interrumpida conservada por separado. 36/36 traces elegibles correlacionados. No se declara cero errores globales.
- [R7/009: entrega final](aceptacion-backend-r7-desplegado-2026-10-31.md): tres escrituras, tres replays, lecturas y privacidad sobre una atención heredada. Los datos y conteos pertenecen a esa revisión.
- [Diagnóstico y plan futuro](diagnostico-latencia-y-plan-futuro-2026-10-31.md): hechos, hipótesis y trabajo propuesto, sin nuevas optimizaciones. En R7 guardar tratamiento dio 722.465284 ms y finalizar 630.391351 ms, con N=1 por grupo. Ambos incumplen `<500 ms`; no se atribuye una causa exclusiva sin tracing por componente.

| Catálogo de calidad | Cómo se interpreta esta entrega |
| --- | --- |
| M1/M2/M4/M5 | Se conservan las poblaciones y oráculos históricos de registro, rechazo y citas junto con la regresión vigente. No se inventa una nueva tasa R7 de operaciones no ejecutadas. |
| M3/M6 | Sus medidas históricas, incluidos incumplimientos, mantienen identidad y perfil; no se trasladan a la imagen R7. |
| M7 | R7 registra texto 1/1, tratamiento 1/1 y finalización 1/1 por separado; creación 0/0 significa no observada, no fallo ni éxito adicional. La creación fa2 tiene su propio denominador. |
| M8 | R7 acredita 1/1 registro heredado. Ya se contó en los 2/2 distintos de fa2; sumar ambas muestras no crea tres registros. |
| M9 | 18 intentos elegibles R7, un intento por grupo: 12 INITIAL, 3 REPLAY y 3 RECONCILIATION. Las dos desviaciones anteriores se conservan, sin SLO PASS. |

Las muestras son controladas, pequeñas y secuenciales. No certifican 99% poblacional, capacidad bajo carga, seguridad total o disponibilidad 24/7. Los replays y conciliaciones no cuentan como escrituras nuevas; los ejemplos de contrato no equivalen a recorridos nuevos de nube.

## Evidencia reutilizada y operación posterior

La [recuperación por correo en Gmail](verificacion-gmail-2026-10-16.md) mantiene su alcance histórico: solicitud 202, aceptación SMTP y recepción humana. No acredita un reset de contraseña u OTP productivo que no se ejecutó. El flujo de recuperación forma parte de la regresión local vigente; no se cambian credenciales reales para fabricar otra evidencia.

La [restauración V22→V23/JPA](evidencias/auditoria-2026-10-26/restore.json) fue comprobada en una base local aislada, sin SMTP y con limpieza; las migraciones no cambiaron para R7. La automatización de respaldo del **13/11/2026 a las 09:00 Bogotá** sigue ACTIVE y su helper conserva el SHA validado. Es una programación futura: requiere equipo/Codex/conectividad disponibles y no se presenta como un backup ya ejecutado ese día.

El cierre operativo del 31/10 confirmó generación 21, revisión 009 al 100%, mínimo 0/máximo 1, configuración protegida conservada, sin tags, Prometheus/Grafana apagados y puertos locales 3000/9090 cerrados. Se conservaron TSDB/SQLite y no se hicieron peticiones públicas después de apagar. Mínimo0 no garantiza cero instancias inmediato ni coste total cero. Para consultar el panel, usar los [controles locales](controles-monitoreo-local-windows.md); encender el scrape puede despertar la API.

Quedan fuera de este cierre backend las pantallas clínicas, la corrección del diagrama C4 registrada en los pendientes del frontend y las mejoras futuras de rendimiento. El C4 deberá mostrar Cloudflare Pages, Cloud Run y Render Free Tier. El expediente de auditoría del 26 conserva su corte parcial y no se transforma retrospectivamente en una auditoría completa del proyecto.
