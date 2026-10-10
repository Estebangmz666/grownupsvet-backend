# Revisión del frontend candidato: pendiente de confirmación del equipo

Fecha: **10 de octubre de 2026**, `America/Bogota`.

Se encontró el repositorio público [juanjo1303/grownupsvet-frontend](https://github.com/juanjo1303/grownupsvet-frontend). Su README enlaza este backend y describe dos aplicaciones Expo/React Native, de propietario y administración. **Esteban no ha podido confirmar que sea el repositorio usado actualmente por el equipo ni que incluya los cambios recientes.** Se conserva como candidato de lectura; no se modificó su código ni se publicaron cambios en él.

## Corte verificado

- Rama remota: `master`; commit `58de10979553a79c675081f549576ab96c739422`, fechado **02/10/2026 a las 07:25:17 de Colombia**. Se contrastó el checkout con `git ls-remote`.
- [Árbol de ese commit](https://github.com/juanjo1303/grownupsvet-frontend/tree/58de10979553a79c675081f549576ab96c739422). No se inspeccionaron posibles cambios locales no publicados de los compañeros.
- El manifiesto raíz declara Expo 57, React Native y TypeScript. Se leyó el `AGENTS.md` y la [documentación exacta de Expo 57](https://docs.expo.dev/versions/v57.0.0/) que exige antes de escribir código. La revisión no cambió esas versiones.

| Elemento del corte público | Evidencia actual | Límite para integración |
|---|---|---|
| Entrada raíz | `index.ts` importa `./App`; el árbol no contiene un `App.tsx` raíz | La entrada necesita verificarse/corregirse en la versión que confirme el equipo |
| Propietario | `apps/propietario/App.tsx` conserva la pantalla inicial de Expo; sus cinco pantallas, tres componentes y navegación están vacíos | Este corte no demuestra los recorridos de usuarios/mascotas o citas |
| Administración | `apps/administrativo/App.tsx` está vacío | Este corte no demuestra agenda, personal o cambios de cita desde el portal |
| Cliente compartido | `packages/api-client/package.json` tiene 0 bytes y no hay código de servicios en esa carpeta | No existe un cliente conectable en este corte, aunque el README lo describe como implementado |
| Paquete de accesibilidad | `packages/accesibility-kit/package.json` tiene 0 bytes | El manifiesto no es JSON válido; no se verificaron componentes de accesibilidad |
| Manifiestos de aplicaciones | Los dos JSON de `apps` contienen dependencias, sin configuración propia completa de ejecución | Debe comprobarse la configuración de las dos apps sobre el código actual |

Un lector JSON de Node.js se ejecutó sobre los cinco manifiestos: raíz y ambos `apps` son JSON válido; los dos `packages` vacíos fallan el parseo. **No se ejecutaron instalación, compilación o prueba de navegador del frontend**, ni se atribuye esa validación estructural a una integración real.

El backend publicado mantiene el contrato 0.9.0 con permisos, estados y respuestas reales. Al integrar se debe contrastar el cliente con ese contrato, incluidas paginación `items`, `NO_SHOW`, cambios administrativos y consulta privada de avisos. No basta la descripción histórica 0.7.0 del README candidato para acreditar compatibilidad actual.

## Dependencia antes de cambiar los portales

Confirmar con el equipo el repositorio, rama y revisión que deben usarse y si falta publicar código reciente. Hasta recibir ese dato, el trabajo continúa en preparación y comprobación del backend. La [verificación Mailtrap separada](verificacion-mailtrap-2026-10-10.md) observó lista y encabezados de seis avisos; contenido, recuperación actual y espera real de 24 horas siguen pendientes. La revisión compartida, los portales integrados y el despliegue aplazado conservan sus verificaciones propias.

Esta revisión describe un commit público concreto y la falta de confirmación; **no establece el avance real de los compañeros ni permite cerrar sus tareas en Jira**.
