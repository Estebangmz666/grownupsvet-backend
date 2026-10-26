package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Comprobación pública")
public class HelloController {
    public static final String HELLO_PATH = "/api/v1/hello";

    @Operation(operationId = "getHelloMessage", summary = "Comprobar acceso público al backend",
            description = "Devuelve un saludo sin autenticación para comprobar el acceso HTTP al servicio. "
                    + "No valida la conexión con la base de datos ni sustituye las sondas de salud.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Servicio HTTP accesible",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(type = "string", example = "Hola Mundo, GrownUpsVet desde google cloud")))
    @GetMapping(value = HELLO_PATH, produces = "text/plain;charset=UTF-8")
    public String getHelloMessage() {
        return "Hola Mundo, GrownUpsVet desde google cloud";
    }
}
