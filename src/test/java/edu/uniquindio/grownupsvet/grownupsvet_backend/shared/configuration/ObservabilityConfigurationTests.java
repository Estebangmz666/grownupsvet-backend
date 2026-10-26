package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("observability"))
            .withUserConfiguration(ObservabilityConfigurationValidator.class)
            .withPropertyValues("management.server.port=9091", "management.server.address=127.0.0.1",
                    "management.endpoints.web.exposure.include=health,prometheus",
                    "management.endpoint.health.show-details=never", "management.endpoint.health.show-components=never",
                    "management.endpoint.health.probes.add-additional-paths=true");

    @Test
    void acceptsOnlyTheConfinedManagementConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed());
        for (String address : new String[]{"0.0.0.0", "::", "192.0.2.10", "localhost", ""}) {
            runner.withPropertyValues("management.server.address=" + address)
                    .run(context -> assertThat(context).hasFailed());
        }
        for (String port : new String[]{"-1", "0", "8080", "65536"}) {
            runner.withPropertyValues("management.server.port=" + port)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void rejectsExposureOrProbeOverridesThatWouldChangeTheSecurityBoundary() {
        for (String property : new String[]{
                "management.endpoints.web.exposure.include=*",
                "management.endpoints.web.exposure.include=health,prometheus,env",
                "management.endpoints.web.base-path=/metrics",
                "management.server.base-path=/private",
                "server.servlet.context-path=/api",
                "management.endpoint.health.show-details=always",
                "management.endpoint.health.show-components=always",
                "management.endpoint.health.probes.add-additional-paths=false"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void managementMatcherUsesTheBoundSocketPortRatherThanForwardedHeadersOrConfiguredZero() {
        MockEnvironment environment = new MockEnvironment().withProperty("management.server.port", "0");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setLocalPort(49152);
        assertThat(ObservabilitySecurityConfiguration.isManagementPort(request, environment)).isFalse();
        environment.setProperty("local.management.port", "49152");
        assertThat(ObservabilitySecurityConfiguration.isManagementPort(request, environment)).isTrue();
        request.setLocalPort(8080);
        request.setServerPort(49152);
        request.addHeader("X-Forwarded-Port", "49152");
        assertThat(ObservabilitySecurityConfiguration.isManagementPort(request, environment)).isFalse();
    }
}
