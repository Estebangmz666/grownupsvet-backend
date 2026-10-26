package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.HexFormat;
import java.util.Set;

/** Rejects overrides that would change the single-endpoint monitoring security boundary. */
@Component
@Profile("monitoring")
public class MonitoringConfigurationValidator {
    private final Environment environment;
    private byte[] expectedTokenHash;

    public MonitoringConfigurationValidator(Environment environment) { this.environment = environment; }

    @PostConstruct
    public void validate() {
        String tokenHash = environment.getProperty("grownupsvet.monitoring.token-sha256", "");
        if (!tokenHash.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalStateException("Monitoring requires MONITORING_TOKEN_SHA256 as exactly 64 hexadecimal characters.");
        }
        if (environment.matchesProfiles("observability")) {
            throw new IllegalStateException("Monitoring and observability profiles cannot be active together.");
        }
        int applicationPort = environment.getProperty("server.port", Integer.class, 8080);
        Integer managementPort = environment.getProperty("management.server.port", Integer.class);
        if ((applicationPort < 1 && !(applicationPort == 0 && environment.matchesProfiles("test"))) || applicationPort > 65535
                || (managementPort != null && (managementPort == 0 || managementPort != applicationPort))
                || environment.containsProperty("management.server.address")) {
            throw new IllegalStateException("Monitoring requires the existing application port without a separate management server.");
        }
        Set<String> exposed = Binder.get(environment).bind("management.endpoints.web.exposure.include", Bindable.setOf(String.class)).orElse(Set.of());
        Set<String> excluded = Binder.get(environment).bind("management.endpoints.web.exposure.exclude", Bindable.setOf(String.class)).orElse(Set.of());
        if (!Set.of("health", "prometheus").equals(exposed) || !excluded.isEmpty()
                || !"/actuator".equals(environment.getProperty("management.endpoints.web.base-path", "/actuator"))
                || !"prometheus".equals(environment.getProperty("management.endpoints.web.path-mapping.prometheus", "prometheus"))
                || !"health".equals(environment.getProperty("management.endpoints.web.path-mapping.health", "health"))
                || !environment.getProperty("management.server.base-path", "").isEmpty()
                || !environment.getProperty("server.servlet.context-path", "").isEmpty()
                || !Set.of("", "/").contains(environment.getProperty("spring.mvc.servlet.path", "/"))
                || !"none".equals(environment.getProperty("management.endpoints.access.default"))
                || !"read-only".equals(environment.getProperty("management.endpoint.prometheus.access"))
                || !"read-only".equals(environment.getProperty("management.endpoint.health.access"))) {
            throw new IllegalStateException("Monitoring exposes only read-only health and prometheus at the standard management paths.");
        }
        if (!"never".equals(environment.getProperty("management.endpoint.health.show-details"))
                || !"never".equals(environment.getProperty("management.endpoint.health.show-components"))
                || !environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class, false)
                || environment.getProperty("management.endpoint.health.probes.add-additional-paths", Boolean.class, false)) {
            throw new IllegalStateException("Monitoring requires the existing minimal health probe paths.");
        }
        for (String group : Set.of("liveness", "readiness")) {
            String groupPrefix = "management.endpoint.health.group." + group + ".";
            if (!"never".equals(environment.getProperty(groupPrefix + "show-details", "never"))
                    || !"never".equals(environment.getProperty(groupPrefix + "show-components", "never"))
                    || !environment.getProperty(groupPrefix + "additional-path", "").isEmpty()) {
                throw new IllegalStateException("Monitoring health probe groups cannot expose details, components or additional paths.");
            }
        }
        expectedTokenHash = HexFormat.of().parseHex(tokenHash);
    }

    byte[] expectedTokenHash() {
        if (expectedTokenHash == null) { throw new IllegalStateException("Monitoring configuration has not been validated."); }
        return expectedTokenHash.clone();
    }
}
