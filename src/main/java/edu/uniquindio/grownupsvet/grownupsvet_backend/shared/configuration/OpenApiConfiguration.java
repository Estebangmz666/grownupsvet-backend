package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;


@Configuration(proxyBeanMethods = false)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT RS256 supplied in the Authorization header. Tokens expire 24 hours after login.")
public class OpenApiConfiguration {
    @Bean
    public OpenAPI applicationOpenApi(@Value("${grownupsvet.api.base-url:}") String baseUrl) {
        OpenAPI specification = new OpenAPI().info(new Info().title("GrownupsVet API").version("0.10.0")
                .description("Contrato generado desde Java para usuarios, mascotas, acceso, recuperación durable, personal, disponibilidad, citas y atención clínica P3. Los borradores pertenecen a citas confirmadas y solo el veterinario asignado puede modificarlos. La finalización publica la atención inmutable y completa la cita en una transacción. Incluye historia propia/asignada, fórmula y controles, avisos durables de citas y recordatorios clínicos por correo con inicio y fin; SENT acredita aceptación SMTP, no recepción en buzón. La jornada y los horarios clínicos usan America/Bogota. Adendas, urgencias e inventario quedan fuera del alcance aprobado."));
        String normalized = FrontendConfigurationProperties.normalizeUrl(baseUrl, false);
        if (!normalized.isEmpty()) { specification.addServersItem(new Server().url(normalized)); }
        return specification;
    }

    @Bean
    public OpenApiCustomizer nullableEventEnumValues() {
        return specification -> {
            if (specification.getComponents() == null || specification.getComponents().getSchemas() == null) {
                return;
            }
            for (String schemaName : java.util.List.of("VeterinarianAvailabilityEventResponseDTO", "AppointmentEventResponseDTO")) {
                Schema<?> event = specification.getComponents().getSchemas().get(schemaName);
                if (event != null && event.getProperties() != null) {
                    for (Object property : event.getProperties().values()) {
                        allowNullEnumValue((Schema<?>) property);
                    }
                }
            }
        };
    }

    private static <T> void allowNullEnumValue(Schema<T> schema) {
        if (schema != null && schema.getTypes() != null && schema.getTypes().contains("null")
                && schema.getEnum() != null && !schema.getEnum().contains(null)) {
            java.util.List<T> allowedValues = new java.util.ArrayList<>(schema.getEnum());
            allowedValues.add(null);
            schema.setEnum(allowedValues);
        }
    }
}
