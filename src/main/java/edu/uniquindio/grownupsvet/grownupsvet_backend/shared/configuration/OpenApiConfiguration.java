package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT RS256 supplied in the Authorization header. Tokens expire 24 hours after login.")
public class OpenApiConfiguration {
    @Bean
    public OpenAPI applicationOpenApi() {
        return new OpenAPI().info(new Info().title("GrownupsVet API").version("0.8.0")
                .description("Contrato generado desde código para acceso, perfiles, mascotas, recuperación, personal, disponibilidad con ocupación real y solicitudes, confirmación, agenda, historial, reasignación y cambios administrativos de citas veterinarias: cancelación manual, reprogramación con acuerdo previo e inasistencia. Los correos existentes de reasignación requieren configuración SMTP; este incremento no añade avisos generales ni recordatorios P12. La jornada de citas se configura mediante APPOINTMENT_WORKDAY_START_TIME en America/Bogota. Frontend, urgencias y atención clínica quedan fuera de este incremento."));
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
