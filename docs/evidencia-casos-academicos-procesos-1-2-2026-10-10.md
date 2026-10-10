# Anexo de evidencia: casos académicos de los procesos 1 y 2

Fecha de revisión: **10 de octubre de 2026**, `America/Bogota`.

Este anexo contrasta los CP-001 a CP-012 del documento de coordinación **«Casos de prueba referenciados de GrownupsVet»**, elaborado el **06/10/2026**, con los métodos actuales y los resultados XML de Surefire. El original está en `docs/pruebas/casos-de-prueba-referenciados.md` de la carpeta padre, fuera del repositorio backend. Se conservan sus identificadores sin modificarlo. Es una selección representativa del backend, no el catálogo completo ni una medición porcentual de cobertura.

## Ejecución de referencia

La suite completa `mvnw.cmd verify` terminó el **10/10/2026 a las 00:07:42 de Colombia**: **508 pruebas, 0 fallos, 0 errores y 0 omisiones**, con `BUILD SUCCESS`. La evidencia local está en `target/procesos-1-2-publication-verify.log` y los XML de `target/surefire-reports/`. Se leyeron estos resultados; no se ejecutaron pruebas adicionales para elaborar el anexo.

Las cuatro clases referenciadas registran: `UserSignupHttpIntegrationTests` **24/0/0/0**, `UserPasswordValidatorTests` **9/0/0/0**, `PetHttpIntegrationTests` **54/0/0/0** y `AppointmentHttpIntegrationTests` **42/0/0/0**, en orden pruebas/fallos/errores/omisiones. Todos los métodos de la tabla existen en el código y sus invocaciones XML carecen de failure, error o skipped.

## Correspondencia comprobada

| ID | Clase | Método o métodos presentes | Resultado XML | Alcance concreto acreditado |
|---|---|---|---|---|
| CP-001 | `UserSignupHttpIntegrationTests` | `createsAnOwnerAndProfileWithAnEncodedPasswordWithoutStartingASession` | Pasó: 1 | HTTP 201, cuenta OWNER activa, correo/nombre normalizados, perfil persistido, hash Argon2id y ausencia de cookie de sesión/credenciales en respuesta. |
| CP-002 | `UserSignupHttpIntegrationTests` | `rejectsInvalidFieldsWithoutCreatingAnAccount` | Pasaron: 13 variantes parametrizadas | HTTP 400, VALIDATION_FAILED/TYPE_MISMATCH, error del campo y ninguna cuenta/perfil creados. Los datos del CP son una selección de las 13 variantes del método. |
| CP-003 | `UserSignupHttpIntegrationTests` | `returnsConflictForAnExistingEmailRegardlessOfCase` | Pasó: 1 | Segunda alta con correo equivalente en mayúsculas: HTTP 409 EMAIL_ALREADY_REGISTERED y una única cuenta/perfil. |
| CP-004 | `UserPasswordValidatorTests` | `acceptsTheInclusiveLengthBoundsMeasuredInUnicodeCodePoints`; `rejectsLengthsOutsideTheBoundsEvenForSupplementaryUnicodeCharacters` | Pasaron: 4 invocaciones | Acepta 15/128 puntos de código Unicode; rechaza 14/129, incluidos caracteres suplementarios, con mensaje de longitud. |
| CP-005 | `UserPasswordValidatorTests` | `leavesNullToTheRequiredFieldConstraint`; `rejectsLengthsOutsideTheBoundsEvenForSupplementaryUnicodeCharacters`; `rejectsAnActualLongBlocklistedPasswordIgnoringCase`; `acceptsPassphrasesWithoutRequiringUppercaseDigitsOrSymbols` | Pasaron: 5 invocaciones | Ejercita delegación de null, rechazo por longitud/lista y aceptación de frase válida en `UserPasswordValidator.isValid`; no acredita un porcentaje de cobertura. |
| CP-006 | `PetHttpIntegrationTests` | `oneOwnerCreatesSeveralPetsAndServerAssignsOwnershipAndActiveState`; `anotherOwnersPetsStayHiddenAcrossListGetAndPatch` | Pasaron: 2 | Alta con HTTP 201 y propietario derivado del servidor; listados propios y GET/PATCH ajenos con 404 PET_NOT_FOUND, sin modificar la mascota. |
| CP-007 | `AppointmentHttpIntegrationTests` | `ownerRequestIsIdempotentAndReservesTheTurnForOnlyOneAppointment` | Pasó: 1 | Solicitud 201 REQUESTED, replay 200 con mismo UUID, un evento inicial y conflicto 409 APPOINTMENT_SLOT_OCCUPIED para otra intención sobre el turno; excluye el turno ocupado de opciones. |
| CP-008 | `AppointmentHttpIntegrationTests` | `rejectingReleasesTheSlotAndDifferentOwnersCanThenRequestIt` | Pasó: 1 | Rechazo administrativo 200 REJECTED; nueva solicitud 201 sobre el turno liberado y una única solicitud activa. |
| CP-009 | `AppointmentHttpIntegrationTests` | `onePetCannotHaveOverlappingAppointmentsWithDifferentVeterinarians` | Pasó: 1 | Primera solicitud 201; otra cita simultánea para la misma mascota: 409 APPOINTMENT_PET_ALREADY_SCHEDULED. |
| CP-010 | `AppointmentHttpIntegrationTests` | `newRequestsExcludeTodayButAllowTomorrowWithLessThanTwentyFourHoursOfNotice` | Pasó: 1 | Con reloj controlado, hoy responde 409 APPOINTMENT_OUTSIDE_REQUEST_WINDOW y se excluye de opciones; mañana a menos de 24 horas se ofrece y admite 201. |
| CP-011 | `AppointmentHttpIntegrationTests` | `reprogrammingRequiresRecordedPriorAgreementAndPreservesTheOriginalAppointmentOnValidationFailure` | Pasó: 1 | **Reasignación excepcional con cambio de horario** por veterinario deshabilitado: sin acuerdo 400 y origen conservado; con acuerdo 200, turno nuevo, canal WHATSAPP persistido y una tarea `OWNER_REASSIGNED`. |
| CP-012 | `AppointmentHttpIntegrationTests` | `dailyCutoffCancelsEveryExpiredRequestAndKeepsConfirmedAppointments` | Pasó: 1 | Dos invocaciones del corte: procesa 1 y luego 0; REQUESTED pasa a CANCELLED, CONFIRMED permanece y hay un único DAILY_CUTOFF. |

