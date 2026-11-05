# Diseño, implementación y ejecución de pruebas de calidad del software

**Proyecto GrownupsVet · 20 requisitos no funcionales · Corte de evidencias del 3 de noviembre de 2026**

## 1. Objetivo y alcance

Evaluar 20 requisitos no funcionales (RNF) seleccionados del Plan de Gestión de la Calidad. La selección cubre el registro de propietarios y mascotas (P1), la gestión de citas (P2) y la atención e historia clínica (P3). Incluye calidad funcional, interacción, fiabilidad, seguridad, desempeño y mantenibilidad. Se dio prioridad a los requisitos que protegen datos o evitan reservas inconsistentes y a aquellos con mediciones o casos trazables. Se conservan los identificadores y las metas del plan. [Plan de Gestión de la Calidad][plan]

El informe consolida ejecuciones realizadas entre el 14 de octubre y el 3 de noviembre. Identifica qué se ejecutó, en qué entorno y qué permite afirmar cada resultado. Las calificaciones corresponden a las condiciones probadas. La evidencia de una revisión histórica conserva su fecha y alcance.

**Resultado de los 20 RNF.** 17 cumplen en los escenarios comprobados, uno tiene evidencia parcial y dos presentaron operaciones por encima de la meta de latencia en Cloud Run. Los flujos clínicos tienen evidencia funcional de API y aceptación manual web. [Aceptación del backend R7][r7] · [Aceptación manual web R4][r4]

## 2. Técnicas y herramientas

| Técnica | Herramienta y justificación | Alcance |
| --- | --- | --- |
| Clases de equivalencia, valores límite y prueba de integración HTTP | JUnit, Spring Boot, MockMvc y PostgreSQL real. Las aserciones verifican la respuesta y el efecto en los datos. Maven conserva el resultado de cada prueba. | Formularios, cuentas, mascotas, autorización y transiciones. [JUnit][junit] · [Integración continua del backend][ci] |
| Concurrencia y fallo controlado | JUnit, conexiones PostgreSQL independientes y solicitudes sincronizadas. Permiten observar qué petición gana y comprobar que un fallo no deja una modificación parcial. | Turnos incompatibles y reprogramación. [Caso de concurrencia][race] |
| Carga HTTP y análisis del percentil | Postman CLI reutiliza las colecciones y permite fijar usuarios virtuales y duración. Para la creación masiva de citas se usó un arnés Python con turnos exclusivos para cada solicitud. k6 sería compatible para automatizar umbrales por operación en futuras comparaciones. | El P95 local se mantiene separado del tiempo de plataforma medido en Cloud Run. Grafana complementa la lectura con histogramas. [Postman][postman] · [Umbrales en k6][k6] · [Plan de Calidad][plan] |
| Inspección del mensaje y recorrido de aceptación | Postman comprueba la respuesta de la API, la cola de correo registra el envío y una persona revisa el contenido. Para recorridos repetibles en navegador, Playwright ofrece verificaciones sobre la interfaz. La ejecución R4 de este informe fue manual. | Un estado de envío, una recepción y la comprensión de un mensaje se registran como observaciones distintas. [Evidencia de correo en TestRail][t40] · [Aceptación manual][r4] · [Playwright][pw] |
| Verificación de integración continua | GitHub Actions ejecuta compilación, pruebas y validación del contrato. Un cambio con un fallo deliberado permite verificar el bloqueo de la rama principal. | Se registran por separado la ejecución satisfactoria y el rechazo previsto. [Control de CI en TestRail][t112] |

## 3. Ambiente, datos y ejecución

