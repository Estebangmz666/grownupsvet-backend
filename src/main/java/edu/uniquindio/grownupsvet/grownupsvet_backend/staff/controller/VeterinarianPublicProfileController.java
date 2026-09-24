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
@RequestMapping("/api/v1/veterinarian-profiles")
@Tag(name = "Directorio veterinario para propietarios")
@PreAuthorize("hasAuthority('VETERINARIAN_PROFILE_READ')")
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

public class VeterinarianPublicProfileController {
    private final StaffManagementService service;
    public VeterinarianPublicProfileController(StaffManagementService service) { this.service = service; }

    @Operation(operationId = "listPublicVeterinarianProfiles", summary = "Consultar veterinarios activos",
            description = "Solo OWNER autenticado. Excluye correos privados, nacimiento y documentos no publicados. Orden por creación descendente e id ascendente; page empieza en cero.")
    @ApiResponse(responseCode = "200", description = "Página de fichas profesionales", content = @Content(schema = @Schema(implementation = VeterinarianPublicProfilePageResponseDTO.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianPublicProfilePageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.listPublicProfiles(actorId(jwt), page, size));
    }

    @Operation(operationId = "getPublicVeterinarianProfile", summary = "Consultar ficha profesional activa")
    @ApiResponse(responseCode = "200", description = "Ficha profesional sin correo privado", content = @Content(schema = @Schema(implementation = VeterinarianPublicProfileResponseDTO.class)))
    @GetMapping(value = "/{veterinarianId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianPublicProfileResponseDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.getPublicProfile(actorId(jwt), veterinarianId));
    }

    @Operation(operationId = "getPublicVeterinarianPhoto", summary = "Consultar foto del veterinario activo",
            description = "Amplía únicamente la lectura de la foto. Su carga y cambio siguen perteneciendo al titular de la cuenta.")
    @ApiResponse(responseCode = "200", description = "Foto de perfil", content = {
            @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))})
    @GetMapping(value = "/{veterinarianId}/photo", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId) {
        StaffBinaryContent content = service.getPublicPhoto(actorId(jwt), veterinarianId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).contentType(MediaType.parseMediaType(content.contentType()))
                .header("X-Content-Type-Options", "nosniff").contentLength(content.content().length).body(content.content());
    }

    @Operation(operationId = "getPublicVeterinarianDiploma", summary = "Descargar diploma publicado",
            description = "Solo cuando el título pertenece al veterinario solicitado, el diploma fue publicado y el veterinario continúa activo.")
    @ApiResponse(responseCode = "200", description = "Diploma publicado", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/{veterinarianId}/qualifications/{qualificationId}/diploma", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> diploma(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId,
            @PathVariable UUID qualificationId) {
        return StaffBinaryResponses.diploma(service.getPublicDiploma(actorId(jwt), veterinarianId, qualificationId));
    }

    private UUID actorId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
