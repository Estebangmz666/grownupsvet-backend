package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.CreateVeterinarianAvailabilitySlotBatchRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.CreateVeterinarianAvailabilitySlotBatchResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.service.VeterinarianAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
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
@RequestMapping("/api/v1/veterinarians/{veterinarianId}/availability-slot-batches")
@Tag(name = "Disponibilidad veterinaria")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_MANAGE')")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Rango o generación inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "401", description = "Sesión ausente o inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "403", description = "El rol no permite crear turnos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Veterinario no encontrado", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "409", description = "Un turno solicitado ya existe", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "415", description = "Tipo de contenido no admitido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class VeterinarianAvailabilityBatchController {
    private final VeterinarianAvailabilityService service;
    public VeterinarianAvailabilityBatchController(VeterinarianAvailabilityService service) { this.service = service; }

    @Operation(operationId = "createVeterinarianAvailabilitySlotBatch", summary = "Generar un lote de turnos",
            description = "Genera filas concretas para un rango inclusivo máximo de 31 días y no más de 1000 turnos. 24:00 solo se admite como fin exclusivo.")
    @ApiResponse(responseCode = "201", description = "Turnos creados de forma atómica", content = @Content(schema = @Schema(implementation = CreateVeterinarianAvailabilitySlotBatchResponseDTO.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateVeterinarianAvailabilitySlotBatchResponseDTO> createBatch(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @Valid @RequestBody CreateVeterinarianAvailabilitySlotBatchRequestDTO request) {
        var response = service.createBatch(UUID.fromString(jwt.getSubject()), veterinarianId, request);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }
}
