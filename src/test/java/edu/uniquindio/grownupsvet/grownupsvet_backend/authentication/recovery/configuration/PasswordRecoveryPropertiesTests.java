package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void missingSecretPreventsEnablingRecovery() {
        runner.withPropertyValues("grownupsvet.security.password-recovery.enabled=true",
                "grownupsvet.security.password-recovery.sender-address=no-reply@example.test")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void malformedOrTooShortSecretPreventsStartup() {
        for (String secret : new String[]{"malformed:base64", "YWJj"}) {
            runner.withPropertyValues("grownupsvet.security.password-recovery.enabled=true",
                    "grownupsvet.security.password-recovery.sender-address=no-reply@example.test",
                    "grownupsvet.security.password-recovery.hmac-secret=" + secret)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void disabledRecoveryDoesNotRequireCredentialsAndNeverPrintsTheSecret() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PasswordRecoveryProperties.class).enabled()).isFalse();
        });
        PasswordRecoveryProperties properties = new PasswordRecoveryProperties(false, "private-value", "");
        assertThat(properties.toString()).doesNotContain("private-value");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordRecoveryProperties.class)
    static class PropertiesConfiguration { }
}