| Corte | Versión y ambiente | Datos, ejecución y resultado conservado |
| --- | --- | --- |
| 14 de octubre | Backend 0.9.0, base Git '28b2267d9afc1430d72ec9ffa6776632138780ca', árbol de pruebas identificado en R1. Windows 11 Pro, Java 25.0.4.1, PostgreSQL 18 y Postman CLI 1.74.0. | Perfiles, mascotas y turnos ficticios, HTTP y base local. Maven registró 541 pruebas con 0 fallos, errores u omisiones. Carga fija de 5 usuarios virtuales durante 60 s por escenario. Para solicitudes de citas, 5 trabajadores durante 60,004 s. El correo de recuperación se inspeccionó en Mailtrap Sandbox. [Paquete de evidencia R1][r1bundle] · [C72][t72] · [C73][t73] · [Correo][t40] |
| 25 y 28 de octubre | Backend con contrato clínico, Java 25 y PostgreSQL 18 reales en ambiente de prueba. | Datos clínicos ficticios, reloj controlado y esquemas de pruebas. El corte del 25 registró 695 pruebas y el de R7 registró 769, ambos sin fallos, errores u omisiones. [Verificación clínica local][p3] · [Regresión local R7][localr7] |
| 26 de octubre | GitHub Actions en ambos repositorios y ramas principales protegidas. | Ejecuciones normales satisfactorias y cambios deliberadamente defectuosos en PR de prueba. Los cambios defectuosos quedaron bloqueados y los PR se cerraron sin integrarlos. [R2, C84][t112] |
| 28 y 31 de octubre | API 0.11.0 en Cloud Run con PostgreSQL de Render y migraciones hasta V23. Revisiones 'grownupsvet-backend-admission-960c8898' y 'grownupsvet-backend-00009-vmd', respectivamente. | Actores y registros ficticios. Solicitudes HTTP con oráculos SQL de solo lectura para confirmar relaciones y estados. El colector correlacionó 28/28 y 18/18 solicitudes elegibles con los registros de Cloud Run. La segunda ejecución registró 42 comprobaciones y ningún fallo entre ellas. [Aceptación del 28/10][cloud28] · [Aceptación R7][r7] |
| Noviembre | Aplicación web publicada y TestRail R4. | La aceptación manual registró 5/5 casos aprobados, incluido finalizar una atención y consultar su historia publicada. Un experimento aparte del 3/11 comparó las rutas HTTP al **listar** citas. [R4][r4] · [Informe de latencia][latencia] |

Para reproducir la regresión, preparar la base aislada 'grownupsvet_test' siguiendo la guía del backend y ejecutar desde su raíz 'mvnw.cmd -B -ntp clean verify' en Windows o 'bash ./mvnw -B -ntp clean verify' en Linux. Los XML de Surefire identifican clase, método y resultado. Los anexos de TestRail conservan los perfiles de carga y las muestras históricas. Para reproducir una medición, identificar revisión y origen de los tiempos, preparar datos ficticios, ejecutar cada operación con el perfil documentado y conservar errores, exclusiones y muestras completas. [Guía de integración continua][ci] · [Anexos de R1][r1bundle]

## 4. Casos y resultados de los 20 RNF

En las tablas, **Cumple** significa que se verificó el criterio indicado para las condiciones descritas. **Parcial** identifica una parte del criterio con evidencia insuficiente. **No cumple** conserva una desviación observada frente a la meta, aunque la operación funcione. Los indicadores M1–M9 se conservan como referencia. Cada fila define la comprobación directa que corresponde al RNF. Los casos de TestRail se enlazan por su identificador de ejecución cuando se dispone de él.

### Adecuación funcional e interacción

