# Revisión de integración del frontend de propietario

Fecha: **16 de octubre de 2026**, `America/Bogota`.

El equipo indicó que debe evaluarse `feature/propietario` del repositorio [juanjo1303/grownupsvet-frontend](https://github.com/juanjo1303/grownupsvet-frontend). Se consultó el remoto y se obtuvo exactamente el commit [738207d0812b9717e48236e74d3b0206cde5af0b](https://github.com/juanjo1303/grownupsvet-frontend/tree/738207d0812b9717e48236e74d3b0206cde5af0b), fechado el **16/10/2026 a las 10:46:52 de Colombia**. Este es el corte de toda la revisión, aunque la rama avance después.

**Resultado:** este corte corrige las carencias estructurales del candidato revisado el 10 de octubre. El cliente de propietario coincide en sus rutas y estructuras principales con el contrato publicado `0.9.0`. La integración completa todavía no está acreditada: hay pendientes concretos para web, paginación de mascotas y mensajes de recuperación. La aplicación administrativa sigue fuera de este corte.

## Alcance y fuentes

- Se preservó el checkout original del frontend en `master`. La revisión se hizo en un worktree separado, con HEAD separado en el SHA indicado, sin modificar fuentes, manifiestos ni lockfile del equipo.
- Se descargó el [OpenAPI vivo de Cloud Run](https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/v3/api-docs): versión `0.9.0`, **41 rutas y 57 esquemas**. SHA-256 del archivo descargado: `44fdf6ac933e3b6dcc1eb91a9b00809be88f3b1d3a12b2c205030bca32d6c464`.
- Sus objetos `paths` y `components.schemas` son iguales a los del snapshot local `docs/openapi/openapi.json`. Se contrastó con el despliegue, no únicamente con el HEAD de Git del backend, cuyo árbol de trabajo ya tenía cambios anteriores a esta revisión.
- Evidencias locales: [contrato descargado](../../output/frontend-review-2026-10-16/live-openapi.json), [comparación del contrato](../../output/frontend-review-2026-10-16/contract-summary.json) y registros de ejecución en `output/frontend-review-2026-10-16/` del workspace.
- No se cambiaron CORS, Cloud Run ni datos reales. No se enviaron correos, no se publicaron cambios ni se actualizaron tareas de Jira.

## Qué cambió frente a la revisión anterior

| Punto del 10 de octubre | Estado en `738207d` |
|---|---|
| Entrada raíz sin `App.tsx` | Existe `App.tsx`; la entrada apunta a la aplicación de propietario |
| `packages/api-client` vacío | Manifiesto válido y cliente HTTP tipado con autenticación, recuperación, mascotas, citas, disponibilidad, perfil y fotos |
| Paquete de accesibilidad vacío | `packages/accessibility-kit` es un paquete válido con componentes y utilidades; ello no acredita por sí solo conformidad integral de accesibilidad |
| Pantallas de propietario vacías | Hay 12 pantallas: cinco de autenticación, tres de mascotas, tres de citas y una de perfil |
| Falta de confirmación de rama usada | El dato aportado por el equipo coincide con la punta remota consultada |
| Aplicación administrativa vacía | Sigue sin interfaz administrativa: el directorio conserva solamente su manifiesto |

La [revisión del 10 de octubre](revision-frontend-candidato-2026-10-10.md) sigue siendo evidencia válida para `master` en `58de109`; no describe el estado de `feature/propietario` en este nuevo commit.

## Compatibilidad del contrato verificada

Las **21 combinaciones de método y ruta usadas por el cliente** existen en el OpenAPI vivo bajo `/api/v1`. Se verificaron:

- Cuerpos y respuestas principales de login, registro, recuperación en tres pasos y cierre de sesión.
- Actualización de perfil mediante `phoneNumber`, acceso autenticado a fotos y operaciones de mascotas, incluido archivo/reactivación.
- `CreateAppointmentRequestDTO`, incluidos `clientRequestId` y `expectedAvailabilitySlotVersion`.
- Roles, especies, sexo, estados de citas, asignación y estructura `items/page/size/totalElements/totalPages`.
- Helpers de citas y disponibilidad: división en rangos de **hasta 31 fechas inclusivas** y recorrido de `totalPages`.

La coincidencia de contrato no demuestra que todos los recorridos hayan terminado correctamente en un dispositivo. Tampoco confirma que todas las listas de la interfaz recorran todas las páginas.

Para apuntar al despliegue se debe configurar el frontend con:

```text
EXPO_PUBLIC_API_BASE_URL=https://grownupsvet-backend-w5glrcsyda-uc.a.run.app/api/v1
```

El valor predeterminado de `apps/propietario/src/config.ts:3-4` sigue siendo `http://localhost:8080/api/v1`. La variable anterior contiene una URL pública, no una credencial.

## Hallazgos pendientes

### 1. Web: faltan dependencias de plataforma

La exportación web del commit falla porque faltan `react-dom` y `react-native-web`. Expo solicita `react-dom@19.2.3` y `react-native-web@^0.21.2`. La verificación se ejecutó con `CI=1`; no se instalaron automáticamente ni se alteraron los manifiestos para hacer pasar la revisión. El resultado corresponde al commit recibido.

### 2. Web: la sesión depende de una API nativa sin adaptación

En [auth/session.ts:9](https://github.com/juanjo1303/grownupsvet-frontend/blob/738207d0812b9717e48236e74d3b0206cde5af0b/apps/propietario/src/auth/session.ts#L9), `getAccessToken()` llama directamente a `SecureStore.getItemAsync`. Guardar y borrar el token también usan directamente ese módulo.

La dependencia instalada contiene `node_modules/expo-secure-store/src/ExpoSecureStore.web.ts` con `export default {};`. `SecureStore.ts:151-156` invoca `getValueWithKeyAsync`, que no existe en esa implementación web. El arranque llama a `getAccessToken()` en `AuthContext.tsx:54`; el error fija `initializationError` y `RootNavigator.tsx:15-21` presenta una pantalla de error antes de mostrar login. Reintentar ejecuta el mismo camino.

**Impacto:** aun después de añadir las dependencias web y habilitar CORS, la sesión requiere una implementación compatible con navegador. Es una conclusión sustentada en las fuentes exactas y la dependencia instalada; no se presenta como una prueba de interfaz web ejecutada. No se atribuye este bloqueo a Android/iOS.

### 3. Web: el multipart de la foto no contiene un archivo binario

[profile.api.ts:25-31](https://github.com/juanjo1303/grownupsvet-frontend/blob/738207d0812b9717e48236e74d3b0206cde5af0b/packages/api-client/src/profile.api.ts#L25) añade `{ uri, name, type }` mediante `form.append("file", file as unknown as Blob)`. El cast de TypeScript no convierte ese objeto en un `Blob`.

En `FormData` estándar, la misma operación produce el texto `"[object Object]"`, no un archivo. Se reprodujo esa conversión sin llamadas de red. El backend exige un archivo multipart `file` en `UserProfilePhotoController.java:84-87`.

**Impacto:** tras resolver el arranque web, la carga de foto necesita convertir/obtener un `Blob` o `File` real en navegador. La forma con URI corresponde a React Native; no se afirma que falle en nativo.

### 4. Mascotas: la interfaz conserva solamente la primera página

- [MascotasListScreen.tsx:29-30](https://github.com/juanjo1303/grownupsvet-frontend/blob/738207d0812b9717e48236e74d3b0206cde5af0b/apps/propietario/src/screens/mascotas/MascotasListScreen.tsx#L29) consulta `size: 50` y usa únicamente `page.items`.
- [SolicitarCitaScreen.tsx:75-76](https://github.com/juanjo1303/grownupsvet-frontend/blob/738207d0812b9717e48236e74d3b0206cde5af0b/apps/propietario/src/screens/citas/SolicitarCitaScreen.tsx#L75) toma únicamente la primera página de hasta 100 mascotas activas.

No recorren `totalPages` ni ofrecen controles para cargar las siguientes. Con más de 50 mascotas por filtro se omiten elementos de la lista; con más de 100 activas, algunas no se pueden seleccionar para solicitar cita. El riesgo requiere ese volumen; no implica que los usuarios con pocas mascotas estén bloqueados.

La afirmación de paginación completa queda confirmada para los helpers de citas/disponibilidad, no para todas las listas del frontend. `listAppointmentEvents` también solicita solamente la página predeterminada, aunque no se encontró una pantalla que consuma esa función en este corte.

### 5. Recuperación: un error de contraseña se muestra como token vencido

[ForgotPasswordResetScreen.tsx:114-118](https://github.com/juanjo1303/grownupsvet-frontend/blob/738207d0812b9717e48236e74d3b0206cde5af0b/apps/propietario/src/screens/auth/ForgotPasswordResetScreen.tsx#L114) interpreta cualquier HTTP `400` como enlace vencido. La pantalla solo comprueba que las dos contraseñas coincidan antes de enviar.

El backend también devuelve `400 VALIDATION_FAILED` y `fieldErrors` cuando la contraseña incumple su política, por ejemplo si tiene menos de 15 caracteres o es común (`UserPasswordResetRequestDTO.java:16-25` y `GlobalExceptionHandler.java`).

**Impacto:** con un token válido y una contraseña inválida, la interfaz indica pedir otro código en lugar de corregir la contraseña. Debe distinguir el código de error y los errores de campo; aplica también al flujo nativo.

## CORS: qué se comprobó y qué hace falta

El backend publicado respondió a un preflight real de `POST /api/v1/auth/login`:

| Origen consultado | Resultado |
|---|---|
| `http://localhost:8081` | `403`, sin `Access-Control-Allow-Origin` |
| `https://grownupsvet-backend-w5glrcsyda-uc.a.run.app` | `200`, con `Access-Control-Allow-Origin` para ese mismo origen |

Registros: [localhost](../../output/frontend-review-2026-10-16/cors-localhost-headers.txt) y [origen de la API](../../output/frontend-review-2026-10-16/cors-api-origin-headers.txt). La prueba no crea sesiones ni modifica datos.

La aplicación nativa no está sujeta al control CORS del navegador, como documenta [React Native](https://reactnative.dev/docs/network#using-other-networking-libraries). Para Expo web sí se necesitan los orígenes exactos, incluidos esquema y puerto. El origen del frontend es distinto de la URL base de la API y no incluye `/api/v1`.

**Restricción adicional del backend:** `ProductionConfigurationValidator.java:141-151` exige HTTPS con host real para los orígenes de producción y rechaza loopback. Existe una prueba explícita de rechazo de `http://localhost:5173` en `ProductionConfigurationValidatorTests.java`. Por tanto, añadir `http://localhost:8081` a `CORS_ALLOWED_ORIGINS` de Cloud Run no basta: la revisión nueva no arrancaría con la política actual.

Opciones para coordinar, sin aplicar cambios en esta revisión:

1. Probar web local contra backend local, configurando el origen y puerto real de desarrollo en ese backend.
2. Probar web contra Cloud Run desde una URL HTTPS real del frontend y habilitar exactamente ese origen.
3. Si el equipo necesita específicamente web en localhost contra Cloud Run, acordar una excepción explícita y limitada para desarrollo, con su cambio y pruebas correspondientes. No se debe introducir un comodín ni asumir que la variable lo permite hoy.

El equipo aún debe entregar las URLs definitivas. No se tomó el ejemplo `localhost:8081` del correo como un origen confirmado.

## Validación ejecutada

Entorno: Windows, Node `v22.18.0`, npm `11.16.0`. Los registros están fuera del worktree revisado.

| Comprobación | Resultado y evidencia |
|---|---|
| `npm ci --no-audit --no-fund` | Correcto: 621 paquetes; [registro](../../output/frontend-review-2026-10-16/npm-ci.log) |
| `npm run check` | TypeScript pasa; lint detiene la cadena por 5271 errores de finales CRLF, por lo que ese comando no llega a las pruebas; [registro](../../output/frontend-review-2026-10-16/npm-run-check.log) |
| ESLint variando únicamente `endOfLine` a `auto` por CLI | Correcto; [registro](../../output/frontend-review-2026-10-16/eslint-endofline-auto.log) |
| `npm test`, ejecutado separadamente | **13 pruebas en 6 archivos, todas correctas**; [registro](../../output/frontend-review-2026-10-16/npm-test.log) |
| `CI=1 npx --no-install expo export --platform android --output-dir ...` | Correcto: 909 módulos, 17 assets y bundle Hermes de aproximadamente 2,1 MB; [registro](../../output/frontend-review-2026-10-16/expo-export-android.log) |
| `CI=1 npx --no-install expo export --platform web --output-dir ...` | Falla por las dependencias web ausentes; [registro](../../output/frontend-review-2026-10-16/expo-export-web.log) |

Los archivos están almacenados en Git con LF, pero el checkout tiene CRLF por `core.autocrlf=true` del entorno Windows (`git ls-files --eol`: `i/lf w/crlf`). El diagnóstico de ESLint solo cambia esa regla por CLI; no reescribe los archivos. **No se declara que el comando original `npm run check` haya pasado en este equipo.**

Las pruebas cubren rangos y paginación de disponibilidad con `fetch` simulado, fechas de Bogotá, mensajes de error de mascotas/citas, contraste y escala tipográfica. No constituyen pruebas completas de UI, sesión, carga de fotos o reserva contra el backend real. La exportación Android no genera por sí sola un APK instalado ni verifica el comportamiento en un teléfono. No se ejecutó auditoría de dependencias en esta revisión.

## Siguiente paso y respuesta propuesta al equipo

Primero corregir los puntos del frontend que afectan la plataforma elegida. Después coordinar los orígenes exactos y ejecutar una prueba compartida de login, recuperación, mascotas, cita y perfil/foto contra el backend acordado. La administración y la atención clínica requieren su propio alcance y evidencia.

Borrador para copiar; **no se envió ningún mensaje**:

> Hola, Juanjo. Gracias por precisar la rama y el commit. Repetí la revisión sobre `feature/propietario` en `738207d`; confirmé que la entrada, los paquetes y las pantallas de propietario ya están implementados. Las 21 operaciones del cliente coinciden en ruta/método y estructuras principales con el OpenAPI vivo 0.9.0, y los helpers de citas/disponibilidad respetan los rangos y páginas.
>
> Encontré pendientes concretos: la exportación web requiere dependencias de plataforma, la sesión con SecureStore y el multipart de foto necesitan adaptación para navegador, las listas de mascotas toman solo la primera página y el restablecimiento interpreta cualquier 400 como token vencido. Dejé referencias al commit y resultados en la nueva revisión.
>
> Sobre CORS, envíame los orígenes exactos para coordinarlos del lado del backend. Cloud Run actualmente exige orígenes HTTPS reales; localhost contra producción necesitaría una excepción de desarrollo acordada, no solo añadir la variable. Para probar nativo podemos usar la URL pública de la API sin habilitar CORS. Cuando tengamos la plataforma y los orígenes definidos, coordinamos una prueba completa de los recorridos.
