package edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.UserSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UserSignupController.REGISTRATION_PATH)
@Tag(name = "Registro de propietarios")
public class UserSignupController {
    public static final String REGISTRATION_PATH = "/api/v1/auth/registrations";
    private final UserSignupService userSignupService;

    public UserSignupController(UserSignupService userSignupService) {
        this.userSignupService = userSignupService;
    }

    @Operation(operationId = "signupOwner", summary = "Crear una cuenta de propietario",
            description = "Crea una cuenta OWNER activa y su perfil en una transacción. El cliente no asigna rol ni estado. Devuelve un recibo sin token; la interfaz conduce al login. Las mascotas y la foto se incorporan en operaciones posteriores.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserSignupResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "JSON, propiedades o campos inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class),
                            examples = @ExampleObject(value = """
                                    {"type":"about:blank","title":"Conflicto con el estado actual","status":409,"detail":"Este correo ya está registrado. Puedes iniciar sesión o recuperar tu contraseña.","instance":"urn:uuid:7d667530-867b-4c78-a458-0904fb82d574","errorCode":"EMAIL_ALREADY_REGISTERED","fieldErrors":[]}
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
    public ResponseEntity<UserSignupResponseDTO> signup(@Valid @RequestBody UserSignupRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userSignupService.signup(request));
    }
}
