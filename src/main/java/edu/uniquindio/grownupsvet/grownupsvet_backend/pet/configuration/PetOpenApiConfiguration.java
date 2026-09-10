package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.configuration;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class PetOpenApiConfiguration {
    @Bean
    public OpenApiCustomizer nullablePetSexSchemas() {
        return specification -> {
            if (specification.getComponents() == null || specification.getComponents().getSchemas() == null) {
                return;
            }
            for (String name : List.of("CreatePetRequestDTO", "UpdatePetRequestDTO", "PetResponseDTO")) {
                Schema<?> schema = specification.getComponents().getSchemas().get(name);
                if (schema != null && schema.getProperties() != null) {
                    allowNullEnumValue(schema.getProperties().get("sex"));
                }
            }
        };
    }

    private static <T> void allowNullEnumValue(Schema<T> schema) {
        // Swagger's nullable enum conversion emits type=[string,null] while keeping
        // only string enum values. JSON Schema 2020-12 requires null in both.
        if (schema != null && schema.getTypes() != null && schema.getTypes().contains("null")
                && schema.getEnum() != null && !schema.getEnum().contains(null)) {
            List<T> allowedValues = new ArrayList<>(schema.getEnum());
            allowedValues.add(null);
            schema.setEnum(allowedValues);
        }
    }
}
