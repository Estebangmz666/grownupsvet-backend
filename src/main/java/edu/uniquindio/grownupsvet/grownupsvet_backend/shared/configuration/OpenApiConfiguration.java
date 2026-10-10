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
        OpenAPI specification = new OpenAPI().info(new Info().title("GrownupsVet API").version("0.9.0")
                .description("Contrato generado desde Java para usuarios, mascotas, acceso, recuperación durable, personal, disponibilidad y gestión administrativa de citas. Incluye avisos durables de confirmación, rechazo, cancelación y cambios de horario, y recordatorio de citas confirmadas 24 horas antes. SMTP se configura para Mailtrap Sandbox; SENT acredita aceptación SMTP, no recepción en buzón. La jornada usa APPOINTMENT_WORKDAY_START_TIME en America/Bogota. Atención clínica, tratamientos y recordatorios de medicamentos quedan fuera de esta entrega."));
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
