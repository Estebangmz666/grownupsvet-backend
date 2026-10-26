# Expediente parcial de preparación de auditoría — GrownupsVet

**Corte: 26 de octubre de 2026, America/Bogota.** Este documento registra el alcance parcial disponible y el fortalecimiento en construcción al corte. **No es la entrega final del proyecto ni el resultado de una auditoría integral.** La auditoría del proyecto completo corresponde a la versión final que el equipo termine y someta a revisión.

La guía académica contiene **94 criterios**, distribuidos en 6.1–6.11. Se registran todos para facilitar su revisión, sin asignar notas, porcentajes de cumplimiento ni concepto de aprobación. La existencia de una evidencia técnica acredita su alcance y fecha; no acredita una aceptación humana que no haya ocurrido.

## Alcance y versiones que se deben declarar antes de demostrar

| Elemento | Alcance al corte | Límite de la afirmación |
| --- | --- | --- |
| P1/P2 publicados | Web por cuatro roles y API de usuarios/mascotas, personal, disponibilidad, citas y avisos | Los recorridos específicos se contrastan con sus verificaciones; pruebas aisladas no sustituyen aceptación completa de la web. |
| P3 publicado | API clínica 0.10.0: borrador, tratamiento, finalización, historia y recordatorios | No hay interfaz clínica completa ni aceptación del proceso íntegro. Las barreras de hora real permanecen vigentes. |
| Backend remoto de partida | [736805879fcb6ffe968242d0647439fbe0ddf3f6](https://github.com/Estebangmz666/grownupsvet-backend/tree/736805879fcb6ffe968242d0647439fbe0ddf3f6) | V1–V22; producción y commit final de este incremento deben identificarse en su cierre. |
| Frontend remoto de partida | [492c71fc152ddeda55e34df808d0f132cd361dc7](https://github.com/Estebangmz666/grownupsvet-frontend/tree/492c71fc152ddeda55e34df808d0f132cd361dc7) | Compatibilidad COMPLETED y P1/P2; un SHA nuevo exige actualizar el corte y los resultados afectados. |
| Fortalecimiento en construcción | Kit de base de datos V1–V23 y limitación de login/contrato 0.11.0; adaptación del cliente propuesta en [PR frontend #3](https://github.com/Estebangmz666/grownupsvet-frontend/pull/3) | La prueba local del kit está completada; la publicación, CI y despliegue nuevos se acreditan por separado. No presentar 0.11.0 como producción antes de verificarla. |
| Sitios de demostración | [Web](https://grownupsvet-frontend.pages.dev/), [Swagger](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/swagger-ui/index.html), [OpenAPI](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs) | HTTP 200 y presencia de rutas no equivalen a aceptación de todos los casos. |

**Pendientes dentro del alcance actual:** aceptación integrada de los recorridos publicados que aún no tengan soporte suficiente, sesiones con personas mayores, comprobación con lector de pantalla/teclado, revisión compartida de contratos y documentos, trazabilidad y resultados TestRail actuales, política operativa acordada y reproducción por otro integrante. Los controles nuevos necesitan sus verificaciones remotas después de publicarse.

**Funciones futuras excluidas de esta evaluación parcial:** interfaz clínica P3, adendas P11, aplicaciones nativas Android/iOS y funcionalidades fuera del contrato acordado. La cancelación/reprogramación autónoma del propietario tampoco se incorpora como si existiera. Excluirlas de este corte no las declara terminadas ni permite cerrar las historias globales que las requieren.

## Cómo leer la matriz

- **Comprobado:** existe soporte concreto para el criterio en el alcance expresamente indicado. No significa madurez máxima ni ausencia de defectos.
- **Parcial:** existe una base comprobable y una parte pendiente indicada.
- **No verificado:** no se obtuvo evidencia suficiente o falta una revisión humana/externa; no significa inexistencia.
- **No aplica acordado:** exige una exclusión explícitamente acordada. No se utiliza para ocultar una función pendiente; ninguna fila se marca así en este corte.

Las fuentes fechadas conservan su carácter histórico. El catálogo TestRail y los documentos de Drive requieren sesión y versión vigente antes de actualizarse; aquí no se atribuyen firmas, reuniones, consentimiento, validaciones del cliente ni cumplimiento legal sin soporte.

## 6.1. Gestión del proyecto — 10 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.1.1 | Acta de inicio o documento formal | No verificado | [Entrega/seguimiento SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69). Localizar la definición vigente y aprobación; este expediente no sustituye el acta. |
| 6.1.2 | Objetivos claros | Parcial | [Alcance funcional](../README.md). Alinear con la definición académica vigente y validar sus objetivos con el equipo/cliente. |
| 6.1.3 | Stakeholders identificados | Parcial | [Roles del sistema](arquitectura-usuarios-y-permisos.md). Roles de aplicación no equivalen a lista de interesados, cliente y revisores del proyecto. |
| 6.1.4 | Cronograma actualizado | No verificado | [Versión candidata SCRUM-68](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-68). Contrastar fechas/capacidad actuales; no usar propuestas de septiembre como cronograma vigente. |
| 6.1.5 | Roles y responsabilidades asignados | Parcial | [Proyecto SCRUM](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69). Hay responsables por incidencia; falta comprobar reparto de exposición y aceptación documental compartida. |
| 6.1.6 | Seguimiento de avance | Comprobado | [SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69), [historial backend](https://github.com/Estebangmz666/grownupsvet-backend/commits/main/). Los estados se contrastan con evidencias, sin cerrar por presencia de código. |
| 6.1.7 | Gestión de riesgos | Parcial | [Planes de cierre](plan-cierre-ejecucion-2026-10-25.md) y riesgos al final de este expediente. Falta validación de responsables/mitigaciones y seguimiento conjunto. |
| 6.1.8 | Control de cambios de alcance | Parcial | [Contrato clínico y exclusiones](atenciones-clinicas.md). Cambios técnicos documentados; confirmar aprobación compartida y diferencias con documento académico vigente. |
| 6.1.9 | Reuniones o actas de seguimiento | No verificado | [Preparación de defensa SCRUM-70](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-70). Adjuntar actas reales; comentarios técnicos no prueban una reunión. |
| 6.1.10 | Metodología definida | Parcial | [Tablero/proyecto SCRUM](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-68). Jira evidencia organización; verificar definición y práctica real de planificación/revisión con el equipo. |

## 6.2. Requisitos — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.2.1 | Requisitos funcionales documentados | Comprobado | [Usuarios/mascotas](mascotas-y-recuperacion.md), [citas](citas-y-agenda.md), [clínica API](atenciones-clinicas.md), [historias Jira](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69). Acredita el alcance descrito, no funciones futuras. |
| 6.2.2 | Requisitos no funcionales documentados | Parcial | [Métricas y límites](observabilidad.md), [TestRail S1](https://grownupsvet.testrail.io/index.php?/suites/view/1). Identificar RNF/Plan de Calidad vigentes en Drive antes de sustituir copias históricas. |
| 6.2.3 | Requisitos claros y verificables | Parcial | [Reglas de citas](citas-y-agenda.md) y [contrato generado](openapi/README.md). Reconciliar criterios humanos y requisitos documentales; metas no medidas siguen como metas. |
| 6.2.4 | Requisitos priorizados | Parcial | [SCRUM-68](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-68). Validar prioridades/capacidad actuales y acuerdo del equipo para el incremento; no inferir prioridad solo del estado. |
| 6.2.5 | Trazabilidad requisito–desarrollo | Parcial | [Casos académicos P1/P2](evidencia-casos-academicos-procesos-1-2-2026-10-10.md), [SCRUM-79](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-79), [S1](https://grownupsvet.testrail.io/index.php?/suites/view/1). Completar requisito→caso→resultado→versión actual. |
| 6.2.6 | Validación con cliente/usuario | No verificado | [Revisión/aceptación SCRUM-77](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-77). Falta acta o resultado real; aceptación del código no sustituye validación del usuario. |
| 6.2.7 | Gestión de cambios de requisitos | Parcial | [Planes y decisiones clínicas](plan-cierre-ejecucion-2026-10-25.md), [SCRUM-82](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-82). Registrar revisiones compartidas y vínculo con la fuente académica vigente. |
| 6.2.8 | Consistencia requisitos–producto | Parcial | [Alcance y límites backend](../README.md), [frontend](https://github.com/Estebangmz666/grownupsvet-frontend). P3 API existe; su interfaz y aceptación no. Revisar documentos todavía anteriores al despliegue. |

## 6.3. Análisis y diseño — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.3.1 | Arquitectura definida | Comprobado | [Arquitectura de permisos](arquitectura-usuarios-y-permisos.md), [despliegue](google-cloud-render.md), [estructura backend](../src/main/java). Conservar las limitaciones de versión del C4 académico. |
| 6.3.2 | Diagramas de análisis/diseño | Parcial | [ER verificable del incremento](auditoria-base-datos.md#modelo-entidad-relación-esencial). Revisar C4/BPMN vigentes y su correspondencia con gateway, Gmail y clínica API. |
| 6.3.3 | Diseño responde a requisitos | Parcial | [Diseño y reglas de clínica](atenciones-clinicas.md), [agenda](citas-y-agenda.md). Falta revisión integral/aceptación de usuarios; la prueba técnica no la reemplaza. |
| 6.3.4 | Componentes/módulos/capas identificados | Comprobado | [Paquetes Java](../src/main/java), [frontend apps/packages](https://github.com/Estebangmz666/grownupsvet-frontend). La separación se puede inspeccionar en fuentes. |
| 6.3.5 | Entidades y relaciones definidas | Comprobado | [Modelo/diccionario y kit I3](auditoria-base-datos.md), [migraciones](../src/main/resources/db/migration). Ensayo local V1–V23: 31 tablas incluyendo Flyway y 56 FK; no implica despliegue de V23. |
| 6.3.6 | Principios de diseño adecuados | Parcial | [Guías de dominio](citas-y-agenda.md), [código](../src/main/java). Capas, DTOs y transacciones inspeccionables; queda revisión humana del conjunto, no una certificación automática. |
| 6.3.7 | Mantenibilidad y escalabilidad | Parcial | [CI](integracion-continua.md), [concurrencia clínica](verificacion-backend-p3-2026-10-25.md), [observabilidad](observabilidad.md). No hay evidencia de capacidad general bajo carga productiva. |
| 6.3.8 | Coherencia diseño–código | Parcial | [OpenAPI desde Java](openapi/README.md), [modelo desde migraciones](auditoria-base-datos.md). Actualizar/validar el C4 y BPMN académicos vigentes, sin presentar diagramas iniciales como actuales. |

## 6.4. Desarrollo e implementación — 10 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.4.1 | Código organizado por módulos/capas | Comprobado | [Backend Java](../src/main/java), [frontend](https://github.com/Estebangmz666/grownupsvet-frontend). |
| 6.4.2 | Estándares de codificación | Parcial | [Workflow backend](../.github/workflows/ci.yml), [workflow frontend](https://github.com/Estebangmz666/grownupsvet-frontend/tree/main/.github/workflows). Hay gates; falta revisión cualitativa global de convenciones. |
| 6.4.3 | Nombres adecuados | Parcial | [DTOs y servicios backend](../src/main/java). Hay nombres explícitos y estructura por dominio; no se certificó cada símbolo del proyecto. |
| 6.4.4 | Separación de responsabilidades | Comprobado | [Fuente backend](../src/main/java), [cliente y presentación frontend](https://github.com/Estebangmz666/grownupsvet-frontend/tree/main/packages). Reglas de negocio centralizadas en backend; clientes por rol. |
| 6.4.5 | Bajo acoplamiento/alta cohesión | Parcial | [Dominios y pruebas](../src). Separación visible, pendiente revisión arquitectónica compartida y de módulos amplios; no se asigna una métrica inventada. |
| 6.4.6 | Reutilización adecuada | Comprobado | [Paquetes compartidos frontend](https://github.com/Estebangmz666/grownupsvet-frontend/tree/main/packages), [fuentes compartidas backend](../src/main/java). |
| 6.4.7 | Manejo de errores/excepciones | Comprobado | [Contrato Problem Details](openapi/README.md), [pruebas HTTP](../src/test/java). La adaptación 429 del nuevo incremento requiere su propia publicación/comprobación. |
| 6.4.8 | Compila/ejecuta correctamente | Comprobado | [Regresión backend del corte anterior](verificacion-backend-p3-2026-10-25.md), [CI frontend](https://github.com/Estebangmz666/grownupsvet-frontend/actions). CI y build finales del incremento en construcción se anexan al cierre; no heredan el resultado anterior. |
| 6.4.9 | Repositorio versionado | Comprobado | [Git backend](https://github.com/Estebangmz666/grownupsvet-backend), [Git frontend](https://github.com/Estebangmz666/grownupsvet-frontend). Identificar SHA exacto en cada evidencia. |
| 6.4.10 | Buenas prácticas de programación | Parcial | [Pruebas/gates backend](integracion-continua.md), [protección backend](verificacion-proteccion-integracion-2026-10-25.md), [rechazo CI frontend](https://github.com/Estebangmz666/grownupsvet-frontend/pull/2). Cero aprobaciones humanas permite trabajo individual; no equivale a revisión por pares obligatoria. |

## 6.5. Base de datos — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.5.1 | Modelo documentado | Comprobado | [ER/diccionario](auditoria-base-datos.md). Exportación de catálogo I3 sin filas ni credenciales disponible en el expediente local saneado. |
| 6.5.2 | Tablas/relaciones definidas | Comprobado | [Flyway y fixtures aislados](../scripts/audit-database/), [migraciones](../src/main/resources/db/migration). El kit aplicó/validó 23 migraciones en dos bases UUID. |
| 6.5.3 | PK/FK coherentes | Comprobado | [Kit I3 y oráculos](auditoria-base-datos.md#verificación-y-límites). 56 FK extraídas del esquema aplicado y rechazo real de propietario inexistente. |
| 6.5.4 | Redundancia innecesaria evitada | Parcial | [Migraciones/relaciones](../src/main/resources/db/migration). Snapshots y eventos tienen finalidad histórica; revisión del modelo completo/normalización pendiente, sin afirmar ausencia de toda redundancia. |
| 6.5.5 | BD soporta requisitos | Parcial | [Regresión de dominios](verificacion-backend-p3-2026-10-25.md), [modelo I3](auditoria-base-datos.md). Acredita las reglas probadas de API; no aceptación de UI clínica ni escalabilidad general. |
| 6.5.6 | Restricciones de integridad | Comprobado | [I3](auditoria-base-datos.md): FK, duplicación de turno, evento inmutable, especie inválida y rollback comprobados con escritor limitado; [consistencia clínica](verificacion-backend-p3-2026-10-25.md). |
| 6.5.7 | Respaldo/política de recuperación | Parcial | [Dump real restaurado 25/10](verificacion-respaldo-render-2026-10-25.md), [procedimiento](respaldo-render.md). Existe respaldo probado; frecuencia/retención/responsable y objetivos operativos sostenidos requieren acuerdo/verificación. |
| 6.5.8 | Acceso BD protegido | Comprobado | [Lector por vistas y escritor aislado](auditoria-base-datos.md), [TLS de respaldo](respaldo-render.md). Roles sin privilegios globales; credenciales DPAPI/ACL fuera de Git. No compartir conexión de producción con el auditor. |

## 6.6. Pruebas y calidad — 10 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.6.1 | Plan de pruebas | Comprobado | [Plan de aceptación desplegada](plan-aceptacion-desplegada-p1-p3-2026-10-25.md), [S1](https://grownupsvet.testrail.io/index.php?/suites/view/1). Un plan no demuestra su ejecución. |
| 6.6.2 | Casos definidos | Comprobado | [Casos académicos P1/P2](evidencia-casos-academicos-procesos-1-2-2026-10-10.md), [TestRail S1](https://grownupsvet.testrail.io/index.php?/suites/view/1), [suite fuente](../src/test/java). Verificar catálogo vivo antes de importar. |
| 6.6.3 | Pruebas funcionales realizadas | Comprobado | [Regresión backend fechada](verificacion-backend-p3-2026-10-25.md), [verificación de citas](verificacion-cambios-citas-2026-10-08.md). Resultados fechados no se trasladan automáticamente a otra versión. |
| 6.6.4 | Pruebas de integración realizadas | Comprobado | [HTTP/PostgreSQL/concurrencia](verificacion-backend-p3-2026-10-25.md), [I3 JDBC/Flyway](auditoria-base-datos.md). No se sustituyó PostgreSQL por mocks para esas comprobaciones. |
| 6.6.5 | Pruebas de aceptación realizadas | Parcial | [Plan/checkpoints](plan-aceptacion-desplegada-p1-p3-2026-10-25.md), [SCRUM-77](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-77), [SCRUM-80](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-80). Consolidar las ejecutadas; quedan usuarios/revisión compartida y UI clínica futura. |
| 6.6.6 | Resultados documentados | Comprobado | [Informes fechados](verificacion-backend-p3-2026-10-25.md), [R1 histórica](https://grownupsvet.testrail.io/index.php?/runs/view/1). R1 54 Passed/30 Blocked es corte 14–15/10, no porcentaje actual ni cobertura. |
| 6.6.7 | Gestión de defectos/incidencias | Parcial | [Jira SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69), [CI fallido de control](https://github.com/Estebangmz666/grownupsvet-frontend/pull/2). Vincular cada defecto real/usuario con prueba, corrección y repetición; el fallo deliberado no es un bug productivo. |
| 6.6.8 | Corrección de errores críticos | Parcial | [Historial frontend](https://github.com/Estebangmz666/grownupsvet-frontend/commits/main/), [verificaciones backend](verificacion-backend-p3-2026-10-25.md). Hay correcciones verificadas; no afirmar cierre de todos los críticos sin inventario/revalidación. |
| 6.6.9 | Cumple expectativas del usuario | No verificado | [Aceptación SCRUM-77/80](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-77). Sesiones reales y aceptación de usuarios pendientes de soporte; no deducir satisfacción de tests. |
| 6.6.10 | Validación final | No verificado | [Revisión integral SCRUM-86](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-86). Este corte es parcial; la auditoría/aceptación final espera a que el equipo termine su versión final. |

## 6.7. Seguridad — 10 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.7.1 | Autenticación | Comprobado | [JWT/estado/revocación](arquitectura-usuarios-y-permisos.md), [OpenAPI](openapi/README.md), [pruebas](../src/test/java). |
| 6.7.2 | Roles/permisos/propiedad | Comprobado | [Matriz de permisos](arquitectura-usuarios-y-permisos.md), [historia clínica autorizada](atenciones-clinicas.md). Los cuatro roles tienen competencias independientes. |
| 6.7.3 | Protección de datos sensibles | Parcial | [Documentos/perfiles privados](personal-e-invitaciones.md), [vistas saneadas I3](auditoria-base-datos.md), [logs/observabilidad](observabilidad.md). Falta evaluar de manera integral ciclo de vida/políticas y todos los recorridos UI. |
| 6.7.4 | Validación de entradas | Comprobado | [DTOs/contrato](openapi/README.md), [regresión HTTP](verificacion-backend-p3-2026-10-25.md). Incluye rechazos sin escrituras indebidas dentro de casos comprobados. |
| 6.7.5 | Prevención de vulnerabilidades comunes | Parcial | [Seguridad actual](arquitectura-usuarios-y-permisos.md), [PR de adaptación 429](https://github.com/Estebangmz666/grownupsvet-frontend/pull/3). Limitación de login nueva aún se acredita por etapa; falta cierre de revisión de dependencias y evaluación integral. No se declara aplicación libre de vulnerabilidades. |
| 6.7.6 | Contraseñas/cifrado adecuados | Comprobado | [Argon2id/JWT](arquitectura-usuarios-y-permisos.md), [HMAC/recuperación](mascotas-y-recuperacion.md), [DPAPI del kit](auditoria-base-datos.md). No adjuntar valores secretos para demostrar mecanismos. |
| 6.7.7 | Conexiones protegidas | Comprobado | [Web HTTPS](https://grownupsvet-frontend.pages.dev/), [API HTTPS](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs), [PostgreSQL TLS](respaldo-render.md). El kit se limita a loopback local; no representa TLS remoto. |
| 6.7.8 | Copias y recuperación | Parcial | [Restauración real](verificacion-respaldo-render-2026-10-25.md). La copia puntual programada no prueba una política diaria sostenida ni objetivos RPO/RTO aceptados. |
| 6.7.9 | Eventos/auditorías | Comprobado | [Citas/eventos](citas-y-agenda.md), [clínica/historial](atenciones-clinicas.md), [inmutabilidad I3](auditoria-base-datos.md). Métricas en memoria no sustituyen auditoría durable. |
| 6.7.10 | Políticas básicas documentadas | Parcial | [Usuarios/permisos](arquitectura-usuarios-y-permisos.md), [personal](personal-e-invitaciones.md), [respaldo](respaldo-render.md). Reunir tratamiento, conservación, incidentes y revisión responsable sin inventar aprobación institucional. |

## 6.8. Interfaz y usabilidad — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.8.1 | Interfaz clara/comprensible | Parcial | [Sitio publicado](https://grownupsvet-frontend.pages.dev/), [verificación visual frontend](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-vistas-por-rol-2026-10-22.md). Falta observación documentada con personas mayores. |
| 6.8.2 | Navegación consistente | Parcial | [Vistas por rol](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-vistas-por-rol-2026-10-22.md). Recorridos P1/P2 revisados técnicamente; falta aceptación humana del conjunto. |
| 6.8.3 | Mensajes adecuados | Parcial | [Acceso/registro](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-login-signup-accesibilidad-2026-10-22.md), [avisos](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-avisos-administrativos-2026-10-23.md). Comprensión por usuario y nuevo 429 se acreditan por separado. |
| 6.8.4 | Coherencia visual | Parcial | [Pantallas por rol](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-vistas-por-rol-2026-10-22.md). Capturas/revisión técnica no equivalen a estudio de usabilidad. |
| 6.8.5 | Facilita interacción | Parcial | [Continuidad de sesión](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-continuidad-frontend-2026-10-23.md), [SCRUM-77](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-77). Medir éxito/ayuda/abandono por tarea en sesiones reales. |
| 6.8.6 | Validaciones visibles/comprensibles | Parcial | [Verificación acceso](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-login-signup-accesibilidad-2026-10-22.md), [citas](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-citas-administrativas-2026-10-23.md). Confirmar comprensión/lector real y los formularios afectados por correcciones nuevas. |
| 6.8.7 | Respuesta adecuada a errores de usuario | Parcial | [Cambios/conflictos](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-cambios-citas-2026-10-23.md), [lotes](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-lotes-disponibilidad-2026-10-23.md). Validar recorridos restantes y mensajes nuevos sin sustituir aceptación por mocks. |
| 6.8.8 | Accesibilidad considerada | Parcial | [Acceso/registro accesibles](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/verificacion-login-signup-accesibilidad-2026-10-22.md), [S1](https://grownupsvet.testrail.io/index.php?/suites/view/1). Pendientes revisión real con lector/teclado/zoom y personas mayores; no se declara conformidad WCAG completa. |

## 6.9. Documentación — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.9.1 | Documentación técnica | Comprobado | [Índice backend](../README.md), [contrato](openapi/README.md), [BD](auditoria-base-datos.md), [frontend](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/README.md). |
| 6.9.2 | Documentación funcional | Comprobado | [Mascotas](mascotas-y-recuperacion.md), [personal](personal-e-invitaciones.md), [citas](citas-y-agenda.md), [clínica API](atenciones-clinicas.md). Alcance vigente debe distinguirse de la visión futura. |
| 6.9.3 | Manual de usuario | Parcial | [Guion P1/P2 técnico](guion-demostracion-procesos-1-2.md), [SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69). Existe copia funcional histórica; falta confirmar/actualizar manual remoto con controles web reales. |
| 6.9.4 | Manual de instalación/despliegue | Comprobado | [Arranque local](ejecucion-local.md), [Google Cloud/Render](google-cloud-render.md), [frontend Pages](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/publicacion-cloudflare-2026-10-23.md), [kit I3](auditoria-base-datos.md). Reproducción independiente aún no acreditada. |
| 6.9.5 | Documentación actualizada | Parcial | [Expediente de corte](expediente-auditoria-2026-10-26.md), [informes históricos](verificacion-backend-p3-2026-10-25.md). Persisten fuentes anteriores; reconciliar Drive/README/CI/versión al cierre sin reescribir resultados antiguos. |
| 6.9.6 | Documentación clara/suficiente | Parcial | [Guías del backend](../README.md), [reproducción I3](auditoria-base-datos.md). Un segundo integrante debe seguir instrucciones y reportar obstáculos. |
| 6.9.7 | Trazabilidad entre documentos | Parcial | [Índice OpenAPI](openapi/README.md), [R1 histórica](https://grownupsvet.testrail.io/index.php?/runs/view/1), [SCRUM-69](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69). Completar matriz de la versión actual y distinguir enlaces históricos. |
| 6.9.8 | Comentarios pertinentes en código | Parcial | [Migraciones comentadas](../src/main/resources/db/migration), [fuentes Java](../src/main/java), [kit documentado](../scripts/audit-database/). Hay ejemplos concretos; falta revisión cualitativa general, sin exigir comentar cada línea. |

## 6.10. Despliegue, operación y mantenimiento — 8 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.10.1 | Ambiente de pruebas/producción | Comprobado | [Web publicada](https://grownupsvet-frontend.pages.dev/), [API](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs), [PostgreSQL aislado I3](auditoria-base-datos.md). No mezclar datos/base de pruebas con producción. |
| 6.10.2 | Despliegue documentado | Parcial | [Cloud Run/Render](google-cloud-render.md), [Pages](https://github.com/Estebangmz666/grownupsvet-frontend/blob/main/docs/publicacion-cloudflare-2026-10-23.md), [monitoring](observabilidad.md). Alinear generador y configuración real antes de reutilizar plantillas históricas. |
| 6.10.3 | Procedimiento de respaldo | Comprobado | [Helper/procedimiento](respaldo-render.md) y [ejecución real](verificacion-respaldo-render-2026-10-25.md). Frecuencia sostenida/retención se evalúan aparte. |
| 6.10.4 | Recuperación ante fallos | Parcial | [Restauración real en base nueva](verificacion-respaldo-render-2026-10-25.md), [retiro seguro I3](auditoria-base-datos.md). Falta operación completa por suplente y objetivos de tiempo acordados/medidos. |
| 6.10.5 | Mantenimiento correctivo/evolutivo | Comprobado | [Historial backend](https://github.com/Estebangmz666/grownupsvet-backend/commits/main/), [frontend](https://github.com/Estebangmz666/grownupsvet-frontend/commits/main/). Cambios nuevos todavía en PR se identifican como tales. |
| 6.10.6 | Monitoreo/seguimiento | Comprobado | [Prometheus/Grafana local](monitoreo-local-windows.md), [contrato de métricas](observabilidad.md). Scrape remoto comprobado en evidencia posterior al despliegue; solo recolecta mientras equipo/procesos estén activos. No equivale a SLA ni M1–M9. |
| 6.10.7 | Registro de incidentes/fallos | Parcial | [Jira](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-69), [diagnóstico de CI controlado](https://github.com/Estebangmz666/grownupsvet-frontend/pull/2), [reportes backend](verificacion-backend-p3-2026-10-25.md). Consolidar incidentes reales y su resolución; no mezclar pruebas deliberadas con incidentes de usuarios. |
| 6.10.8 | Estabilidad operativa | Parcial | [Sondas y observabilidad](observabilidad.md), [sitio publicado](https://grownupsvet-frontend.pages.dev/). Comprobaciones puntuales no prueban disponibilidad prolongada ni comportamiento bajo cualquier carga. |

## 6.11. Cumplimiento y buenas prácticas — 6 criterios

| Ítem | Criterio | Estado | Evidencia y pendiente exacto |
| --- | --- | --- | --- |
| 6.11.1 | Metodología definida seguida | Parcial | [Seguimiento SCRUM-68](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-68). Hay tablero/planes; comprobar acuerdos y ceremonias reales sin inferir Scrum por el nombre de Jira. |
| 6.11.2 | Estándares de calidad | Parcial | [CI/validación contractual](integracion-continua.md), [S1](https://grownupsvet.testrail.io/index.php?/suites/view/1). Prácticas concretas no equivalen a certificación ISO o conformidad WCAG integral. |
| 6.11.3 | Ética y profesionalismo | Parcial | [Datos ficticios y separación de evidencia](datos-demostracion.md), [kit I3 sin producción/secretos](auditoria-base-datos.md). No atribuir diagnósticos, títulos auténticos, participantes o revisiones inexistentes. Validación humana global pendiente. |
| 6.11.4 | Tratamiento de datos conforme | No verificado | [Historia SCRUM-6](https://uqvirtual-team-yavszk8l.atlassian.net/browse/SCRUM-6), [seguridad documentada](arquitectura-usuarios-y-permisos.md). Falta revisar aviso/política, aprobación y ciclo de vida aplicables. Controles técnicos no acreditan por sí solos cumplimiento normativo. |
| 6.11.5 | Licencias respetadas | No verificado | [Dependencias Maven](../pom.xml), [manifiesto/licencia frontend](https://github.com/Estebangmz666/grownupsvet-frontend). Elaborar inventario y revisar obligaciones/atribuciones; archivo LICENSE propio no certifica todas las dependencias. |
| 6.11.6 | Herramientas/dependencias documentadas | Comprobado | [Maven](../pom.xml), [frontend manifiestos/lock](https://github.com/Estebangmz666/grownupsvet-frontend), [CI](integracion-continua.md), [runtime OSS](monitoreo-local-windows.md). Mantener versiones y procedencia junto al corte. |

## Evidencia reciente de fortalecimiento que no debe perderse

1. **CI backend:** [protección y PR fallido controlado](verificacion-proteccion-integracion-2026-10-25.md), con check `Backend verification`; bloqueo real observado, PR cerrado sin merge y rama temporal retirada.
2. **CI frontend al 26/10:** `main` exige `Tipos, lint y pruebas`, strict y GitHub Actions app 15368, incluye administradores, sin force push ni eliminación de main; PR obligatorio con cero aprobaciones humanas. [PR de control #2](https://github.com/Estebangmz666/grownupsvet-frontend/pull/2) produjo TS2322 deliberado y `mergeable_state=blocked`, se cerró sin merge y se retiró su rama. Esto demuestra gate, no revisión humana de código.
3. **Conservación:** ocho ZIP de artefactos CI preservados con SHA-256. Su índice local saneado está en `output/auditoria-2026-10-26/github`; no se presentan como descargas de GitHub cuando se comparte solo este repositorio.
4. **Base de evaluación I3:** [procedimiento y modelo](auditoria-base-datos.md). Ensayo 26/10, 11:38 Bogotá, RunId `527562dc9a3b434fabb35ecc51985307`: dos bases locales, V1–V23, cuatro cuentas/mascota/cita/borrador por base; vistas sin credenciales, denegaciones reales y restricciones comprobadas. El kit no arranca SMTP ni accede a Render/dev/test compartidas. `secondPersonReproductionVerified=false`.
5. **Respaldo:** [dump Render restaurado y validado](verificacion-respaldo-render-2026-10-25.md), separado del ensayo sintético. Las copias y credenciales quedan fuera de Git.
6. **Nuevos cambios en revisión:** [PR frontend #3](https://github.com/Estebangmz666/grownupsvet-frontend/pull/3), adaptación 429 con 835 tests/47 suites y build locales reportados; estos resultados no se heredan a `main` ni se llaman aceptación remota antes del cierre. El backend de login limitado tiene verificación de su incremento separada.

Archivos locales de soporte, **sin secretos**, en el workspace del corte: `output/auditoria-2026-10-26/database/README.md`, `verification-sanitized.json`, `schema-sanitized.json`, `offline-guards.json`, `source-manifest.json`; y `output/auditoria-2026-10-26/github/frontend-ci-rejection-proof.json`. Para compartir con el auditor, adjuntar el paquete saneado con su índice y hashes; una ruta local sola no concede acceso remoto. No adjuntar archivos privados DPAPI, dumps reales, logs crudos, contraseñas, JWT, OTP ni destinatarios innecesarios.

## Guion breve de defensa por cuatro roles

Antes de comenzar, declarar fecha de corte, SHA frontend/backend y revisión desplegada. Utilizar únicamente cuentas/recursos ficticios previstos; entregar credenciales por canal privado. La persona expositora revisa los turnos válidos del día sin alterar relojes o registros históricos para facilitar la demostración.

| Rol | Recorrido defendible | Evidencia/límite que se explica |
| --- | --- | --- |
| SUPER_ADMIN | Entrar, consultar administradores y demostrar separación de funciones | No hereda agenda ni clínica; crear/invitar solo si el destinatario y envío de ensayo están previstos, no a una persona ajena. |
| ADMINISTRATOR | Consultar veterinario/perfil/títulos y disponibilidad; gestionar una cita ficticia y revisar su historial/avisos | Versiones, conflictos e incertidumbre tras respuesta perdida; cambios administrativos no conceden acceso al contenido clínico. |
| VETERINARIAN | Consultar agenda/cita asignada; para clínica utilizar Swagger/API con la cuenta autorizada | Mostrar borrador/propiedad y barrera temporal; finalización solo desde inicio real. No mostrar una pantalla clínica futura como disponible. |
| OWNER | Consultar/editar su mascota; revisar disponibilidad, solicitar o consultar cita y distinguir REQUESTED/CONFIRMED | Propiedad de recursos, idempotencia y estados de aviso. Cancelación/reprogramación requieren gestión administrativa. Historia clínica publicada se demuestra mediante API mientras falte interfaz clínica. |

Después, mostrar modelo/BD aislada con lector por vistas, una evidencia de restricción/rollback en base desechable, contrato generado, CI protegido y restauración documentada. Una prueba negativa se explica junto a su control válido: rechazar todo no demuestra seguridad correcta.

## Pendientes humanos y registro de riesgos

| Pendiente/riesgo | Acción y evidencia que lo cierra | Responsable |
| --- | --- | --- |
| Falta de aceptación de usuarios/personas mayores | Sesiones por tareas implementadas; registrar participantes reales, ayuda/errores/abandono y revisión de cambios. Sin porcentajes ni certificación inventados. | Equipo y usuarios disponibles; reparto por acordar. |
| Revisión de contratos/documentos sin firma o acta | Leer la versión vigente, registrar observaciones/decisión y vincular a Jira; no atribuir una revisión a los compañeros sin que ocurra. | Responsables actuales de cada documento/contrato. |
| TestRail vivo no comprobado | Acceso, exportación, deduplicación por IDs/PV y nueva ejecución por versión; Passed solo si cumple todo el esperado. Conservar R1 histórica. | Coordinación de pruebas según Jira; no se reasigna aquí. |
| Manual/C4/Plan de Calidad remotos anteriores al producto | Identificar la versión de Drive, actualizar documentos vivos y referencias; no reescribir resultados de ejecuciones antiguas. | Autores/revisores vigentes de los documentos. |
| Reproducción dependiente de Esteban | Otro integrante sigue el kit/arranque, conserva checkout/versión/resultado y reporta impedimentos. No compartir secretos de producción. | Integrante suplente por confirmar. |
| Monitoreo/respaldo dependientes del computador | Acordar operación, titular/suplente, frecuencia/retención y objetivos medibles; reconocer huecos de scrape y copia puntual del 13/11. | Responsable de operación y equipo. |
| Código nuevo aún no publicado/verificado | PR/CI/revisión/despliegue y smoke específicos, actualizar corte y casos afectados. | Autores del incremento; revisión compartida pendiente. |
| Tratamiento de datos/licencias sin evaluación suficiente | Localizar fuentes vigentes, inventariar obligaciones y registrar revisión responsable; sin dictamen de cumplimiento inventado. | Equipo y responsable académico/cliente correspondiente. |

No se asignan fechas de reunión, asistentes, responsables nuevos ni compromisos humanos que el equipo no haya confirmado. Los pendientes dentro del alcance permanecen visibles aunque una función futura esté excluida de este corte.

## Condición de revisión final

Este expediente se cierra como evidencia parcial fechada. Para auditar el proyecto integral, el equipo deberá terminar la versión final acordada, identificar sus commits/despliegues, completar los recorridos y pruebas de aceptación aplicables, actualizar los documentos vigentes y validar accesos del auditor a sus soportes. La matriz se revisará sobre esa nueva versión, conservando el presente corte histórico. No se declara cumplimiento total de los tres procesos ni aprobación final del proyecto mediante este documento.
