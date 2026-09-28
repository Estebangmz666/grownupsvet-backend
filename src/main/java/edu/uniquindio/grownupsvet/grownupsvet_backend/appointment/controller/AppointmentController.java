package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermission;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Citas veterinarias")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Campos, tipos, rango o paginación inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Sesión ausente, vencida o inválida", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "403", description = "El rol no tiene permiso para esta operación", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Fallo interno sin información sensible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
})
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }

    @Operation(operationId = "requestAppointment", summary = "Solicitar una cita",
            description = "La solicitud reserva el turno inmediatamente. El propietario se deriva del JWT. Solo admite fechas posteriores al día actual en America/Bogota y hasta 60 días. clientRequestId identifica una intención persistente: misma clave y contenido devuelve la cita actual; contenido distinto devuelve 409.")
    @ApiResponses({
            @ApiResponse(responseCode="201",description="Solicitud creada",content=@Content(schema=@Schema(implementation=AppointmentResponseDTO.class))),
            @ApiResponse(responseCode="200",description="Reintento idempotente",content=@Content(schema=@Schema(implementation=AppointmentResponseDTO.class))),
            @ApiResponse(responseCode="400",description="Solicitud inválida",content=@Content(mediaType=MediaType.APPLICATION_PROBLEM_JSON_VALUE,schema=@Schema(implementation=ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode="404",description="Mascota propia o turno inexistente",content=@Content(mediaType=MediaType.APPLICATION_PROBLEM_JSON_VALUE,schema=@Schema(implementation=ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode="409",description="Turno ocupado, vencido o clave idempotente incompatible",content=@Content(mediaType=MediaType.APPLICATION_PROBLEM_JSON_VALUE,schema=@Schema(implementation=ApiErrorResponseDTO.class)))
    })
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('"+UserPermission.Constants.APPOINTMENT_CREATE_SELF+"')")
    public ResponseEntity<AppointmentResponseDTO> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateAppointmentRequestDTO request) {
        var result=service.create(UUID.fromString(jwt.getSubject()),request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/appointments/"+result.appointment().id()))
                    .cacheControl(CacheControl.noStore().cachePrivate()).body(result.appointment());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(result.appointment());
    }

    @Operation(operationId="listAppointments",summary="Consultar agenda de citas",description="La consulta se filtra por el rol antes de paginar: propias para propietarios, asignadas actualmente para veterinarios y todas para administradores. from/to son fechas locales inclusivas en America/Bogota, con rango máximo de 31 días. Orden estable por inicio e identificador. No incluye correos.")
    @ApiResponse(responseCode = "200", description = "Página de citas autorizadas; items puede estar vacío", content = @Content(schema = @Schema(implementation = AppointmentPageResponseDTO.class)))
    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('"+UserPermission.Constants.APPOINTMENT_READ_SELF+"','"+UserPermission.Constants.APPOINTMENT_READ_ASSIGNED+"','"+UserPermission.Constants.APPOINTMENT_READ_ALL+"')")
    public ResponseEntity<AppointmentPageResponseDTO> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) AppointmentStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(UUID.fromString(jwt.getSubject()),from,to,status,page,size));
    }

    @Operation(operationId="getAppointment",summary="Consultar una cita")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita autorizada sin correo del propietario", content = @Content(schema = @Schema(implementation = AppointmentResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cita inexistente o ajena", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @GetMapping(path="/{appointmentId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('"+UserPermission.Constants.APPOINTMENT_READ_SELF+"','"+UserPermission.Constants.APPOINTMENT_READ_ASSIGNED+"','"+UserPermission.Constants.APPOINTMENT_READ_ALL+"')")
    public ResponseEntity<AppointmentResponseDTO> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.get(UUID.fromString(jwt.getSubject()),appointmentId));
    }

    @Operation(operationId="updateAppointmentStatus",summary="Confirmar o rechazar una solicitud", description="Solo administradores: REQUESTED a CONFIRMED o REJECTED con expectedVersion. Rechazar requiere motivo. Confirmar exige propietario, mascota, veterinario y turno elegibles y plazo de confirmación vigente. No admite cancelación manual ni estados clínicos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transición aplicada o repetición equivalente", content = @Content(schema = @Schema(implementation = AppointmentResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cita inexistente", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Versión, estado, plazo o requisitos de confirmación incompatibles", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PatchMapping(path="/{appointmentId}/status",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('"+UserPermission.Constants.APPOINTMENT_MANAGE+"')")
    public ResponseEntity<AppointmentResponseDTO> updateStatus(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.updateStatus(UUID.fromString(jwt.getSubject()),appointmentId,request));
    }

    @Operation(operationId="listAppointmentEvents",summary="Consultar el historial de una cita", description="Historial paginado de una cita autorizada. Los eventos de acceso al correo y sus motivos solo están disponibles para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de eventos autorizados", content = @Content(schema = @Schema(implementation = AppointmentEventPageResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cita inexistente o ajena", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @GetMapping(path="/{appointmentId}/events",produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('"+UserPermission.Constants.APPOINTMENT_READ_SELF+"','"+UserPermission.Constants.APPOINTMENT_READ_ASSIGNED+"','"+UserPermission.Constants.APPOINTMENT_READ_ALL+"')")
    public ResponseEntity<AppointmentEventPageResponseDTO> events(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.events(UUID.fromString(jwt.getSubject()),appointmentId,page,size));
    }

    @Operation(operationId="reassignAppointmentVeterinarian",summary="Resolver la falta de veterinario de una cita", description="Solo administradores: cita vigente con NEEDS_REASSIGNMENT a un turno libre de otro veterinario activo. Conserva identidad y estado funcional. Cambiar fecha u hora requiere constancia de acuerdo previo con el propietario. Una confirmada puede resolverse a un turno futuro del mismo día; una pendiente no puede eludir su corte. Operación idempotente mediante clientRequestId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignación completada o repetición equivalente", content = @Content(schema = @Schema(implementation = AppointmentResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cita o turno inexistente", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Versión, estado, vigencia o capacidad incompatibles", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PostMapping(path="/{appointmentId}/veterinarian-reassignments",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('"+UserPermission.Constants.APPOINTMENT_MANAGE+"')")
    public ResponseEntity<AppointmentResponseDTO> reassign(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId,
            @Valid @RequestBody ReassignAppointmentRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.reassign(UUID.fromString(jwt.getSubject()),appointmentId,request));
    }

    @Operation(operationId="revealAppointmentOwnerEmail",summary="Consultar el correo del propietario con motivo", description="Solo el veterinario actualmente asignado a una cita CONFIRMED cuyo intervalo no terminó. Cada consulta exige motivo de contacto por WhatsApp fallido y se audita antes de responder. No concede acceso permanente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correo revelado y acceso auditado; respuesta privada sin caché", content = @Content(schema = @Schema(implementation = OwnerEmailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cita inexistente, no asignada al veterinario, no confirmada o terminada", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponseDTO.class)))
    })
    @PostMapping(path="/{appointmentId}/owner-email-accesses",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('"+UserPermission.Constants.APPOINTMENT_OWNER_EMAIL_READ+"')")
    public ResponseEntity<OwnerEmailResponseDTO> revealOwnerEmail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId,
            @Valid @RequestBody RevealOwnerEmailRequestDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.revealOwnerEmail(UUID.fromString(jwt.getSubject()),appointmentId,request));
    }
}
