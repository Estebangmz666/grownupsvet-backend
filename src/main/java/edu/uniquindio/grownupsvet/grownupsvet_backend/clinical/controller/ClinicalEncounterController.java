package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalEncounterResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.CreateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalEncounterService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(path = ClinicalEncounterController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Clinical encounters", description = "Borradores y atenciones clínicas con autorización por recurso.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Solicitud inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "Permiso clínico requerido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Versión, estado o intención incompatible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class ClinicalEncounterController {
    public static final String BASE_PATH = "/api/v1/clinical-encounters";
    private final ClinicalEncounterService service;

    public ClinicalEncounterController(ClinicalEncounterService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED')")
    @Operation(operationId = "createClinicalEncounter", summary = "Crear borrador desde cita confirmada",
            description = "Solo veterinario asignado activo. Una atención por cita, datos de mascota y propietario derivados del servidor. clientRequestId conserva la intención original; repetirla no crea otra atención.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Borrador creado", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class))),
            @ApiResponse(responseCode = "200", description = "Repetición equivalente", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class)))
    })
    public ResponseEntity<ClinicalEncounterResponseDTO> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateClinicalEncounterRequestDTO request) {
        var result = service.create(UUID.fromString(jwt.getSubject()), request);
        var response = result.created() ? ResponseEntity.created(URI.create(BASE_PATH + "/" + result.encounter().id())) : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore().cachePrivate()).body(result.encounter());
    }

    @GetMapping("/{encounterId}")
    @PreAuthorize("hasAnyAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED','CLINICAL_HISTORY_READ_SELF','CLINICAL_HISTORY_READ_ASSIGNED')")
    @Operation(operationId = "getClinicalEncounter", summary = "Consultar una atención autorizada",
            description = "Un borrador solo es visible al veterinario vigente de su cita CONFIRMED/ASSIGNED. " +
                    "Un finalizado es visible al propietario de su mascota, al veterinario con una cita CONFIRMED/ASSIGNED actual de esa mascota " +
                    "o al veterinario que lo finalizó. Sin asignación vigente, este último conserva únicamente sus propios finalizados; " +
                    "los roles administrativos no reciben contenido clínico.")
    @ApiResponse(responseCode = "200", description = "Atención autorizada", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class)))
    public ResponseEntity<ClinicalEncounterResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID encounterId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.get(UUID.fromString(jwt.getSubject()), encounterId));
    }

    @PutMapping(path = "/{encounterId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('CLINICAL_ENCOUNTER_MANAGE_ASSIGNED')")
    @Operation(operationId = "updateClinicalEncounter", summary = "Reemplazar campos de un borrador",
            description = "Versión obligatoria y clientRequestId idempotente. Campos omitidos o blancos se limpian. El veterinario vigente puede continuar el borrador reasignado conservando su creador y la autoría de los eventos. Los finalizados son inmutables.")
    @ApiResponse(responseCode = "200", description = "Borrador guardado o repetición equivalente", content = @Content(schema = @Schema(implementation = ClinicalEncounterResponseDTO.class)))
    public ResponseEntity<ClinicalEncounterResponseDTO> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID encounterId,
            @Valid @RequestBody UpdateClinicalEncounterRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.update(UUID.fromString(jwt.getSubject()), encounterId, request));
    }
}
