package edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermission;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.OwnerProfileResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UpdateOwnerProfileRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.OwnerProfileService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(OwnerProfileController.CURRENT_USER_PATH)
@Tag(name = "Perfil propio")
@SecurityRequirement(name = "bearerAuth")
public class OwnerProfileController {
    public static final String CURRENT_USER_PATH = "/api/v1/users/me";

    private final OwnerProfileService ownerProfileService;

    public OwnerProfileController(OwnerProfileService ownerProfileService) {
        this.ownerProfileService = ownerProfileService;
    }

    @Operation(operationId = "getCurrentOwnerProfile", summary = "Consultar el perfil propio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del propietario autenticado",
                    content = @Content(schema = @Schema(implementation = OwnerProfileResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de propietario",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_READ_SELF + "')")
    public ResponseEntity<OwnerProfileResponseDTO> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ownerProfileService.get(userId(jwt)));
    }

    @Operation(operationId = "updateCurrentOwnerProfile", summary = "Actualizar el teléfono propio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = OwnerProfileResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Teléfono o JSON inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de propietario",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_UPDATE_SELF + "')")
    public ResponseEntity<OwnerProfileResponseDTO> update(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody UpdateOwnerProfileRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ownerProfileService.update(userId(jwt), request));
    }

    @Operation(operationId = "deactivateCurrentOwnerAccount", summary = "Desactivar la cuenta propia",
            description = "Realiza una desactivación lógica. Los JWT emitidos dejan de aceptarse inmediatamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cuenta desactivada"),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "La cuenta no es de propietario",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @DeleteMapping
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_DEACTIVATE_SELF + "')")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt) {
        ownerProfileService.deactivate(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
