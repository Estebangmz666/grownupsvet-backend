# I5: historia publicada, fase de acceso ya acordado

25/10/2026, America/Bogota. Addendum escrito antes de implementar. Complementa el plan I5 general. La fase inicial preservó la restricción de asignación vigente hasta recibir la decisión explícita del usuario que se registra al final.

## Alcance de esta fase

- OWNER activo consulta únicamente FINALIZED de mascotas propias, incluso archivadas. La propiedad histórica de la atención también debe corresponderle; no se introduce transferencia de propiedad.
- VETERINARIAN activo consulta todos los FINALIZED de una mascota si tiene actualmente una cita CONFIRMED/ASSIGNED de esa mascota. Al perder esa asignación conserva únicamente las atenciones que él finalizó, conforme a la extensión aprobada descrita al final. Una cita cancelada, rechazada o reasignada deja de habilitar lectura de atenciones ajenas.
- DRAFT solo se consulta por el veterinario vigente de su propia cita CONFIRMED/ASSIGNED. OWNER y veterinarios de otras citas de la misma mascota no ven ese borrador.
- ADMINISTRATOR y SUPER_ADMIN carecen de lectura clínica.

## Implementación prevista

1. Crear `ClinicalHistoryEntryResponseDTO` para resumen: identidad de atención/cita/mascota, responsable y nombre profesional, motivo y fecha de finalización; sin anamnesis, examen, diagnóstico ni fórmula en cada fila. El detalle y tratamiento conservan sus recursos actuales.
2. Crear `ClinicalHistoryPageResponseDTO` con items/page/size/totalElements/totalPages. `GET /api/v1/pets/{petId}/clinical-history`, page no negativa, size 1–100 y desplazamiento acotado según convención existente.
3. Centralizar política en helpers lectores de `ClinicalEncounterService`, reutilizados por listado, detalle y descubrimiento por cita. El tratamiento ya delega el acceso en ese servicio. Aplicar autorización dentro del SQL del recurso; no cargar contenido clínico ajeno antes de filtrar.
4. El listado valida acceso a la mascota aun si no hay finalizados, filtra FINALIZED y ordena por finalizedAt DESC,id DESC. Usar transacción REPEATABLE_READ para que permiso, total y filas de una respuesta correspondan a una misma instantánea PostgreSQL. No añadir migraciones.
5. Declarar page/size como **vista viva**: el desempate es determinista en cada consulta, pero nuevas finalizaciones entre peticiones pueden desplazar páginas. Para recargar, comenzar por page 0; no prometer una instantánea entre solicitudes.
6. Cache-Control no-store/private y errores Problem Details existentes. Sin sesión 401, sin facultad clínica 403, mascota/atención inexistente o no visible 404, parámetros inválidos 400.
7. Separar la respuesta de una mutación autorizada de la política GET posterior: completar la cita puede quitar el acceso por asignación vigente. La finalización/replay usa el contexto ya bloqueado y no debe llamar al GET que ahora filtra lectura posterior.

## Verificación posterior al congelamiento I4

La raíz ejecutará la compilación/focal coordinados; no ejecutar Maven en paralelo. Esta fase escribe implementación, sin editar todavía las pruebas Java de I4. Después se agregarán pruebas de historia propia/ajena/archivada/vacía, exclusión de borradores, acceso veterinario actual y pérdida por cancelación/reasignación, paridad detalle/tratamiento/cita, límites y desempate con fechas iguales. La prueba de instantánea necesita fixtures confirmados y una transacción real por request, sin transacción exterior que oculte el aislamiento. Actualizar ejemplos y OpenAPI al validar.

## Extensión de acceso aprobada antes de implementar

El usuario aprobó después de la fase inicial: durante una asignación CONFIRMED/ASSIGNED, el veterinario puede leer todos los antecedentes publicados de esa mascota. Después conserva lectura **únicamente de las atenciones que él finalizó**, no de toda su historia. La responsabilidad final procede de `veterinarian_id` en el registro FINALIZED inmutable; `created_by` no concede esa facultad.

Antes de modificar código se define esta extensión:

1. El guard de acceso a la mascota admite asignación vigente o existencia de al menos un FINALIZED de ese veterinario.
2. El predicado por atención devuelve FINALIZED cuando existe asignación vigente a la mascota o el responsable final coincide con el lector. El mismo filtro determina total, filas, detalle, tratamiento y búsqueda por cita.
3. El acceso a borradores sigue limitado a la cita CONFIRMED/ASSIGNED actualmente asignada; las reglas OWNER y administrativas no cambian.
4. Añadir prueba con atenciones finalizadas por dos veterinarios y una publicación posterior: tras perder asignación, cada uno conserva solo sus propios finalizados; una nueva asignación amplía temporalmente a todos los antecedentes y su cancelación vuelve a limitar los resultados. Los totales y todas las superficies de lectura deben seguir el mismo filtro.

## Resultado local comprobado

La ejecución focal coordinada por la raíz produjo `TEST-edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.ClinicalHistoryHttpIntegrationTests.xml` en `target/surefire-reports`: **10 pruebas, cero fallos, errores u omisiones**. Incluye la carrera de publicación entre count/filas con instantánea coherente, las reglas de acceso aprobadas y los ejemplos HTTP `clinical-history.json` y `clinical-history-empty.json`. No acredita publicación, aceptación de frontend ni el entorno desplegado.
