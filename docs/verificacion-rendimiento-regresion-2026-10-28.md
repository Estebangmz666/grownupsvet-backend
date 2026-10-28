# Verificación local del incremento de rendimiento

28/10/2026. Se implementaron los planes previos de [autenticación](plan-rendimiento-autenticacion-2026-10-28.md), [citas](plan-rendimiento-citas-2026-10-28.md) y [clínica](plan-rendimiento-clinico-2026-10-28.md), incluidas sus decisiones acotadas C2/B1 y el delta de privacidad de replays. **La regresión local pasa; el cumplimiento de latencia desplegada sigue sin acreditarse para este código.**

## Cambios verificados

- Cuenta/revocación JWT en una consulta nueva por petición, sin caché ni carga del hash de contraseña. Se conservan estado, versión, rol, email, permisos y revocación por issuer+jti; logout y cambios confirmados se observan en la siguiente validación.
- Creación de cita agrupada con dependencias `RETURNING` de padre, asignación y evento; confirmación agrupa estado/evento/respuesta y mantiene supersesión/fuente del aviso en pasos explícitos. Las tareas de confirmación inmediata y recordatorio se insertan juntas cuando corresponden. Los presupuestos de las pruebas son **máximo 8 y 12 sentencias de servicio**, respectivamente, separados de autenticación, commit y SQL interno de triggers.
- Respuestas clínicas desde valores persistidos `RETURNING`, snapshot inicial de identidad sin lecturas duplicadas, locks secuenciales conservados y escritura multivalor de hijos con orden/precisión obtenidos de PostgreSQL. Las programaciones usan una escritura por conjunto dentro del mismo BEFORE_COMMIT obligatorio.
- Los replays de DRAFT vuelven a comprobar elegibilidad antes de devolver contenido. Cancelación/inactivación impide devolver el borrador con una clave antigua; el veterinario reasignado pierde acceso y los replays legítimos de FINALIZED continúan funcionando. No se añaden consultas para esa guarda.

No se modificaron rutas, DTOs, permisos, migraciones V1–V23, fingerprints históricos, configuración de nube ni reglas de tiempo para obtener estas reducciones.

## Ejecuciones y conservación de resultados

1. Focal inicial C1/autenticación/MIME: **149 pruebas, ocho suites, cero fallos/errores/omitidas**. Sus XML se archivaron antes del siguiente incremento.
2. Primer `clean verify` conjunto: **762 pruebas, 65 suites, un error de preparación** en el nuevo caso de reasignación. Intentaba reasignar mientras el veterinario aún estaba disponible. Se conservaron log y XML completos del fallo; no se alteró la regla del servicio para hacerlo pasar.
3. Corregido únicamente el fixture: se marca indisponibilidad mediante el flujo de pruebas existente, se toma la versión incrementada y se reasigna. Los **cuatro casos focalizados** de pérdida de acceso pasaron sin cambios productivos.
4. Nuevo `mvnw -B -ntp clean verify`: **762 pruebas, 65 suites, cero fallos, errores u omitidas**, BUILD SUCCESS. Java 25 y PostgreSQL local de pruebas, sin configuración de runtime/SMTP productivo en el proceso hijo. Duración de esta ejecución: 3 min 40 s.

Los casos incluyen concurrencia y rollback, fallos tardíos de asignación/evento/primera y segunda tarea, restauración exacta de leases, respuesta nueva/replay/GET, tratamiento con 0/1/50 hijos, precisión y orden, fallo del segundo grupo de hijos, fin/ventana de controles y los replays tras pérdida de acceso.

## Gates posteriores

| Control | Resultado |
| --- | --- |
| Reportes Surefire completos | 65 suites / 762 pruebas, ninguna fallida u omitida |
| OpenAPI generado | 3.1.0 válido, API 0.11.0 |
| Ejemplos HTTP contra esquemas | 51 válidos |
| Campos requeridos, enteros y enums | Rechazos previstos para disponibilidad y citas |
| Snapshot versionado | Coincide; solo se ignora orden de objetos/conjuntos de esquemas |
| Gates negativos | Aceptan orden inocuo; rechazan drift, OpenAPI inválido, tests omitidos y reportes faltantes |

El checker local de monitoreo y el colector conservan sus 18 y 24 pruebas offline aprobadas; actionlint pasó para la integración de CI. No se atribuye cobertura de líneas al número de pruebas.

## Identidad de la evidencia local

El directorio `output/backend-cierre-2026-10-28/r5-full-final-reports`, fuera de Git, conserva XML, contrato y un manifiesto de 292 archivos de aplicación/build leídos tras compilar. Las fuentes de aplicación permanecieron congeladas durante esta ejecución.

| Artefacto | SHA256 |
| --- | --- |
| Manifiesto de los 65 reportes finales | `870b72150b8e0154b879d4cf4dbb179b8beab462aa01d5cae71a63efe1bf68a8` |
| Manifiesto de fuentes de aplicación/build | `210a1ac72c3f9bbda450615b21ec62dfd9e705a76621f12e734a06e4259b9c2e` |
| OpenAPI generado archivado | `2109adcd1c915f6c9b714d9a8aa1a15a591064eafe7a20c89ddf3056eda89412` |

La publicación seguirá el [plan de imagen y despliegue](plan-publicacion-rendimiento-2026-10-28.md). CI remoto del commit final, imagen, revisión, observabilidad fresca y mediciones posteriores tienen evidencia propia; el corte anterior con seis incumplimientos no se elimina ni se convierte en resultado de este código.