| RNF y caso | Objetivo, precondiciones y procedimiento | Criterio de aceptación | Resultado y evidencia |
| --- | --- | --- | --- |
| **RNF-01.01** · C1–C3 | En una BD de pruebas, enviar un registro válido y variantes con campos ausentes, formatos o tipos incorrectos y privilegios añadidos. Revisar la respuesta HTTP y consultar cuentas y perfiles con JUnit/PostgreSQL. | El registro válido persiste completo y cada variante inválida se rechaza sin dejar una cuenta o perfil parcial. M2 aporta evidencia de rechazo de datos, con su propio conjunto. | **Cumple.** C1, C2 y C3 registraron 1, 23 y 16 invocaciones aprobadas respectivamente. Los casos pueden compartir métodos. [C1][t1] · [C2][t2] · [C3][t3] |
| **RNF-01.02** · C4–C5 | Usar dos correos equivalentes admitidos por el contrato. Intentar el registro en serie y luego en paralelo con datos ficticios. Verificar el correo almacenado y contar cuentas. | Correo normalizado, una sola cuenta y rechazo del duplicado en ambas modalidades, sin perfiles parciales. M2 es un apoyo parcial. | **Cumple.** Pasaron el caso de duplicado sucesivo y el de concurrencia contra la restricción de datos. [C4][t4] · [C5][t5] |
| **RNF-01.03** · C6 | Autenticar propietarios ficticios distintos y registrar mascotas. Enviar identidad ajena o intentar cambiarla en una edición. Contrastar los datos guardados mediante integración HTTP/SQL. | Cada mascota queda vinculada a exactamente un propietario existente, determinado por la sesión. Los intentos de sustituirlo se rechazan. M1 aporta contexto de altas válidas. | **Cumple.** C6 registró 42 invocaciones aprobadas. La muestra desplegada corroboró propiedad y privacidad cruzada. [C6][t6] · [Nube 28/10][cloud28] |
| **RNF-01.04** · C7–C9 | Preparar turnos ficticios de 30 minutos y un reloj controlado en Bogotá. Probar los límites de fecha, confirmación, cancelación y reprogramación, junto con el corte de pendientes de las 06:00. Incluir turno ocupado y uno contiguo. | Se respeta la ventana de mañana a 60 días, los plazos y el corte. Se impide ocupar un turno reservado y los turnos contiguos válidos siguen disponibles. M5 cubre los conflictos de horario. | **Cumple.** C7, C8 y C9 registraron 7, 8 y 4 invocaciones aprobadas. [C7][t7] · [C8][t8] · [C9][t9] |
| **RNF-01.05** · C10–C12 | Con citas ficticias, cambiar estados con administración y repetir con propietario o rol sin permiso. Intentar reabrir terminales y registrar inasistencia antes y después del fin. Repetir una operación autorizada y verificar el historial. | Solo se aplican las transiciones permitidas. El reloj y los roles respetan sus reglas. Los reintentos mantienen un único efecto. M4 mide creación y aporta contexto. | **Cumple.** C10, C11 y C12 registraron 7, 3 y 3 invocaciones aprobadas. El ensayo en nube corroboró solicitud, confirmación y repetición sin duplicar la cita. [C10][t10] · [C11][t11] · [C12][t12] · [Nube 28/10][cloud28] |
| **RNF-01.06** · C13 histórico y prueba clínica posterior | Preparar una cita confirmada y un borrador ficticio. Intentar finalizar antes del turno, con datos incompletos o desde otro rol. Verificar que la cita no cambie. Completar los cinco campos y finalizar como profesional asignado. | Solo se finaliza desde el inicio con contenido obligatorio y autorización. La atención y la cita cambian a FINALIZED y COMPLETED en la misma operación. M7 cuenta altas válidas, no finalizaciones. | **Cumple.** JUnit verificó rechazo por hora, datos o rol y finalización consistente. En R7 se verificó una finalización y su repetición sin duplicado. El alta clínica de 1/1 corresponde al corte del 28/10. C13 quedó bloqueado en el corte anterior a la clínica. [Pruebas de finalización][finish] · [R7][r7] · [Nube 28/10][cloud28] |
| **RNF-01.07** · C14 histórico y prueba clínica posterior | Preparar una receta y un control ficticios. Registrar una cantidad con decimales, unidad e instrucciones. Consultar el tratamiento con el actor autorizado y comparar contra lo guardado. La comprobación de comprensión requiere observar al destinatario interpretando la indicación. | Se conservan texto, dosis y unidad en la atención correspondiente. El propietario puede identificar el cuidado indicado. M8 verifica relaciones y no sustituye estas comprobaciones. | **Parcial.** JUnit verificó persistencia y precisión. R7 contrastó contenido clínico publicado, con una receta y un control. No se dispone aquí de una sesión con propietarios que acredite comprensión de las indicaciones. [Pruebas clínicas locales][p3] · [Tratamiento registrado y consultado][treatment] · [R7][r7] |
| **RNF-01.08** · C15 histórico y prueba clínica posterior | Preparar varias atenciones ficticias, incluidas publicadas y borradores. Consultar el historial propio, repetir como actor ajeno y contrastar orden, relaciones, paginación y contenido. | Solo aparecen atenciones publicadas y autorizadas, con mascota, fecha, profesional e indicaciones correctos. M8 exige 100 % de registros evaluados con relaciones válidas. | **Cumple.** La suite verifica publicación, aislamiento y orden. R7 verificó 1/1 registro reutilizado con relaciones válidas y R4 confirmó en navegador el historial publicado. El 1/1 de R7 no se suma a los 2/2 de la muestra fa2 porque incluye el mismo registro. [Pruebas de historia][history] · [R7][r7] · [fa2][fa2] · [R4][r4] |
| **RNF-01.10** · C17–C18 | Crear mascotas con datos opcionales ausentes y fechas conocidas o estimadas. Editar con campos omitidos, explícitos y nulos. Consultar la BD tras cada variante. | Los datos ausentes se conservan como desconocidos. Una estimación exige fecha. La edición conserva las omisiones y solo permite retirar datos opcionales. M1 aporta contexto de altas. | **Cumple.** C17 y C18 registraron 23 y 19 invocaciones aprobadas. [C17][t17] · [C18][t18] |
| **RNF-02.21** · C40 | Solicitar recuperación para una cuenta ficticia, correlacionar la tarea de correo con el mensaje en Mailtrap y revisar su texto sin mostrar el código. | El mensaje explica en español el propósito, la acción y la vigencia de forma legible. La inspección del contenido se registra aparte del envío. M8 es una referencia indirecta. | **Cumple en el caso de recuperación.** Postman registró 2 solicitudes y 7 aserciones satisfactorias. Una revisión asistida del cuerpo confirmó la instrucción y los 10 minutos de vigencia. En otro corte se reportó recepción humana de 2/2 avisos clínicos, con codificación correcta. [C40][t40] · [R7][r7] |

