# Plan de protección de integración — 2026-10-25

## Objetivo y alcance autorizado

Impedir la integración en `main` del backend cuando falle `Backend verification`, incluidas las integraciones realizadas por administradores. La prueba real usará un PR temporal que introduce una diferencia deliberada y aislada en el snapshot OpenAPI. No se integrará ese PR ni los PR funcionales existentes; no habrá despliegues ni cambios en Java, SQL, el índice o la rama local activa.

El CORE verificado es `4bdaf2bcec024dc69b07b6f5665ffff36e89904f`, en `codex/backend-cierre-p3-2026-10-25`. Los archivos nuevos del respaldo Render pertenecen a otro incremento y quedan fuera de este trabajo.

## Preflight y autenticación

1. Consultar exclusivamente documentación oficial de GitHub para protección de ramas, check runs, objetos Git, referencias y PR.
2. Capturar la credencial Git ya almacenada mediante `git credential fill`, con `GCM_INTERACTIVE=Never` y prompts deshabilitados. Mantener la credencial únicamente en memoria, sin imprimirla, escribirla en archivos ni pasarla como argumento de proceso. Descartar stderr del helper; si falta credencial, detener las mutaciones sin abrir login.
3. Restringir las solicitudes autenticadas a HTTPS y al prefijo `https://api.github.com/repos/Estebangmz666/grownupsvet-backend`, sin seguir redirecciones.
4. Leer permisos, HEAD remoto de `main`, protección y check runs del CORE. Verificar permiso administrativo y que `Backend verification` procede de GitHub Actions, app `15368`. Capturar también HEAD, rama e índice locales para comprobar que la prueba no los altera.

## Configuración de protección

1. Volver a leer la protección antes de escribir. Si ya existe, conservar sus opciones ajenas al objetivo y las restricciones adicionales; si la respuesta es 404 y `main` no está protegida, crear la política mínima siguiente.
2. Aplicar mediante `PUT /branches/main/protection`:
   - `required_status_checks.strict=true`.
   - Check requerido `Backend verification`, vinculado explícitamente a `app_id=15368`.
   - `enforce_admins=true`.
   - PR obligatorio, `required_approving_review_count=0`, sin exigir aprobación del último push ni CODEOWNERS para el flujo individual actual.
   - `restrictions=null` para este repositorio personal.
   - `allow_force_pushes=false` y `allow_deletions=false`.
3. Verificar por GET todos los valores persistidos. No deducir éxito únicamente de la respuesta del PUT. No efectuar intentos de push directo ni de merge para probar la política.

## Prueba deliberada y aislada

1. Comprobar que no existe `codex/ci-rejection-proof-2026-10-25`. No reutilizar ni sobrescribir una rama ajena.
2. Leer el árbol del CORE y `docs/openapi/openapi.json` directamente de GitHub. Crear un árbol basado en el CORE reemplazando únicamente el valor JSON `info.version` por `invalid-control`, preservando el resto de los bytes. Verificar la diferencia semántica exacta y la lista de archivos del nuevo commit.
3. Crear un commit hijo del CORE y la referencia exacta `refs/heads/codex/ci-rejection-proof-2026-10-25` mediante API de objetos Git. No cambiar checkout, rama local, índice ni los archivos de contrato locales.
4. Abrir un PR no borrador contra `main` con título `CI expected failure`; su descripción explicará la prueba intencional y que no debe integrarse.
5. Consultar los runs/checks con intervalos de aproximadamente 30 segundos. No ejecutar Maven local. La suite remota debe llegar al gate que compara el contrato generado y el snapshot; la diferencia de versión debe producir el fallo previsto, no un problema de infraestructura.
6. Guardar estado final de checks, step fallido y extracto mínimo saneado del log. Verificar que el check requerido falla, el PR abierto muestra `mergeable_state=blocked` y HEAD remoto de `main` permanece idéntico al capturado al inicio. No llamar al endpoint de merge bajo ninguna circunstancia.

## Limpieza y evidencia

1. Una vez registrada la evidencia, cerrar únicamente el PR de control y verificar que no fue integrado.
2. Borrar exclusivamente `refs/heads/codex/ci-rejection-proof-2026-10-25`, después de comprobar nombre exacto, prefijo temporal y SHA del commit de prueba. Prohibido borrar `main` o la rama CORE. Verificar 404 de esa referencia tras la eliminación.
3. Conservar historial del PR y runs; guardar un JSON compacto sin credenciales en `output/backend-cierre-2026-10-25/ci-protection-proof.json`, fuera del repositorio.
4. Escribir `docs/verificacion-proteccion-integracion-2026-10-25.md` con resultados, enlaces y límites; verificar HEAD/rama/índice locales intactos y `git diff --check` de los documentos nuevos. No hacer commit ni push de estos documentos sin coordinación posterior.

## Garantías y límites

La prueba demuestra que un fallo real del check requerido deja bloqueado un PR, sin intentar su integración. La protección es configuración remota de GitHub, no una garantía contra administradores que posteriormente cambien esa configuración. Una revisión con cero aprobaciones mantiene el requisito de PR, pero no aporta una segunda revisión humana. El `strict=true` requiere que la rama esté al día con `main`; cambios posteriores en `main` pueden exigir actualizarla y repetir CI. La evidencia corresponde a los SHA y estados observados durante esta ejecución.

## Referencias primarias

- [Protección de ramas](https://docs.github.com/en/rest/branches/branch-protection#update-branch-protection).
- [Check runs de una referencia](https://docs.github.com/en/rest/checks/runs#list-check-runs-for-a-git-reference).
- [Árboles Git](https://docs.github.com/en/rest/git/trees#create-a-tree).
- [Commits Git](https://docs.github.com/en/rest/git/commits#create-a-commit).
- [Referencias Git](https://docs.github.com/en/rest/git/refs).
- [Pull requests](https://docs.github.com/en/rest/pulls/pulls).
