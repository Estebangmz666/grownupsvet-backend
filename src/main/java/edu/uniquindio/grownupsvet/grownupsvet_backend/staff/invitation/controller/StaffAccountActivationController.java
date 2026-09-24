package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto.StaffAccountActivationRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Activación de cuenta de personal")
public class StaffAccountActivationController {
    public static final String ACCOUNT_ACTIVATIONS_PATH = "/api/v1/auth/account-activations";
    private final StaffInvitationService service;

    public StaffAccountActivationController(StaffInvitationService service) { this.service = service; }

    @Operation(operationId = "activateStaffAccount", summary = "Activar cuenta de personal mediante invitación",
            description = "Consume un enlace opaco de un uso, válido durante 48 horas. Solo activa cuentas pendientes sin credenciales. "
                    + "Aplica la política de contraseña del registro, con confirmación idéntica. No crea automáticamente una sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cuenta activada; puede iniciar sesión con la contraseña elegida"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos, confirmación distinta o enlace inválido, vencido o consumido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "415", description = "El cuerpo debe ser application/json",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", description = "Activación por invitación deshabilitada",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PostMapping(value = ACCOUNT_ACTIVATIONS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> activate(@Valid @RequestBody StaffAccountActivationRequestDTO request) {
        service.activateAccount(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