### Fiabilidad y seguridad

| RNF y caso | Objetivo, precondiciones y procedimiento | Criterio de aceptación | Resultado y evidencia |
| --- | --- | --- | --- |
| **RNF-03.04** · C52–C54 | Crear turnos y mascotas ficticios. Enviar solicitudes simultáneas sobre el mismo turno y luego sobre intervalos incompatibles para la misma mascota. Probar turnos contiguos y mascotas distintas como controles. | Cada conflicto impide una segunda reserva y conserva una sola cita válida con historial coherente. Los controles compatibles se permiten. M5 exige 100 % de conflictos conocidos impedidos en la muestra. | **Cumple.** Los dos conflictos y los controles válidos pasaron. La prueba del turno exige un HTTP 201, un HTTP 409 y una cita, un evento y una asignación persistidos. El 1/1 observado en nube fue secuencial y se conserva por separado. [C52][t52] · [C53][t53] · [C54][t54] · [Código][race] · [Nube 28/10][cloud28] |
| **RNF-03.05** · C55–C57 | Preparar una cita y dos turnos. Reprogramar a destino libre, repetir con destino ocupado y provocar un fallo controlado de persistencia. Comparar origen, destino, versión e historial. | El éxito reserva destino y libera origen como un solo cambio. Ante conflicto o fallo se conserva la reserva anterior con su historial. M4 aporta contexto de creación. | **Cumple.** Pasaron la reprogramación completa, el conflicto y el rollback. C55–C57 registraron 3, 2 y 3 invocaciones aprobadas. [C55][t55] · [C56][t56] · [C57][t57] |
| **RNF-04.02** · C61–C63 | En operaciones de nueva contraseña, ensayar 14, 15, 128 y 129 puntos de código, frases con espacios y valores de la lista de contraseñas comunes. Verificar aceptación y ausencia de persistencia al rechazar. | Límites inclusivos 15–128, espacios preservados, rechazo de contraseñas comunes y sin truncar. M2 aporta evidencia parcial de rechazo. | **Cumple.** C61, C62 y C63 registraron 6, 4 y 17 invocaciones aprobadas para límites, espacios y lista local. [C61][t61] · [C62][t62] · [C63][t63] |
| **RNF-04.03** · C64–C66 | Consultar un recurso protegido con un JWT RS256 válido y repetir sin token, con firma alterada, emisor o audiencia incorrectos y vigencia vencida. | Solo el token que supera las validaciones permite el acceso. M1 aporta contexto y el criterio se evalúa directamente con los resultados de autorización. | **Cumple.** Pasaron 2, 2 y 3 invocaciones en los tres casos de R1. [C64][t64] · [C65][t65] · [C66][t66] |
| **RNF-04.04** · C67–C69 | Crear sesiones de prueba y reutilizar el token tras cerrar sesión, restablecer la contraseña, deshabilitar la cuenta o cambiar sus permisos. Verificar el resultado con el token anterior. | Se bloquea el acceso cuando el token está revocado, la cuenta deshabilitada o la versión, el rol o los permisos han cambiado. | **Cumple en la regresión local.** C67–C69 registraron 1, 1 y 4 invocaciones aprobadas en R1. El resultado de R1 se conserva como evidencia de ese entorno. [C67][t67] · [C68][t68] · [C69][t69] |
| **RNF-04.05** · C70–C71 | Con dos sesiones de propietarios ficticios y mascotas de ambas cuentas, consultar datos propios y luego intentar leer o cambiar datos ajenos. Inspeccionar las filas tras los rechazos. | El propietario puede consultar o modificar sus datos permitidos. Los identificadores ajenos no dan acceso ni causan cambios persistidos. | **Cumple.** Ambos casos pasaron en R1. La aceptación del 28/10 volvió a comprobar la privacidad cruzada con datos ficticios en nube. [C70][t70] · [C71][t71] · [Nube 28/10][cloud28] |

