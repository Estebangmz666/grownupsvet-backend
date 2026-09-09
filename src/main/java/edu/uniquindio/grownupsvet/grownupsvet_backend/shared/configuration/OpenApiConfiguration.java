package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info().title("GrownupsVet API").version("0.1.0")
                .description("Contrato incremental generado desde código. Este incremento implementa registro de propietarios y respuestas de error. Login, JWT, perfil, fotos y mascotas todavía no están disponibles."));
    }
}
