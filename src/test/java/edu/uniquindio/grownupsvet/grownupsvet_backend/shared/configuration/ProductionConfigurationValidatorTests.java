package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationValidatorTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withUserConfiguration(FrontendConfiguration.class, ProductionConfigurationValidator.class)
            .withPropertyValues("spring.datasource.url=jdbc:postgresql:///grownupsvet",
                    "spring.datasource.username=app_user", "spring.datasource.password=fixture-password",
                    "spring.datasource.hikari.data-source-properties.cloudSqlInstance=vet-demo-project:us-central1:vet-database",
                    "spring.mail.host=sandbox.smtp.mailtrap.io", "spring.mail.port=2525",
                    "spring.mail.username=fixture-mail-user", "spring.mail.password=fixture-mail-password",
                    "spring.mail.properties.mail.smtp.auth=true", "spring.mail.properties.mail.smtp.starttls.required=true",
                    "grownupsvet.appointments.sender-address=no-reply@vet-demo.dev");

    @Test
    void productionCanStartBeforeFrontendUrlsArriveWhenInvitationsAreDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FrontendConfigurationProperties.class).baseUrl()).isEmpty();
            assertThat(context.getBean(FrontendConfigurationProperties.class).allowedOrigins()).isEmpty();
        });
    }

    @Test
    void enabledInvitationsRequireARealActivationUrl() {
        for (String value : new String[]{"", "https://portal.example.invalid/activate-account", "http://localhost:5173/activate-account"}) {
            runner.withPropertyValues("grownupsvet.staff.invitations.enabled=true", "grownupsvet.staff.invitations.activation-url=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("grownupsvet.staff.invitations.enabled=true",
                "grownupsvet.staff.invitations.activation-url=https://staff.vet-demo.dev/activation")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void productionRejectsLocalOriginsAndPlaceholderLinks() {
        runner.withPropertyValues("grownupsvet.frontend.allowed-origins=http://localhost:5173")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("grownupsvet.frontend.base-url=https://portal.example.invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingMailCredentialsAndDisabledTlsPreventProductionStartup() {
        runner.withPropertyValues("spring.mail.password=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Production configuration is missing or invalid: spring.mail.password");
        });
        runner.withPropertyValues("spring.mail.properties.mail.smtp.starttls.required=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionDoesNotSilentlySelectAnotherMailProviderOrAnUnboundedProxyTrustPattern() {
        runner.withPropertyValues("spring.mail.host=other.smtp.dev")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("server.tomcat.remoteip.internal-proxies=.*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void cloudSqlConnectionNameMustBeExplicitAndWellFormed() {
        runner.withPropertyValues("spring.datasource.hikari.data-source-properties.cloudSqlInstance=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.datasource.hikari.data-source-properties.cloudSqlInstance=localhost")
                .run(context -> assertThat(context).hasFailed());
    }
}
