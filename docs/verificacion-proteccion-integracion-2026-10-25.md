# Verificación de protección de integración — 2026-10-25

## Resultado

`main` del backend tiene protección activa, confirmada mediante GET tras su configuración. Un PR de control no borrador ejecutó CI real y quedó bloqueado por el fallo deliberado del snapshot OpenAPI. Después se cerró el PR sin integrarlo y se eliminó exclusivamente su rama temporal. La verificación terminó el 25 de octubre de 2026 en `America/Bogota` (`2026-10-26T03:36:23Z` para el cierre del PR).

## Política persistida

| Opción | Valor verificado |
| --- | --- |
| Check requerido | `Backend verification` |
| App que debe emitirlo | GitHub Actions, `app_id=15368` |
| Rama actualizada antes de integrar | `required_status_checks.strict=true` |
| Aplicación a administradores | `enforce_admins=true` |
| PR requerido | Sí |
| Aprobaciones requeridas | `0` |
| Restricciones de actores | `null`, repositorio personal |
| Force push | Deshabilitado |
| Eliminación de `main` | Deshabilitada |

El preflight devolvió permisos administrativos y ausencia de protección (`GET /branches/main/protection`: 404). No había una política anterior que conservar. La primera solicitud con `contexts:[]` y `checks` recibió 422 porque ambos formatos coincidían con alternativas excluyentes del esquema de la API; se envió únicamente `checks`, sin modificar la política solicitada. El PUT correcto devolvió 200 y un GET independiente confirmó todos los valores anteriores.

## Prueba de rechazo

- CORE validado: `4bdaf2bcec024dc69b07b6f5665ffff36e89904f`.
- Commit de control, hijo directo del CORE: `f6bb9b14fee749972f7faf89e1651c8dee28c247`.
- Rama temporal: `codex/ci-rejection-proof-2026-10-25`.
- [PR de control #3: CI expected failure](https://github.com/Estebangmz666/grownupsvet-backend/pull/3), creado listo para revisión contra `main`, sin modo borrador.
- Diferencia exclusiva del commit de control frente al CORE: `docs/openapi/openapi.json`, campo `info.version`, de `0.10.0` a `invalid-control`. Se verificó igualdad semántica del resto del documento y un único archivo modificado mediante la comparación remota. El resto de los bytes del snapshot se preservó.

El commit, árbol y referencia se crearon mediante la API de objetos Git. El PR contra `main` contenía el CORE pendiente de integrar más esta única alteración deliberada; la diferencia aislada anterior se comprobó contra el CORE, no contra la versión antigua de `main`.

| Evento | Run | Job | Resultado |
| --- | --- | --- | --- |
| Push | [40264549863](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40264549863) | [121383947577](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40264549863/job/121383947577) | `failure` previsto |
| Pull request | [40264552524](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40264552524) | [121383955883](https://github.com/Estebangmz666/grownupsvet-backend/actions/runs/40264552524/job/121383955883) | `failure` previsto |

En ambos runs:

1. Maven terminó con `BUILD SUCCESS`: 695 pruebas, 0 fallos, 0 errores y 0 omitidas.
2. El gate de reportes verificó 59 suites completas.
3. OpenAPI 3.1.0 y los 50 ejemplos de respuestas HTTP pasaron su validación.
4. Falló exclusivamente el paso 11, `Check the versioned OpenAPI snapshot`, con este diagnóstico:

```text
CI evidence check failed: Generated OpenAPI differs from the versioned snapshot; review the change and regenerate docs/openapi/openapi.json.
```

Antes de cerrar el PR, la API devolvió `mergeable_state=blocked` y `draft=false`. Los dos checks `Backend verification` terminaron con `conclusion=failure`, emitidos por la app 15368. Esto vincula el bloqueo observado al check requerido y a un fallo real del contrato, después de superar las pruebas y la validación estructural.

## Integridad y limpieza

- HEAD remoto de `main` antes, durante y después: `432766397468242e8e837392340b8cec3e483f7b`.
- Se cerró únicamente el PR #3; GET posterior confirmó `state=closed` y `merged=false`.
- Se borró únicamente `refs/heads/codex/ci-rejection-proof-2026-10-25`, tras comprobar nombre exacto y SHA. GET posterior de esa referencia: 404.
- Los runs y el PR cerrado permanecen como evidencia histórica.
- La rama local siguió en `codex/backend-cierre-p3-2026-10-25`, HEAD `4bdaf2bcec024dc69b07b6f5665ffff36e89904f`. Se compararon hashes del índice y del contrato local antes/después: idénticos. Los documentos de este incremento quedaron nuevos, sin stage ni commit.
- No hubo intentos de merge, push directo a `main`, despliegues, Maven local ni cambios en Java o SQL. Los archivos del respaldo Render se preservaron.

Evidencia compacta fuera de Git: `output/backend-cierre-2026-10-25/ci-protection-proof.json`, dentro del workspace padre del backend. Incluye política persistida, SHA, checks, runs, extractos saneados del log, bloqueo observado y limpieza. La credencial Git se obtuvo de caché sin login interactivo y se usó solo en memoria; no se imprimió ni se guardó en evidencias o argumentos de proceso.

## Límites

Se verificó el bloqueo mediante el estado del PR y el fallo del check requerido; no se intentó integrar el PR de control. Cero aprobaciones permite el flujo individual con PR y CI, sin exigir una segunda revisión humana. La protección depende de que un administrador no la deshabilite posteriormente. Los PR funcionales existentes siguen sujetos a una decisión de integración separada.

Referencias: [API oficial de protección](https://docs.github.com/en/rest/branches/branch-protection#update-branch-protection), [check runs](https://docs.github.com/en/rest/checks/runs#list-check-runs-for-a-git-reference) y [plan previo](plan-proteccion-integracion-2026-10-25.md).
