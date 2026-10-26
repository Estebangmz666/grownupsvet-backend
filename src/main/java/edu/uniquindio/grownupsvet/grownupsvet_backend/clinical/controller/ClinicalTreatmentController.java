package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalTreatmentResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalTreatmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalTreatmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/clinical-encounters/{encounterId}/treatment", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clinical encounters")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Datos de tratamiento inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Permiso clínico requerido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Atención inexistente o no visible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Versión, estado o intención incompatible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class ClinicalTreatmentController {
    private final ClinicalTreatmentService treatments;

    public ClinicalTreatmentController(ClinicalTreatmentService treatments) { this.treatments = treatments; }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED','CLINICAL_HISTORY_READ_SELF','CLINICAL_HISTORY_READ_ASSIGNED')")
    @Operation(operationId = "getClinicalTreatment", summary = "Consultar fórmula y controles de una atención",
            description = "Aplica la misma autorización que el detalle clínico. Devuelve la versión compartida de la atención y listas ordenadas. El borrador solo es visible al veterinario vigente de la cita.")
    @ApiResponse(responseCode = "200", description = "Fórmula y controles autorizados", content = @Content(schema = @Schema(implementation = ClinicalTreatmentResponseDTO.class)))
    public ResponseEntity<ClinicalTreatmentResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID encounterId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(treatments.get(UUID.fromString(jwt.getSubject()), encounterId));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED')")
    @Operation(operationId = "updateClinicalTreatment", summary = "Reemplazar fórmula y controles del borrador",
            description = "Reemplaza ambas listas completas, con máximo 50 elementos por lista e IDs únicos dentro de cada lista. Las listas vacías eliminan sus elementos. No modifica los textos clínicos; comparte expectedVersion con toda la atención. clientRequestId conserva la intención y repetirla devuelve el estado actual sin nuevos eventos. Dosis positiva con hasta 12 enteros y 6 decimales; intervalo de 1 a 168 horas; inicio y fin explícitos separados por un máximo de 366 días. Fechas con offset obligatorio, normalizadas a microsegundos UTC; la presentación de avisos usa America/Bogota. No calcula dosis ni exige que el inicio o los controles sean futuros. Los finalizados son inmutables.")
    @ApiResponse(responseCode = "200", description = "Tratamiento guardado o repetición equivalente", content = @Content(schema = @Schema(implementation = ClinicalTreatmentResponseDTO.class)))
    public ResponseEntity<ClinicalTreatmentResponseDTO> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID encounterId,
            @Valid @RequestBody UpdateClinicalTreatmentRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(treatments.update(UUID.fromString(jwt.getSubject()), encounterId, request));
    }
}
