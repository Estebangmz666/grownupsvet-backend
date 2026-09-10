package edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermission;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserProfilePhotoContent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.OwnerProfileService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.UserProfilePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping(OwnerProfileService.PROFILE_PHOTO_PATH)
@Tag(name = "Foto de perfil propia")
@SecurityRequirement(name = "bearerAuth")
public class UserProfilePhotoController {
    private final UserProfilePhotoService userProfilePhotoService;

    public UserProfilePhotoController(UserProfilePhotoService userProfilePhotoService) {
        this.userProfilePhotoService = userProfilePhotoService;
    }

    @Operation(operationId = "getCurrentUserProfilePhoto", summary = "Consultar la foto propia")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bytes JPEG o PNG procesados",
                    content = {@Content(mediaType = MediaType.IMAGE_JPEG_VALUE,
                            schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = MediaType.IMAGE_PNG_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "La cuenta no tiene foto",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @GetMapping(produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE,
            MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_PHOTO_READ_SELF + "')")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal Jwt jwt) {
        UserProfilePhotoContent photo = userProfilePhotoService.get(userId(jwt));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.content());
    }

    @Operation(operationId = "putCurrentUserProfilePhoto", summary = "Crear o reemplazar la foto propia",
            description = "Recibe el campo multipart file. Acepta JPEG/PNG de hasta 2 MiB y almacena una copia procesada sin metadatos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Foto guardada"),
            @ApiResponse(responseCode = "400", description = "Imagen vacía, inválida o con dimensiones no permitidas",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "413", description = "Archivo superior a 2 MiB",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "415", description = "El contenido real no es JPEG ni PNG",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_PHOTO_UPDATE_SELF + "')")
    public ResponseEntity<Void> put(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        userProfilePhotoService.put(userId(jwt), file);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "deleteCurrentUserProfilePhoto", summary = "Eliminar la foto propia",
            description = "Es idempotente: devuelve 204 aunque la cuenta no tenga foto.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Foto eliminada o ya inexistente"),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @DeleteMapping
    @PreAuthorize("hasAuthority('" + UserPermission.Constants.PROFILE_PHOTO_UPDATE_SELF + "')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        userProfilePhotoService.delete(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
