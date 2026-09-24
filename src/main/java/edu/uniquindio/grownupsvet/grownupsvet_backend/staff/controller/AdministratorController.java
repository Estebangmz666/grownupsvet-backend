package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service.StaffManagementService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/administrators")
@Tag(name = "Administración de administradores")
@PreAuthorize("hasAuthority('ADMINISTRATOR_MANAGE')")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Petición o parámetros inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "403", description = "Rol sin permiso para esta operación", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Recurso inexistente o no accesible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "409", description = "Correo, matrícula o estado en conflicto", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "406", description = "Formato de respuesta no disponible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "415", description = "Tipo de contenido no admitido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
    @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})

public class AdministratorController {
    private final StaffManagementService service;
    public AdministratorController(StaffManagementService service) { this.service = service; }

    @Operation(operationId = "createAdministrator", summary = "Crear cuenta pendiente de administrador",
            description = "Guarda cuenta y perfil e inicia la invitación por correo. No acepta contraseña, estado ni rol del cliente. El correo permanece privado e inmutable.")
    @ApiResponse(responseCode = "201", description = "Cuenta pendiente creada e invitación programada", content = @Content(schema = @Schema(implementation = AdministratorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Invitaciones no disponibles; no se crea una cuenta parcial", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdministratorResponseDTO> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAdministratorRequestDTO request) {
        AdministratorResponseDTO response = service.createAdministrator(actorId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/administrators/" + response.id())).cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @Operation(operationId = "listAdministrators", summary = "Listar administradores",
            description = "Incluye todos los estados si se omite status. Orden fijo por createdAt descendente e id ascendente. page empieza en cero; page × size no puede superar 2147483647.")
    @ApiResponse(responseCode = "200", description = "Página de administradores", content = @Content(schema = @Schema(implementation = AdministratorPageResponseDTO.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdministratorPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.listAdministrators(actorId(jwt), page, size, status));
    }

    @Operation(operationId = "getAdministrator", summary = "Consultar administrador")
    @ApiResponse(responseCode = "200", description = "Perfil administrativo", content = @Content(schema = @Schema(implementation = AdministratorResponseDTO.class)))
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdministratorResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.getAdministrator(actorId(jwt), id));
    }

    @Operation(operationId = "updateAdministrator", summary = "Reemplazar datos editables del perfil",
            description = "Reemplazo completo de los datos editables. Conserva correo, rol y estado. ")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado", content = @Content(schema = @Schema(implementation = AdministratorResponseDTO.class)))
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdministratorResponseDTO> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateAdministratorRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.updateAdministrator(actorId(jwt), id, request));
    }

    @Operation(operationId = "updateAdministratorStatus", summary = "Deshabilitar o rehabilitar la cuenta",
            description = "DISABLED bloquea acceso e invalida invitaciones pendientes. ACTIVE solo rehabilita cuentas que ya establecieron contraseña; nunca completa una invitación. Conserva identidad y datos.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado", content = @Content(schema = @Schema(implementation = AdministratorResponseDTO.class)))
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdministratorResponseDTO> updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffStatusRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.updateAdministratorStatus(actorId(jwt), id, request));
    }

    private UUID actorId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
