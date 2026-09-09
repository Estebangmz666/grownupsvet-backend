package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.FieldValidationErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.EmailAlreadyRegisteredException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates known failures into the public problem contract. Exception messages,
 * rejected values and request URLs are deliberately never copied to a response.
 * Spring's MVC status codes and protocol headers (for example Allow) are retained.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger APPLICATION_LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_REQUEST_DETAIL = "Revisa los campos indicados y vuelve a intentarlo.";
    private static final String INTERNAL_ERROR_DETAIL = "No pudimos completar la solicitud. Inténtalo de nuevo más tarde.";

    private final ApiErrorResponseFactory apiErrorResponseFactory;

    public GlobalExceptionHandler(ApiErrorResponseFactory apiErrorResponseFactory) {
        this.apiErrorResponseFactory = apiErrorResponseFactory;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Object> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return problemResponse(apiErrorResponseFactory.create(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "Este correo ya está registrado. Puedes iniciar sesión o recuperar tu contraseña."), new HttpHeaders());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationRequired(AuthenticationException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return problemResponse(apiErrorResponseFactory.create(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Inicia sesión para acceder a este recurso."), headers);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception) {
        return problemResponse(apiErrorResponseFactory.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tienes permiso para realizar esta acción."), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception) {
        ApiErrorResponseDTO problem = apiErrorResponseFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_DETAIL);
        logUnexpectedFailure(exception, problem);
        return problemResponse(problem, new HttpHeaders());
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // The superclass logs exception.getMessage(); keep protocol handling without that diagnostic.
        return handleExceptionInternal(exception, null, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest
                && servletWebRequest.getResponse() != null && servletWebRequest.getResponse().isCommitted()) {
            return null;
        }

        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ApiErrorResponseDTO problem = createMvcProblem(exception, status);
        if (status.is5xxServerError()) {
            logUnexpectedFailure(exception, problem);
        }
        return problemResponse(problem, headers);
    }

    private ApiErrorResponseDTO createMvcProblem(Exception exception, HttpStatus status) {
        // Validation of a controller return value represents a server defect, not bad user input.
        if (status.is5xxServerError()) {
            return apiErrorResponseFactory.create(status,
                    status == HttpStatus.SERVICE_UNAVAILABLE ? "SERVICE_UNAVAILABLE" : "INTERNAL_ERROR",
                    INTERNAL_ERROR_DETAIL);
        }
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return apiErrorResponseFactory.create(status, "VALIDATION_FAILED", INVALID_REQUEST_DETAIL,
                    validationErrors(validationException.getBindingResult().getFieldErrors()));
        }
        if (exception instanceof HandlerMethodValidationException validationException) {
            return apiErrorResponseFactory.create(status, "VALIDATION_FAILED", INVALID_REQUEST_DETAIL,
                    methodValidationErrors(validationException));
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return unreadableJsonProblem(exception, status);
        }
        if (exception instanceof MethodArgumentTypeMismatchException typeMismatchException) {
            return apiErrorResponseFactory.create(status, "TYPE_MISMATCH", INVALID_REQUEST_DETAIL,
                    List.of(new FieldValidationErrorResponseDTO(typeMismatchException.getName(),
                            "TYPE_MISMATCH", "El tipo de dato no es válido para este campo.")));
        }
        return switch (status.value()) {
            case 400 -> apiErrorResponseFactory.create(status, "INVALID_REQUEST",
                    "La solicitud está incompleta o contiene datos inválidos.");
            case 404 -> apiErrorResponseFactory.create(status, "RESOURCE_NOT_FOUND",
                    "No se encontró el recurso solicitado.");
            case 405 -> apiErrorResponseFactory.create(status, "METHOD_NOT_ALLOWED",
                    "Este recurso no admite el método HTTP utilizado.");
            case 406 -> apiErrorResponseFactory.create(status, "NOT_ACCEPTABLE",
                    "El recurso no está disponible en el formato solicitado.");
            case 413 -> apiErrorResponseFactory.create(status, "PAYLOAD_TOO_LARGE",
                    "El contenido enviado supera el tamaño permitido.");
            case 415 -> apiErrorResponseFactory.create(status, "UNSUPPORTED_MEDIA_TYPE",
                    "El tipo de contenido enviado no está admitido por esta operación.");
            default -> apiErrorResponseFactory.create(status, "REQUEST_REJECTED",
                    "No se pudo aceptar la solicitud.");
        };
    }

    private ApiErrorResponseDTO unreadableJsonProblem(Exception exception, HttpStatus status) {
        Throwable cause = exception;
        // Bound traversal: a cause chain may originate in third-party deserializers.
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException) {
                // Unknown names are arbitrary client input and may themselves contain private data.
                return apiErrorResponseFactory.create(status, "UNKNOWN_PROPERTY",
                        "La solicitud contiene campos que no forman parte de esta operación.");
            }
            if (cause instanceof MismatchedInputException mismatch) {
                List<FieldValidationErrorResponseDTO> fieldErrors = declaredJsonField(mismatch).stream()
                        .map(field -> new FieldValidationErrorResponseDTO(field, "TYPE_MISMATCH",
                                mismatch.getTargetType() == String.class
                                        ? "Debe enviarse como texto."
                                        : "El tipo o formato de dato no es válido para este campo."))
                        .toList();
                return apiErrorResponseFactory.create(status, "TYPE_MISMATCH", INVALID_REQUEST_DETAIL, fieldErrors);
            }
        }
        return apiErrorResponseFactory.create(status, "MALFORMED_JSON",
                "Envía un cuerpo JSON válido con los campos requeridos.");
    }

    private List<String> declaredJsonField(JacksonException exception) {
        // Only expose a declared Java field, never arbitrary map keys or Jackson's full path description.
        for (JacksonException.Reference reference : exception.getPath()) {
            Object source = reference.from();
            String name = reference.getPropertyName();
            if (source != null && name != null) {
                Class<?> sourceType = source instanceof Class<?> type ? type : source.getClass();
                if (Arrays.stream(sourceType.getDeclaredFields()).anyMatch(field -> field.getName().equals(name))) {
                    return List.of(name);
                }
            }
        }
        return List.of();
    }

    private List<FieldValidationErrorResponseDTO> validationErrors(List<FieldError> errors) {
        return errors.stream().map(error -> validationError(error.getField(), error))
                .distinct().sorted(Comparator.comparing(FieldValidationErrorResponseDTO::field)
                        .thenComparing(FieldValidationErrorResponseDTO::code)).toList();
    }

    private List<FieldValidationErrorResponseDTO> methodValidationErrors(HandlerMethodValidationException exception) {
        List<FieldValidationErrorResponseDTO> errors = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                errors.addAll(validationErrors(parameterErrors.getFieldErrors()));
            } else {
                String parameterName = result.getMethodParameter().getParameterName();
                if (parameterName != null) {
                    result.getResolvableErrors().forEach(error -> errors.add(validationError(parameterName, error)));
                }
            }
        });
        return errors.stream().distinct().sorted(Comparator.comparing(FieldValidationErrorResponseDTO::field)
                .thenComparing(FieldValidationErrorResponseDTO::code)).toList();
    }

    private FieldValidationErrorResponseDTO validationError(String field, MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        // Use fixed messages: custom constraint interpolation must never disclose the rejected value.
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> new FieldValidationErrorResponseDTO(field,
                    "REQUIRED", "Este campo es obligatorio.");
            case "Email" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_EMAIL", "Introduce un correo electrónico válido.");
            case "Size", "Length" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_LENGTH", "La longitud no cumple los límites permitidos para este campo.");
            case "Past", "PastOrPresent" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_DATE", "La fecha no puede estar en el futuro.");
            case "ValidUserPassword" -> "Elige una contraseña menos común; puedes usar una frase con varias palabras."
                    .equals(error.getDefaultMessage())
                    ? new FieldValidationErrorResponseDTO(field, "COMMON_PASSWORD",
                    "Elige una contraseña menos común; puedes usar una frase con varias palabras.")
                    : new FieldValidationErrorResponseDTO(field, "INVALID_PASSWORD_LENGTH",
                    "La contraseña debe tener entre 15 y 128 caracteres.");
            case "ValidInternationalPhoneNumber" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_PHONE_NUMBER", "Escribe un teléfono internacional válido con + y código de país, sin espacios.");
            case "ValidDateOfBirth" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_DATE_OF_BIRTH", "Escribe una fecha de nacimiento no futura y una edad de hasta 130 años.");
            case "ValidFullName" -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_FULL_NAME", "Escribe al menos un nombre y un apellido, con un máximo de 150 caracteres.");
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero" ->
                    new FieldValidationErrorResponseDTO(field, "OUT_OF_RANGE", "El valor está fuera del rango permitido.");
            default -> new FieldValidationErrorResponseDTO(field,
                    "INVALID_VALUE", "Revisa el contenido de este campo.");
        };
    }

    private ResponseEntity<Object> problemResponse(ApiErrorResponseDTO problem, HttpHeaders originalHeaders) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(originalHeaders);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, HttpStatusCode.valueOf(problem.getStatus()));
    }

    private void logUnexpectedFailure(Exception exception, ApiErrorResponseDTO problem) {
        String locations = Arrays.stream(exception.getStackTrace()).limit(12)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(" -> "));
        APPLICATION_LOGGER.error("Request failed: instance={} exceptionType={} locations={}",
                problem.getInstance(), exception.getClass().getName(), locations);
    }
}