CP-004 y CP-005 comparten las dos invocaciones de rechazo por longitud. Las filas agrupan **16 métodos y 30 invocaciones únicas**; no deben sumarse como si fueran pruebas nuevas independientes. Tampoco se suman a las 508: están incluidas en esa suite.

## Fuentes y precisiones

- Registro: [código](../src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/user/UserSignupHttpIntegrationTests.java). XML local: `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.user.UserSignupHttpIntegrationTests.xml`.
- Contraseña: [pruebas](../src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/user/validation/UserPasswordValidatorTests.java) y [validador](../src/main/java/edu/uniquindio/grownupsvet/grownupsvet_backend/user/validation/UserPasswordValidator.java). XML local: `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.UserPasswordValidatorTests.xml`.
- Mascotas: [código](../src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/pet/PetHttpIntegrationTests.java). XML local: `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.pet.PetHttpIntegrationTests.xml`.
- Citas: [código](../src/test/java/edu/uniquindio/grownupsvet/grownupsvet_backend/appointment/AppointmentHttpIntegrationTests.java). XML local: `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.AppointmentHttpIntegrationTests.xml`.

No se encontraron métodos referenciados ausentes, fallidos u omitidos. El documento del 06/10 conserva «ejecución no repetida» y «resultado no registrado»: describen su elaboración histórica. Este anexo aporta la ejecución posterior sin reescribir esa evidencia.

El nombre de CP-011 no debe interpretarse como prueba de la reprogramación general P07: utiliza `POST /api/v1/appointments/{appointmentId}/veterinarian-reassignments`, no `/reschedulings`. Sus conteos verifican `OWNER_REASSIGNED` al propietario, **no el total de la cola**; deshabilitar al veterinario también genera un aviso administrativo. «Sin aviso» significa que el cambio rechazado no produjo una tarea nueva `OWNER_REASSIGNED`.

CP-007/009 prueban reintentos y conflictos mediante peticiones sucesivas; por sí solos no acreditan una carrera simultánea entre conexiones. Esa evidencia existe en otras pruebas de la suite, fuera de esta selección. La clasificación de técnicas del documento histórico no equivale a mediciones de cobertura.

**CP-013, CP-014 y CP-015 permanecen previstos** para registro/historial clínico, validación de datos clínicos y permisos clínicos. No se les atribuye ejecución ni se recategorizan; el proceso 3 queda fuera de esta entrega.

Los archivos de `target/` son evidencia local generada e ignorada por Git; requieren el workspace de verificación y no se presentan como descargas de GitHub. El [informe permanente del incremento](verificacion-procesos-1-2-preparacion-google-cloud-2026-10-09.md) conserva resultados y límites. Este anexo no acredita pantallas, revisión compartida, pruebas manuales en Mailtrap, recepción en buzones, despliegue Google Cloud ni aceptación integrada de los procesos.