### Desempeño y mantenibilidad

| RNF y caso | Objetivo, precondiciones y procedimiento | Criterio de aceptación | Resultado y evidencia |
| --- | --- | --- | --- |
| **RNF-05.01** · C72 | Preparar altas ficticias y un propietario con 50 mascotas. Ejecutar 5 usuarios virtuales durante 60 s mediante Postman CLI. Medir cada operación y separar los tiempos de cliente de los registros de plataforma. | **M3, P95 <500 ms por operación** bajo el perfil declarado. Registrar tamaño, fallos y respuestas desconocidas. | **Cumple en las muestras.** En local, registro 80 ms (n=1.393), alta de mascota 13 ms (n=1.393), perfil 11 ms (n=1.392, una respuesta sin aserción final) y listado 15 ms (n=1.390). En Cloud Run, registro 490,70 ms (n=2), alta 315,12 ms (n=3) y consulta de mascota 266,72 ms (n=3). [C72][t72] · [Nube 28/10][cloud28] |
| **RNF-05.02** · C73 | Consultar disponibilidad con Postman y solicitar citas con el arnés Python, usando turnos preparados y exclusivos. Medir cada operación por separado. En nube, correlacionar los tiempos de disponibilidad y cita sin incluir la espera humana. | **M6, P95 <500 ms** para consultar disponibilidad y solicitar citas. El Plan de Calidad también presenta las mediciones de publicación y confirmación. | **No cumple en la muestra de nube.** En local, consultar disponibilidad 20 ms (n=7.455, una respuesta sin aserción final) y solicitar 6,93 ms (n=65.189). En Cloud Run, consultar 366,70 ms y publicar 450,07 ms. Solicitar llegó a 549,60 ms y confirmar a 749,17 ms. Cada operación de nube tuvo n=1. [C73][t73] · [Nube 28/10][cloud28] |
| **RNF-05.03** · C74 histórico y medición posterior | Preparar una atención ficticia vinculada a una cita confirmada. Guardar textos, fórmula y control, finalizar y consultar detalle e historia. Correlacionar cada respuesta elegible con la latencia de Cloud Run. Excluir tiempos humanos y declarar el perfil. | **M9, P95 <500 ms por operación clínica**. Separar escrituras, lecturas y reintentos. | **No cumple en la muestra de nube.** En R7 hubo 18 grupos con una observación cada uno. Guardar tratamiento llegó a 722,47 ms y finalizar a 630,39 ms. Los otros 16 grupos quedaron debajo de 500 ms. El ensayo fue de aceptación, sin carga clínica sostenida. [R7][r7] · [Plan de Calidad][plan] |
| **RNF-06.05** · C84 | Ejecutar la integración continua con cambios válidos. En ramas de prueba, provocar fallos deliberados de una comprobación obligatoria y comprobar que GitHub impide integrar esos cambios. | Se conservan los resultados de compilación, pruebas y contrato. Una comprobación obligatoria fallida bloquea la integración. | **Cumple.** En el corte de auditoría pasaron 722 pruebas de backend y 835 de frontend. Los dos PR con fallos deliberados quedaron bloqueados. R2 registró C84 como Passed. [C84 en R2][t112] · [CI backend][bci] · [CI frontend][fci] |

