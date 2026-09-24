package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AvailabilityProperties.class)
public class AvailabilityConfiguration {
    @org.springframework.context.annotation.Bean
    OpenApiCustomizer nullableAvailabilityStatusEnum() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) { return; }
            var eventSchema = openApi.getComponents().getSchemas().get("VeterinarianAvailabilityEventResponseDTO");
            if (eventSchema == null || eventSchema.getProperties() == null) { return; }
            @SuppressWarnings("unchecked")
            io.swagger.v3.oas.models.media.Schema<String> previousStatus =
                    (io.swagger.v3.oas.models.media.Schema<String>) eventSchema.getProperties().get("previousStatus");
            if (previousStatus != null) { previousStatus.setEnum(Arrays.asList("PUBLISHED", "BLOCKED", null)); }
        };
    }
}
