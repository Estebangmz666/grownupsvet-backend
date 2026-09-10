package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordRecoveryRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordRecoveryResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordRecoveryVerificationRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordRecoveryVerificationResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordResetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service.UserPasswordRecoveryService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recuperación de acceso")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "JSON o campos inválidos; código o permiso inválido, vencido o consumido",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "406", description = "Formato de respuesta no disponible",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "415", description = "El cuerpo debe ser application/json",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "429", description = "Límite de recuperación alcanzado; Retry-After indica segundos de espera",
                headers = @Header(name = "Retry-After", description = "Segundos hasta poder reintentar",
                        schema = @Schema(type = "integer", format = "int64", minimum = "1")),
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Recuperación deshabilitada o envío no disponible",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class UserPasswordRecoveryController {
    public static final String PASSWORD_RECOVERIES_PATH = "/api/v1/auth/password-recoveries";
    public static final String PASSWORD_RECOVERY_VERIFICATIONS_PATH = PASSWORD_RECOVERIES_PATH + "/verifications";
    public static final String PASSWORD_RESETS_PATH = "/api/v1/auth/password-resets";
    private final UserPasswordRecoveryService recoveryService;

    public UserPasswordRecoveryController(UserPasswordRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Operation(operationId = "requestUserPasswordRecovery", summary = "Solicitar recuperación por correo",
            description = "Responde igual para correos desconocidos y cuentas inactivas. Un código de seis dígitos "
                    + "vence a los diez minutos. Solicitar otro invalida el código y permiso anteriores. "
                    + "202 significa solicitud aceptada y envío asíncrono; no confirma recepción del correo.")
    @ApiResponse(responseCode = "202", description = "Solicitud aceptada",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserPasswordRecoveryResponseDTO.class)))
    @PostMapping(value = PASSWORD_RECOVERIES_PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserPasswordRecoveryResponseDTO> requestCode(
            @Valid @RequestBody UserPasswordRecoveryRequestDTO request, HttpServletRequest servletRequest) {
        recoveryService.requestCode(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(UserPasswordRecoveryResponseDTO.accepted());
    }

    @Operation(operationId = "verifyUserPasswordRecoveryCode", summary = "Verificar código de recuperación",
            description = "Consume el código válido y emite un permiso opaco de un uso durante cinco minutos. "
                    + "Permite hasta cinco fallos por código y diez verificaciones por correo en una hora. "
                    + "El permiso únicamente autoriza el restablecimiento de contraseña.")
    @ApiResponse(responseCode = "200", description = "Código verificado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserPasswordRecoveryVerificationResponseDTO.class)))
    @PostMapping(value = PASSWORD_RECOVERY_VERIFICATIONS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserPasswordRecoveryVerificationResponseDTO> verifyCode(
            @Valid @RequestBody UserPasswordRecoveryVerificationRequestDTO request, HttpServletRequest servletRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                recoveryService.verifyCode(request.email(), request.code(), servletRequest.getRemoteAddr()));
    }

    @Operation(operationId = "resetUserPassword", summary = "Restablecer contraseña",
            description = "Consume el permiso y aplica la misma política de contraseña del registro. "
                    + "Exige confirmación idéntica. Invalida todas las sesiones anteriores y requiere un nuevo login. "
                    + "Nunca reactiva una cuenta ni crea automáticamente una sesión.")
    @ApiResponse(responseCode = "204", description = "Contraseña restablecida y sesiones previas invalidadas")
    @PostMapping(value = PASSWORD_RESETS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody UserPasswordResetRequestDTO request,
                                               HttpServletRequest servletRequest) {
        recoveryService.resetPassword(request, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
