package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.UserAuthenticationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UserSessionController.SESSIONS_PATH)
@Tag(name = "Sesiones")
public class UserSessionController {
    public static final String SESSIONS_PATH = "/api/v1/auth/sessions";

    private final UserAuthenticationService authenticationService;

    public UserSessionController(UserAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Operation(operationId = "createUserSession", summary = "Iniciar sesión",
            description = "Verifica correo y contraseña y emite un JWT RS256 por 24 horas. No emite refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión creada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserLoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "JSON, propiedades o campos inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Correo, contraseña o estado de cuenta no válidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class),
                            examples = @ExampleObject(value = """
                                    {"type":"about:blank","title":"Autenticación requerida","status":401,"detail":"El correo o la contraseña no son correctos.","instance":"urn:uuid:7d667530-867b-4c78-a458-0904fb82d574","errorCode":"INVALID_CREDENTIALS","fieldErrors":[]}
                                    """))),
            @ApiResponse(responseCode = "406", description = "El formato solicitado en Accept no está disponible",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "415", description = "La petición debe ser application/json",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "500", description = "Fallo interno sin detalles sensibles",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserLoginResponseDTO> createSession(@Valid @RequestBody UserLoginRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authenticationService.authenticate(request));
    }
}
