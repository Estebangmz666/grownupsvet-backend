package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalHistoryPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalHistoryService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Clinical history", description = "Consulta autorizada de atenciones publicadas por mascota.")
@SecurityRequirement(name = "bearerAuth")
public class ClinicalHistoryController {
    private final ClinicalHistoryService service;

    public ClinicalHistoryController(ClinicalHistoryService service) { this.service = service; }

    @GetMapping(path = "/api/v1/pets/{petId}/clinical-history", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('CLINICAL_HISTORY_READ_SELF','CLINICAL_HISTORY_READ_ASSIGNED')")
    @Operation(operationId = "getPetClinicalHistory", summary = "Consultar historia publicada de una mascota",
            description = "Propietario activo de su mascota, incluso archivada. Un veterinario activo con cita CONFIRMED/ASSIGNED actual consulta antecedentes publicados; " +
                    "sin esa asignación consulta únicamente atenciones que él finalizó. " +
                    "Solo FINALIZED, orden finalizedAt e id descendentes. Vista viva page/size: nuevas publicaciones pueden desplazar páginas entre peticiones; " +
                    "recargar desde page 0. No incluye borradores ni concede lectura administrativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historia publicada paginada, incluida una lista vacía", content = @Content(schema = @Schema(implementation = ClinicalHistoryPageResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Identificador o paginación inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Autenticación requerida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso de historia clínica", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Mascota inexistente o no visible para la cuenta", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    public ResponseEntity<ClinicalHistoryPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.list(UUID.fromString(jwt.getSubject()), petId, page, size));
    }
}
