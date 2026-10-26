package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Fails closed when an observability override would enlarge the management surface. */
@Component
@Profile("observability")
public class ObservabilityConfigurationValidator {
    private final Environment environment;

    public ObservabilityConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        Integer managementPort = environment.getProperty("management.server.port", Integer.class);
        int applicationPort = environment.getProperty("server.port", Integer.class, 8080);
        boolean randomTestPort = managementPort != null && managementPort == 0 && environment.matchesProfiles("test");
        if (managementPort == null || (!randomTestPort && (managementPort < 1 || managementPort > 65535))
                || (managementPort != 0 && managementPort == applicationPort)
                || !"127.0.0.1".equals(environment.getProperty("management.server.address"))) {
            throw new IllegalStateException("Observability requires a separate management port bound to 127.0.0.1.");
        }
        Set<String> exposedEndpoints = Binder.get(environment)
                .bind("management.endpoints.web.exposure.include", Bindable.setOf(String.class)).orElse(Set.of());
        if (!Set.of("health", "prometheus").equals(exposedEndpoints)
                || !"/actuator".equals(environment.getProperty("management.endpoints.web.base-path", "/actuator"))
                || !environment.getProperty("management.server.base-path", "").isEmpty()
                || !environment.getProperty("server.servlet.context-path", "").isEmpty()) {
            throw new IllegalStateException("Observability exposes only health and prometheus at the standard management paths.");
        }
        if (!"never".equals(environment.getProperty("management.endpoint.health.show-details"))
                || !"never".equals(environment.getProperty("management.endpoint.health.show-components"))
                || !environment.getProperty("management.endpoint.health.probes.add-additional-paths", Boolean.class, false)) {
            throw new IllegalStateException("Observability requires minimal health probes on the application port.");
        }
    }
}
