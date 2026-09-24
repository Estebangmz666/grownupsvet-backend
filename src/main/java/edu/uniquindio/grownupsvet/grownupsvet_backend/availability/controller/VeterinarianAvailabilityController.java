package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.service.VeterinarianAvailabilityService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/veterinarians/{veterinarianId}/availability-slots")
@Tag(name = "Disponibilidad veterinaria")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('VETERINARIAN_AVAILABILITY_MANAGE', 'VETERINARIAN_AVAILABILITY_READ_SELF')")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Solicitud, fechas o versión inválidas", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "401", description = "Sesión ausente, inválida o con permisos anteriores", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "403", description = "El rol no permite la operación", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Veterinario o turno no encontrado en esta agenda", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "409", description = "Conflicto de horario, estado o versión", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "415", description = "Tipo de contenido no admitido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class VeterinarianAvailabilityController {
    private final VeterinarianAvailabilityService service;

    public VeterinarianAvailabilityController(VeterinarianAvailabilityService service) { this.service = service; }

    @Operation(operationId = "createVeterinarianAvailabilitySlot", summary = "Publicar un turno de 30 minutos",
            description = "Solo un administrador puede publicar turnos futuros para un veterinario activo. El inicio debe caer en :00 o :30 de America/Bogota.")
    @ApiResponse(responseCode = "201", description = "Turno publicado", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilitySlotResponseDTO.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_MANAGE')")
    public ResponseEntity<VeterinarianAvailabilitySlotResponseDTO> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @Valid @RequestBody CreateVeterinarianAvailabilitySlotRequestDTO request) {
        VeterinarianAvailabilitySlotResponseDTO response = service.create(actorId(jwt), veterinarianId, request);
        return ResponseEntity.created(URI.create("/api/v1/veterinarians/" + veterinarianId + "/availability-slots/" + response.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @Operation(operationId = "listVeterinarianAvailabilitySlots", summary = "Consultar turnos de un veterinario",
            description = "Administradores consultan cualquier agenda; un veterinario consulta solo la propia. from/to son fechas locales inclusivas, máximo 31 días.")
    @ApiResponse(responseCode = "200", description = "Página de turnos", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilitySlotPageResponseDTO.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianAvailabilitySlotPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AvailabilitySlotStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(service.list(actorId(jwt), veterinarianId, from, to, status, page, size));
    }

    @Operation(operationId = "getVeterinarianAvailabilitySlot", summary = "Consultar un turno")
    @ApiResponse(responseCode = "200", description = "Turno de la agenda indicada", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilitySlotResponseDTO.class)))
    @GetMapping(value = "/{slotId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianAvailabilitySlotResponseDTO> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID slotId) {
        return privateResponse(service.get(actorId(jwt), veterinarianId, slotId));
    }

    @Operation(operationId = "rescheduleVeterinarianAvailabilitySlot", summary = "Cambiar la hora de un turno",
            description = "Solo permite cambiar turnos futuros. La protección frente a reservas reales se integrará con el módulo de citas. Conserva el UUID y registra auditoría.")
    @ApiResponse(responseCode = "200", description = "Turno actualizado", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilitySlotResponseDTO.class)))
    @PutMapping(value = "/{slotId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_MANAGE')")
    public ResponseEntity<VeterinarianAvailabilitySlotResponseDTO> reschedule(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID slotId,
            @Valid @RequestBody UpdateVeterinarianAvailabilitySlotRequestDTO request) {
        return privateResponse(service.reschedule(actorId(jwt), veterinarianId, slotId, request));
    }

    @Operation(operationId = "updateVeterinarianAvailabilitySlotStatus", summary = "Bloquear o publicar un turno",
            description = "Conserva la fila y registra cada cambio efectivo en un historial inmutable.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilitySlotResponseDTO.class)))
    @PatchMapping(value = "/{slotId}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_MANAGE')")
    public ResponseEntity<VeterinarianAvailabilitySlotResponseDTO> changeStatus(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID slotId,
            @Valid @RequestBody UpdateVeterinarianAvailabilitySlotStatusRequestDTO request) {
        return privateResponse(service.changeStatus(actorId(jwt), veterinarianId, slotId, request));
    }

    @Operation(operationId = "listVeterinarianAvailabilityEvents", summary = "Consultar auditoría de un turno",
            description = "Solo administradores consultan el actor, motivos y estados anteriores y nuevos.")
    @ApiResponse(responseCode = "200", description = "Página cronológica de eventos", content = @Content(schema = @Schema(implementation = VeterinarianAvailabilityEventPageResponseDTO.class)))
    @GetMapping(value = "/{slotId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('VETERINARIAN_AVAILABILITY_MANAGE')")
    public ResponseEntity<VeterinarianAvailabilityEventPageResponseDTO> events(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID slotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return privateResponse(service.events(actorId(jwt), veterinarianId, slotId, page, size));
    }

    private UUID actorId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
