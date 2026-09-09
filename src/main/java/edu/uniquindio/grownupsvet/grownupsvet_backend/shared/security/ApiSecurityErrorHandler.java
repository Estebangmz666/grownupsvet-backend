package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.ApiErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception.ApiErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Security-filter failures use the same body as MVC without redirecting to HTML. */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ApiErrorResponseFactory errorResponseFactory;
    private final JsonMapper jsonMapper;

    public ApiSecurityErrorHandler(ApiErrorResponseFactory errorResponseFactory, JsonMapper jsonMapper) {
        this.errorResponseFactory = errorResponseFactory;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, errorResponseFactory.create(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Necesitas una sesión válida para acceder a este recurso."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, errorResponseFactory.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tienes permiso para realizar esta acción."));
    }

    private void write(HttpServletResponse response, ApiErrorResponseDTO error) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(error.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(error));
    }
}
