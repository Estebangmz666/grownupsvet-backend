package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermission;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.CreatePetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.PetPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.PetResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.UpdatePetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.service.PetService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(PetController.PETS_PATH)
@Tag(name = "Mascotas propias")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "406", description = "Formato de respuesta no disponible",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "415", description = "Tipo de contenido de la petición no admitido",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "JSON, tipos, campos o parámetros inválidos",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "La cuenta no es de propietario",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class PetController {
    public static final String PETS_PATH = "/api/v1/pets";
    private final PetService petService;

    public PetController(PetService petService) { this.petService = petService; }

    @Operation(operationId = "createOwnPet", summary = "Registrar una mascota propia",
            description = "Crea una mascota activa vinculada exclusivamente al OWNER identificado por el JWT. Un propietario puede registrar varias mascotas y cada mascota tiene un único dueño.")
    @ApiResponse(responseCode = "201", description = "Mascota creada; Location indica la ruta de consulta",
            headers = @Header(name = "Location", description = "Ruta relativa de la mascota creada.",
                    schema = @Schema(type = "string", format = "uri-reference", example = "/api/v1/pets/7d667530-867b-4c78-a458-0904fb82d574")),
            content = @Content(schema = @Schema(implementation = PetResponseDTO.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PET_CREATE_SELF + "')")
    public ResponseEntity<PetResponseDTO> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreatePetRequestDTO request) {
        PetResponseDTO response = petService.create(ownerId(jwt), request);
        return ResponseEntity.created(URI.create(PETS_PATH + "/" + response.id()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @Operation(operationId = "listOwnPets", summary = "Listar mascotas propias",
            description = "Incluye activas y archivadas si se omite active; true filtra activas y false archivadas. Orden fijo por createdAt descendente e id ascendente. Las páginas empiezan en cero y page × size no puede superar 2147483647.")
    @ApiResponse(responseCode = "200", description = "Página de mascotas del propietario autenticado",
            content = @Content(schema = @Schema(implementation = PetPageResponseDTO.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PET_READ_SELF + "')")
    public ResponseEntity<PetPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                  @Parameter(description = "Omitir incluye todos los estados.")
                                                  @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(petService.list(ownerId(jwt), page, size, active));
    }

    @Operation(operationId = "getOwnPet", summary = "Consultar una mascota propia")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mascota activa o archivada del propietario autenticado",
                    content = @Content(schema = @Schema(implementation = PetResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Mascota inexistente o no perteneciente al propietario",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @GetMapping(value = "/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PET_READ_SELF + "')")
    public ResponseEntity<PetResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(petService.get(ownerId(jwt), petId));
    }

    @Operation(operationId = "updateOwnPet", summary = "Actualizar, archivar o reactivar una mascota propia",
            description = "Cambio parcial: omitir conserva; null solo elimina raza, sexo o nacimiento. active=false archiva conservando identidad e historial, active=true reactiva. No transfiere propiedad ni elimina físicamente. Al quitar una fecha estimada también se debe enviar dateOfBirthEstimated=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mascota actualizada",
                    content = @Content(schema = @Schema(implementation = PetResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Mascota inexistente o no perteneciente al propietario",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PatchMapping(value = "/{petId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PET_UPDATE_SELF + "')")
    public ResponseEntity<PetResponseDTO> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                                @Valid @RequestBody UpdatePetRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(petService.update(ownerId(jwt), petId, request));
    }

    private UUID ownerId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
