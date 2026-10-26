package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalEncounterResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.FinalizeClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalFinalizationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/clinical-encounters/{encounterId}/finalizations", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Clinical encounters")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Campos clínicos o solicitud incompletos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Permiso clínico requerido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Atención inexistente o no visible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Versión, estado o intención incompatible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class ClinicalFinalizationController {
    private final ClinicalFinalizationService finalizations;

    public ClinicalFinalizationController(ClinicalFinalizationService finalizations) { this.finalizations = finalizations; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED')")
    @Operation(operationId = "finalizeClinicalEncounter", summary = "Finalizar atención y completar cita atómicamente",
            description = "Solo el veterinario asignado puede finalizar desde el inicio del turno. Requiere motivo, anamnesis, examen físico, diagnóstico, plan y ambas versiones. Publica la atención inmutable y completa la cita en una transacción; un replay equivalente no duplica auditoría ni programación clínica.")
    @ApiResponse(responseCode = "200", description = "Atención finalizada o repetición equivalente", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class)))
    public ResponseEntity<ClinicalEncounterResponseDTO> finalizeEncounter(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID encounterId,
            @Valid @RequestBody FinalizeClinicalEncounterRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(finalizations.finalizeEncounter(UUID.fromString(jwt.getSubject()), encounterId, request));
    }
}