## 5. Análisis y acciones de mejora

Los casos de concurrencia y reprogramación comprobaron tanto la respuesta de la API como la persistencia. Esas pruebas permiten detectar una reserva doble o una escritura parcial aunque la interfaz muestre un mensaje correcto. En el proceso clínico, las pruebas de backend y la aceptación manual se complementan. Una verifica las reglas de almacenamiento, publicación y acceso, y la otra observa el recorrido en navegador. [Concurrencia][race] · [Regresión clínica][p3] · [Aceptación manual][r4]

Para la latencia se ordenan las observaciones de cada operación y se toma el valor en la posición 'techo(0,95 × n)'. Con n=1, el P95 de muestra es ese único valor. Por ello, los incumplimientos en nube se registran tal como ocurrieron y deben confirmarse en un perfil repetible con más observaciones. Las dos respuestas sin aserción final del CLI se informan como desconocidas. [C72][t72] · [C73][t73] · [R7][r7]

| Hallazgo | Acción y criterio de repetición |
| --- | --- |
| **RNF-01.07.** Precisión y fidelidad verificadas. Falta evidencia de comprensión por el destinatario. | Mostrar una indicación ficticia a usuarios del perfil previsto y pedir que expliquen qué cuidado harían, en qué cantidad y cuándo. Registrar respuestas, dificultades y ayudas. Corregir el texto o su presentación cuando se observe un malentendido y repetir con el mismo procedimiento. |
| **RNF-05.02.** Solicitar y confirmar cita superaron 500 ms en el corte en nube. | Aumentar el número de observaciones por operación en un ambiente acordado. Registrar revisión, carga, arranque en frío y errores. Si la desviación se repite, separar los tiempos de espera de base, ejecución de consultas y respuesta HTTP antes de elegir una mejora. Comparar el P95 con el mismo perfil tras el cambio. |
| **RNF-05.03.** Guardar tratamiento y finalizar la atención superaron 500 ms en R7. | Repetir por separado guardado, finalización y lectura con atenciones ficticias equivalentes. Registrar tamaño de la receta y el control, conexión, escritura y confirmación de la transacción. Verificar que una mejora conserve autorización, integridad y ausencia de duplicados. |

El ensayo del 3 de noviembre ayuda a orientar esa investigación. Al **listar** citas desde Colombia, el P95 fue de **733,85 ms** vía Cloudflare y **455,31 ms** con acceso directo a Cloud Run. Una consulta mínima desde Iowa a Render Ohio tardó **20,11 ms de mediana**. Son mediciones de rutas y de una sonda de lectura. La contribución de la red a las escrituras de M6 y M9 sigue sin cuantificarse. [Informe técnico del experimento][latencia]

