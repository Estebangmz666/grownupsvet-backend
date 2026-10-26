package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalEncounterResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalEncounterService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class AppointmentClinicalEncounterController {
    private final ClinicalEncounterService encounters;

    public AppointmentClinicalEncounterController(ClinicalEncounterService encounters) { this.encounters = encounters; }

    @GetMapping(path = "/api/v1/appointments/{appointmentId}/clinical-encounter", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED','CLINICAL_HISTORY_READ_SELF','CLINICAL_HISTORY_READ_ASSIGNED')")
    @Operation(operationId = "getAppointmentClinicalEncounter", summary = "Consultar la atención de una cita",
            description = "Descubrimiento autorizado con las mismas reglas que el detalle clínico. Los borradores no son visibles al propietario ni a veterinarios ajenos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atención autorizada", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Autenticación requerida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Permiso clínico requerido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Atención inexistente o no visible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    public ResponseEntity<ClinicalEncounterResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable @NotNull UUID appointmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(encounters.getByAppointment(UUID.fromString(jwt.getSubject()), appointmentId));
    }
}
