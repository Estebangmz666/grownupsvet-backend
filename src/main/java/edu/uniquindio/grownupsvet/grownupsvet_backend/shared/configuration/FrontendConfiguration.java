package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.ArrayList;
import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FrontendConfigurationProperties.class)
public class FrontendConfiguration {
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(FrontendConfigurationProperties properties,
            @Value("${grownupsvet.api.base-url:}") String backendBaseUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>(properties.allowedOrigins());
        String normalizedBackend = FrontendConfigurationProperties.normalizeUrl(backendBaseUrl, false);
        if (!normalizedBackend.isEmpty()) {
            URI uri = URI.create(normalizedBackend);
            // Keep the API's own Swagger usable when TLS ends at the infrastructure proxy.
            allowedOrigins.add(uri.getScheme() + "://" + uri.getRawAuthority());
        }
        configuration.setAllowedOrigins(allowedOrigins.stream().distinct().toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location", "Retry-After", "Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
