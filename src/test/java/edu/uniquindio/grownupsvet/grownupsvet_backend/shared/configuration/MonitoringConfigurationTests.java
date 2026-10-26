package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class MonitoringConfigurationTests {
    private static final String FIXTURE_HASH = "0123456789abcdef".repeat(4);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("monitoring"))
            .withUserConfiguration(MonitoringConfigurationValidator.class)
            .withPropertyValues("grownupsvet.monitoring.token-sha256=" + FIXTURE_HASH,
                    "management.endpoints.web.exposure.include=health,prometheus", "management.endpoints.access.default=none",
                    "management.endpoint.prometheus.access=read-only", "management.endpoint.health.access=read-only",
                    "management.endpoint.health.show-details=never", "management.endpoint.health.show-components=never",
                    "management.endpoint.health.probes.enabled=true", "management.endpoint.health.probes.add-additional-paths=false");

    @Test
    void acceptsTheConfinedMachineMonitoringConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("server.port=8081", "management.server.port=8081")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void requiresExactlyOneSha256DigestWithoutIncludingItsValueInFailures() {
        for (String hash : new String[]{"", " ", "a".repeat(63), "a".repeat(65), "g".repeat(64), "fixture-private-monitoring-value"}) {
            runner.withPropertyValues("grownupsvet.monitoring.token-sha256=" + hash).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                        "Monitoring requires MONITORING_TOKEN_SHA256 as exactly 64 hexadecimal characters.");
                assertThat(context.getStartupFailure().getMessage()).doesNotContain("fixture-private-monitoring-value");
            });
        }
    }

    @Test
    void rejectsConflictingProfilesAndSeparateOrDisabledManagementPorts() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("monitoring", "observability"))
                .run(context -> assertThat(context).hasFailed());
        for (String port : new String[]{"9091", "0", "-1", "65536"}) {
            runner.withPropertyValues("management.server.port=" + port).run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("management.server.address=127.0.0.1").run(context -> assertThat(context).hasFailed());
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test", "monitoring"))
                .withPropertyValues("server.port=0", "management.server.port=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test", "monitoring"))
                .withPropertyValues("server.port=0").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsOverridesOfEndpointExposurePathsAccessAndHealthDetails() {
        for (String property : new String[]{
                "management.endpoints.web.exposure.include=*", "management.endpoints.web.exposure.include=health,prometheus,env",
                "management.endpoints.web.exposure.exclude=prometheus", "management.endpoints.web.base-path=/metrics",
                "management.endpoints.web.path-mapping.prometheus=metrics", "management.endpoints.web.path-mapping.health=status",
                "spring.mvc.servlet.path=/monitoring",
                "management.server.base-path=/private", "server.servlet.context-path=/api", "management.endpoints.access.default=unrestricted",
                "management.endpoint.prometheus.access=none", "management.endpoint.health.access=unrestricted",
                "management.endpoint.health.show-details=always", "management.endpoint.health.show-components=always",
                "management.endpoint.health.probes.enabled=false", "management.endpoint.health.probes.add-additional-paths=true"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void realProfileImportsCommonMetricsAndReadsOnlyTheEnvironmentHash() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("monitoring"))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(MonitoringConfigurationValidator.class)
                .withPropertyValues("MONITORING_TOKEN_SHA256=" + FIXTURE_HASH)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("grownupsvet.monitoring.token-sha256")).isEqualTo(FIXTURE_HASH);
                    assertThat(context.getEnvironment().getProperty("management.metrics.tags.application")).isEqualTo("grownupsvet-backend");
                    assertThat(context.getEnvironment().getProperty("management.metrics.distribution.slo.http.server.requests")).contains("500ms");
                    assertThat(context.getEnvironment().getProperty("management.server.port")).isNull();
                });
    }

    @Test
    void rejectsHealthGroupOverridesThatWouldExposeDetailsOrChangeProbePaths() {
        for (String group : new String[]{"liveness", "readiness"}) {
            String groupPrefix = "management.endpoint.health.group." + group + ".";
            for (String property : new String[]{"show-details=always", "show-details=when-authorized",
                    "show-components=always", "show-components=when-authorized", "additional-path=server:/public-health"}) {
                runner.withPropertyValues(groupPrefix + property).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Monitoring health probe groups cannot expose details, components or additional paths.");
                });
            }
            runner.withPropertyValues(groupPrefix + "show-details=never", groupPrefix + "show-components=never")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }
}
