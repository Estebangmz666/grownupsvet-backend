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

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/veterinarians/{veterinarianId}/qualifications")
@Tag(name = "Títulos y diplomas veterinarios")
@PreAuthorize("hasAuthority('VETERINARIAN_MANAGE')")
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

public class VeterinarianQualificationController {
    private final StaffManagementService service;
    public VeterinarianQualificationController(StaffManagementService service) { this.service = service; }

    @Operation(operationId = "createVeterinarianQualification", summary = "Agregar título académico",
            description = "Hasta 20 títulos por veterinario, incluido el pregrado base. Los cursos no se presentan como especializaciones.")
    @ApiResponse(responseCode = "201", description = "Título creado", content = @Content(schema = @Schema(implementation = VeterinarianQualificationResponseDTO.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianQualificationResponseDTO> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @Valid @RequestBody VeterinarianQualificationRequestDTO request) {
        var response = service.createQualification(actorId(jwt), veterinarianId, request);
        return ResponseEntity.created(URI.create("/api/v1/veterinarians/" + veterinarianId + "/qualifications/" + response.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @Operation(operationId = "listVeterinarianQualifications", summary = "Listar formación académica",
            description = "Primero el título base; después los adicionales por orden de creación e id.")
    @ApiResponse(responseCode = "200", description = "Títulos académicos")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<VeterinarianQualificationResponseDTO>> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.listQualifications(actorId(jwt), veterinarianId));
    }

    @Operation(operationId = "getVeterinarianQualification", summary = "Consultar un título académico")
    @ApiResponse(responseCode = "200", description = "Título académico", content = @Content(schema = @Schema(implementation = VeterinarianQualificationResponseDTO.class)))
    @GetMapping(value = "/{qualificationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianQualificationResponseDTO> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID qualificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.getQualification(actorId(jwt), veterinarianId, qualificationId));
    }

    @Operation(operationId = "updateVeterinarianQualification", summary = "Reemplazar datos del título",
            description = "Cambiar datos académicos retira la publicación del diploma para permitir una nueva revisión; conserva el archivo privado. El título base mantiene el tipo UNDERGRADUATE.")
    @ApiResponse(responseCode = "200", description = "Título actualizado", content = @Content(schema = @Schema(implementation = VeterinarianQualificationResponseDTO.class)))
    @PutMapping(value = "/{qualificationId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianQualificationResponseDTO> update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID qualificationId,
            @Valid @RequestBody VeterinarianQualificationRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.updateQualification(actorId(jwt), veterinarianId, qualificationId, request));
    }

    @Operation(operationId = "deleteVeterinarianQualification", summary = "Eliminar título adicional y su diploma",
            description = "El título base es obligatorio y no se puede eliminar.")
    @ApiResponse(responseCode = "204", description = "Título eliminado", content = @Content)
    @DeleteMapping("/{qualificationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId,
            @PathVariable UUID qualificationId) {
        service.deleteQualification(actorId(jwt), veterinarianId, qualificationId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }

    @Operation(operationId = "replaceVeterinarianDiploma", summary = "Cargar o reemplazar diploma PDF",
            description = "Un PDF por título, máximo 5 MiB y 50 páginas. Solo documentos sin cifrado, formularios, acciones ni adjuntos. published=true confirma la revisión y autoriza su publicación para propietarios; por defecto es privado. El servidor valida estructura y límites, sin afirmar análisis antivirus.")
    @ApiResponse(responseCode = "200", description = "Diploma almacenado", content = @Content(schema = @Schema(implementation = VeterinarianQualificationResponseDTO.class)))
    @ApiResponse(responseCode = "413", description = "PDF mayor de 5 MiB", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    @ApiResponse(responseCode = "503", description = "Validación temporalmente ocupada; reintentar", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    @PutMapping(value = "/{qualificationId}/diploma", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianQualificationResponseDTO> upload(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID qualificationId,
            @RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean published) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.uploadDiploma(actorId(jwt), veterinarianId, qualificationId, file, published));
    }

    @Operation(operationId = "getAdministrativeVeterinarianDiploma", summary = "Descargar diploma para revisión administrativa")
    @ApiResponse(responseCode = "200", description = "PDF del título", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/{qualificationId}/diploma", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId,
            @PathVariable UUID qualificationId) {
        return StaffBinaryResponses.diploma(service.getAdministrativeDiploma(actorId(jwt), veterinarianId, qualificationId));
    }

    @Operation(operationId = "updateVeterinarianDiplomaPublication", summary = "Publicar o retirar la publicación de un diploma",
            description = "Requiere un diploma existente. published=true confirma que el administrador revisó el archivo y autoriza su consulta a propietarios cuando el veterinario esté activo.")
    @ApiResponse(responseCode = "200", description = "Publicación actualizada", content = @Content(schema = @Schema(implementation = VeterinarianQualificationResponseDTO.class)))
    @PatchMapping(value = "/{qualificationId}/diploma/publication", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VeterinarianQualificationResponseDTO> publish(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID veterinarianId, @PathVariable UUID qualificationId,
            @Valid @RequestBody UpdateDiplomaPublicationRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.updateDiplomaPublication(actorId(jwt), veterinarianId, qualificationId, request));
    }

    @Operation(operationId = "deleteVeterinarianDiploma", summary = "Eliminar diploma conservando el título")
    @ApiResponse(responseCode = "204", description = "Diploma eliminado o ya ausente", content = @Content)
    @DeleteMapping("/{qualificationId}/diploma")
    public ResponseEntity<Void> deleteDiploma(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID veterinarianId,
            @PathVariable UUID qualificationId) {
        service.deleteDiploma(actorId(jwt), veterinarianId, qualificationId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }

    private UUID actorId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
