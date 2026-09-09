package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.FieldValidationErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Shared by MVC and the security filter chain to keep one public error contract. */
@Component
public class ApiErrorResponseFactory {

    public ApiErrorResponseDTO create(HttpStatus status, String errorCode, String detail) {
        return create(status, errorCode, detail, List.of());
    }

    public ApiErrorResponseDTO create(HttpStatus status, String errorCode, String detail,
                                      List<FieldValidationErrorResponseDTO> fieldErrors) {
        return new ApiErrorResponseDTO(status, title(status), detail, errorCode,
                URI.create("urn:uuid:" + UUID.randomUUID()), fieldErrors);
    }

    private String title(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "Solicitud incorrecta";
            case 401 -> "Autenticación requerida";
            case 403 -> "Acceso denegado";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato de respuesta no disponible";
            case 409 -> "Conflicto con el estado actual";
            case 413 -> "Contenido demasiado grande";
            case 415 -> "Tipo de contenido no admitido";
            case 500 -> "Error interno del servidor";
            case 503 -> "Servicio no disponible";
            default -> status.is5xxServerError() ? "Error del servidor" : "Solicitud no aceptada";
        };
    }
}
