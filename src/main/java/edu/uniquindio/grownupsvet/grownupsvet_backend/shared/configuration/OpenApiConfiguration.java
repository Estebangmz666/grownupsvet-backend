package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT RS256 supplied in the Authorization header. Tokens expire 24 hours after login.")
public class OpenApiConfiguration {
    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info().title("GrownupsVet API").version("0.2.0")
                .description("Contrato incremental generado desde código. Implementa registro de propietarios, login con JWT RS256 y respuestas de error. Perfil, cierre de sesión, fotos y mascotas todavía no están disponibles."));
    }
}
