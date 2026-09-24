package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.AvailableVeterinarianSlotPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.service.VeterinarianAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability-slots")
@Tag(name = "Disponibilidad veterinaria")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_READ_AVAILABLE')")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Rango o paginación inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "401", description = "Sesión ausente o inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "403", description = "Solo propietarios pueden consultar opciones", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class AvailableVeterinarianSlotController {
    private final VeterinarianAvailabilityService service;
    public AvailableVeterinarianSlotController(VeterinarianAvailabilityService service) { this.service = service; }

    @Operation(operationId = "listAvailableVeterinarianSlots", summary = "Consultar turnos solicitables",
            description = "Solo muestra turnos publicados de veterinarios activos desde 2 horas hasta 60 días después de la consulta. from/to son fechas locales inclusivas y admiten hasta 31 días.")
    @ApiResponse(responseCode = "200", description = "Página de turnos solicitables, sin datos administrativos", content = @Content(schema = @Schema(implementation = AvailableVeterinarianSlotPageResponseDTO.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AvailableVeterinarianSlotPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID veterinarianId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var body = service.available(UUID.fromString(jwt.getSubject()), from, to, veterinarianId, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
