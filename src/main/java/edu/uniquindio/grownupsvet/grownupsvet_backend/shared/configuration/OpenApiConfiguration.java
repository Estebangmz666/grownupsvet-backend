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
        return new OpenAPI().info(new Info().title("GrownupsVet API").version("0.6.0")
                .description("Contrato generado desde código para acceso, perfil, sesiones, mascotas, recuperación, personal con invitaciones y disponibilidad veterinaria. El correo requiere configuración SMTP. La URL de activación del portal contiene un placeholder configurable. Reservas, consulta automática de COMVEZCOL y plantillas Thymeleaf quedan fuera de este incremento."));
    }
}