[junit]: https://docs.junit.org/current/user-guide/
[postman]: https://learning.postman.com/docs/tests-and-scripts/performance-testing/performance-test-configuration/
[k6]: https://grafana.com/docs/k6/latest/using-k6/thresholds/
[pw]: https://playwright.dev/docs/test-assertions
[plan]: https://docs.google.com/document/d/1MqMw5I7LYPxx9LE4k_F4C-xS4o3TOPI_ma1Yct7pYQU/edit?tab=t.0
[ci]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/integracion-continua.md
[p3]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/verificacion-backend-p3-2026-10-25.md
[localr7]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/verificacion-rendimiento-r7-2026-10-28.md
[cloud28]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/aceptacion-backend-desplegado-2026-10-28.md
[r7]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/aceptacion-backend-r7-desplegado-2026-10-31.md
[fa2]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/aceptacion-funcional-fa2-2026-10-31.md
[latencia]: https://github.com/Estebangmz666/grownupsvet-backend/blob/main/docs/evidencias/latencia-2026-11-03/informe-latencia-grownupsvet.pdf
[race]: https://github.com/Estebangmz666/grownupsvet-backend/blob/083cfe75e837a52ce6e532c0e0cb88512be950a5/src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/availability/VeterinarianAvailabilityConcurrencyIntegrationTests.java#L268
[finish]: https://github.com/Estebangmz666/grownupsvet-backend/blob/083cfe75e837a52ce6e532c0e0cb88512be950a5/src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/ClinicalEncounterHttpIntegrationTests.java#L658
[treatment]: https://github.com/Estebangmz666/grownupsvet-backend/blob/083cfe75e837a52ce6e532c0e0cb88512be950a5/src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/ClinicalEncounterHttpIntegrationTests.java#L527
[history]: https://github.com/Estebangmz666/grownupsvet-backend/blob/083cfe75e837a52ce6e532c0e0cb88512be950a5/src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/clinical/ClinicalHistoryHttpIntegrationTests.java#L152
[bci]: https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40366805490
[fci]: https://github.com/Estebangmz666/grownupsvet-frontend/actions/runs/40365585334
[r4]: https://grownupsvet.testrail.io/index.php?/runs/view/4
[r1bundle]: https://grownupsvet.testrail.io/index.php?/tests/view/83
[t112]: https://grownupsvet.testrail.io/index.php?/tests/view/112
[t1]: https://grownupsvet.testrail.io/index.php?/tests/view/1
[t2]: https://grownupsvet.testrail.io/index.php?/tests/view/2
[t3]: https://grownupsvet.testrail.io/index.php?/tests/view/3
[t4]: https://grownupsvet.testrail.io/index.php?/tests/view/4
[t5]: https://grownupsvet.testrail.io/index.php?/tests/view/5
[t6]: https://grownupsvet.testrail.io/index.php?/tests/view/6
[t7]: https://grownupsvet.testrail.io/index.php?/tests/view/7
[t8]: https://grownupsvet.testrail.io/index.php?/tests/view/8
[t9]: https://grownupsvet.testrail.io/index.php?/tests/view/9
[t10]: https://grownupsvet.testrail.io/index.php?/tests/view/10
[t11]: https://grownupsvet.testrail.io/index.php?/tests/view/11
[t12]: https://grownupsvet.testrail.io/index.php?/tests/view/12
[t17]: https://grownupsvet.testrail.io/index.php?/tests/view/17
[t18]: https://grownupsvet.testrail.io/index.php?/tests/view/18
[t40]: https://grownupsvet.testrail.io/index.php?/tests/view/40
[t52]: https://grownupsvet.testrail.io/index.php?/tests/view/52
[t53]: https://grownupsvet.testrail.io/index.php?/tests/view/53
[t54]: https://grownupsvet.testrail.io/index.php?/tests/view/54
[t55]: https://grownupsvet.testrail.io/index.php?/tests/view/55
[t56]: https://grownupsvet.testrail.io/index.php?/tests/view/56
[t57]: https://grownupsvet.testrail.io/index.php?/tests/view/57
[t61]: https://grownupsvet.testrail.io/index.php?/tests/view/61
[t62]: https://grownupsvet.testrail.io/index.php?/tests/view/62
[t63]: https://grownupsvet.testrail.io/index.php?/tests/view/63
[t64]: https://grownupsvet.testrail.io/index.php?/tests/view/64
[t65]: https://grownupsvet.testrail.io/index.php?/tests/view/65
[t66]: https://grownupsvet.testrail.io/index.php?/tests/view/66
[t67]: https://grownupsvet.testrail.io/index.php?/tests/view/67
[t68]: https://grownupsvet.testrail.io/index.php?/tests/view/68
[t69]: https://grownupsvet.testrail.io/index.php?/tests/view/69
[t70]: https://grownupsvet.testrail.io/index.php?/tests/view/70
[t71]: https://grownupsvet.testrail.io/index.php?/tests/view/71
[t72]: https://grownupsvet.testrail.io/index.php?/tests/view/72
[t73]: https://grownupsvet.testrail.io/index.php?/tests/view/73
