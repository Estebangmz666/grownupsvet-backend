package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto.StaffInvitationResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Invitaciones de personal")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ADMINISTRATOR_MANAGE', 'VETERINARIAN_MANAGE')")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sesión ausente, inválida o revocada",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "El rol actual no puede administrar esta cuenta",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Cuenta no encontrada",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "La cuenta ya tiene credenciales o está activa",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "429", description = "Máximo cinco invitaciones por cuenta en 24 horas y un minuto entre solicitudes",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Canal de invitaciones deshabilitado",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class StaffInvitationController {
    public static final String STAFF_INVITATIONS_PATH = "/api/v1/staff/{userId}/invitations";
    private final StaffInvitationService service;

    public StaffInvitationController(StaffInvitationService service) { this.service = service; }

    @Operation(operationId = "resendStaffInvitation", summary = "Reenviar invitación de personal",
            description = "SUPER_ADMIN invita ADMINISTRATOR; ADMINISTRATOR invita VETERINARIAN. "
                    + "Solo cuentas pendientes o deshabilitadas sin contraseña. Invalida el enlace anterior. "
                    + "202 acredita la cola persistente; no confirma entrega SMTP.")
    @ApiResponse(responseCode = "202", description = "Nueva invitación y tarea de correo persistidas",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StaffInvitationResponseDTO.class)))
    @PostMapping(value = STAFF_INVITATIONS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StaffInvitationResponseDTO> resend(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(service.resendInvitation(userId, UUID.fromString(jwt.getSubject())));
    }

    @Operation(operationId = "cancelStaffInvitation", summary = "Cancelar invitación de personal",
            description = "Revoca enlaces pendientes, elimina el secreto temporal de entrega y deja la cuenta sin credenciales deshabilitada.")
    @ApiResponse(responseCode = "204", description = "Invitación cancelada; repetir sobre la cuenta ya deshabilitada es idempotente")
    @DeleteMapping(STAFF_INVITATIONS_PATH)
    public ResponseEntity<Void> cancel(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        service.cancelInvitation(userId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
