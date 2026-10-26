package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationValidatorTests {
    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withUserConfiguration(FrontendConfiguration.class, ProductionConfigurationValidator.class)
            .withPropertyValues("spring.datasource.url=jdbc:postgresql:///grownupsvet",
                    "spring.datasource.username=app_user", "spring.datasource.password=fixture-password",
                    "spring.mail.host=sandbox.smtp.mailtrap.io", "spring.mail.port=2525",
                    "spring.mail.username=fixture-mail-user", "spring.mail.password=fixture-mail-password",
                    "spring.mail.properties.mail.smtp.auth=true", "spring.mail.properties.mail.smtp.starttls.required=true",
                    "grownupsvet.appointments.sender-address=no-reply@vet-demo.dev");
    private final ApplicationContextRunner runner = baseRunner.withPropertyValues(
            "spring.datasource.hikari.data-source-properties.cloudSqlInstance=vet-demo-project:us-central1:vet-database");

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
    void productionRejectsUnsupportedMailProviders() {
        runner.withPropertyValues("spring.mail.host=other.smtp.dev")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Production SMTP supports only Mailtrap Sandbox or Gmail.");
                });
    }

    @Test
    void productionRejectsAnUnboundedProxyTrustPattern() {
        runner.withPropertyValues("server.tomcat.remoteip.internal-proxies=.*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void gmailCanStartWithAuthenticatedStartTlsOnBothDatabaseProviders() {
        for (ApplicationContextRunner databaseRunner : new ApplicationContextRunner[]{runner, externalPostgresRunner()}) {
            gmailRunner(databaseRunner).run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void gmailRejectsDisabledAuthenticationOrStartTls() {
        for (String property : new String[]{"spring.mail.properties.mail.smtp.auth",
                "spring.mail.properties.mail.smtp.starttls.enable", "spring.mail.properties.mail.smtp.starttls.required"}) {
            gmailRunner(runner).withPropertyValues(property + "=false")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void gmailRejectsPortsOtherThanAuthenticatedStartTlsSubmission() {
        for (int port : new int[]{0, 25, 465, 2525, 65536}) {
            gmailRunner(runner).withPropertyValues("spring.mail.port=" + port)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void gmailRequiresTheAuthenticatedAccountAsSender() {
        gmailRunner(runner).withPropertyValues("grownupsvet.appointments.sender-address=no-reply@vet-demo.dev")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Gmail SMTP requires the sender address to match the authenticated account.");
                });
    }

    @Test
    void cloudSqlConnectionNameMustBeExplicitAndWellFormed() {
        runner.withPropertyValues("spring.datasource.hikari.data-source-properties.cloudSqlInstance=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.datasource.hikari.data-source-properties.cloudSqlInstance=localhost")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void externalPostgresAcceptsAnExplicitRemoteUrlWithVerifiedTls() {
        externalPostgresRunner().run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void externalPostgresCannotRunWithoutTheProductionProfile() {
        externalPostgresRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("external-postgres"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void externalPostgresRejectsLocalHostsCredentialsAndUrlParameterOverrides() {
        for (String value : new String[]{"jdbc:postgresql:///grownupsvet", "jdbc:postgresql://localhost/grownupsvet",
                "jdbc:postgresql://127.0.0.1/grownupsvet", "jdbc:postgresql://[::1]/grownupsvet",
                "jdbc:postgresql://database.localhost/grownupsvet", "jdbc:postgresql://database/grownupsvet",
                "jdbc:postgresql://db.render-demo.dev:0/grownupsvet", "jdbc:postgresql://db.render-demo.dev:65536/grownupsvet",
                "jdbc:postgresql://app:fixture-password@db.render-demo.dev/grownupsvet",
                "jdbc:postgresql://db.render-demo.dev/grownupsvet?sslmode=require",
                "jdbc:postgresql://db.render-demo.dev/grownupsvet#fragment", "jdbc:postgresql://db.render-demo.dev/"}) {
            externalPostgresRunner().withPropertyValues("spring.datasource.url=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void externalPostgresRejectsUnverifiedTlsAndCustomHostnameVerifiers() {
        for (String value : new String[]{"disable", "prefer", "require", "verify-ca"}) {
            externalPostgresRunner().withPropertyValues("spring.datasource.hikari.data-source-properties.sslmode=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
        externalPostgresRunner().withPropertyValues(
                "spring.datasource.hikari.data-source-properties.sslfactory=org.postgresql.ssl.NonValidatingFactory")
                .run(context -> assertThat(context).hasFailed());
        externalPostgresRunner().withPropertyValues(
                "spring.datasource.hikari.data-source-properties.sslhostnameverifier=example.UnsafeHostnameVerifier")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void externalPostgresRejectsInheritedCloudSqlSettings() {
        for (String property : new String[]{"socketFactory", "cloudSqlInstance", "cloudSqlRefreshStrategy", "ipTypes"}) {
            externalPostgresRunner().withPropertyValues("spring.datasource.hikari.data-source-properties." + property + "=fixture")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void externalPostgresCannotBypassValidationThroughHikariConnectionOverrides() {
        externalPostgresRunner().withPropertyValues(
                "spring.datasource.hikari.jdbc-url=jdbc:postgresql://db.render-demo.dev/grownupsvet?sslmode=disable")
                .run(context -> assertThat(context).hasFailed());
        externalPostgresRunner().withPropertyValues(
                "spring.datasource.hikari.data-source-class-name=org.postgresql.ds.PGSimpleDataSource")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectedExternalUrlDoesNotExposeItsCredentialsInTheFailure() {
        externalPostgresRunner().withPropertyValues("spring.datasource.url=jdbc:postgresql://fixture-user:private-fixture@[broken/grownupsvet")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("External PostgreSQL requires a valid PostgreSQL JDBC URL.");
                    StringWriter stackTrace = new StringWriter();
                    context.getStartupFailure().printStackTrace(new PrintWriter(stackTrace));
                    assertThat(stackTrace.toString()).doesNotContain("private-fixture");
                });
    }

    @Test
    void productionConfigDataRetainsTheDefaultCloudSqlConnection() {
        configDataRunner("prod").withPropertyValues("CLOUD_SQL_INSTANCE_CONNECTION_NAME=vet-demo-project:us-central1:vet-database")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HikariConfig configuration = Binder.get(context.getEnvironment())
                            .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).orElseThrow(IllegalStateException::new);
                    assertThat(configuration.getDataSourceProperties())
                            .containsEntry("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
                            .containsEntry("cloudSqlInstance", "vet-demo-project:us-central1:vet-database")
                            .doesNotContainKeys("sslmode", "sslfactory");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql:///grownupsvet");
                });
    }

    @Test
    void externalConfigDataBindsVerifiedTlsWithoutCloudSqlPropertiesInEitherProfileOrder() {
        for (String[] profiles : new String[][]{{"prod", "external-postgres"}, {"external-postgres", "prod"}}) {
            configDataRunner(profiles).withPropertyValues("SPRING_DATASOURCE_URL=jdbc:postgresql://db.render-demo.dev:5432/grownupsvet")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        HikariConfig configuration = Binder.get(context.getEnvironment())
                                .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).orElseThrow(IllegalStateException::new);
                        assertThat(configuration.getDataSourceProperties())
                                .containsEntry("sslmode", "verify-full")
                                .containsEntry("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory")
                                .doesNotContainKeys("socketFactory", "cloudSqlInstance", "cloudSqlRefreshStrategy", "ipTypes");
                        assertThat(configuration.getMaximumPoolSize()).isEqualTo(5);
                        assertThat(configuration.getMinimumIdle()).isZero();
                        assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                                .isEqualTo("jdbc:postgresql://db.render-demo.dev:5432/grownupsvet");
                    });
        }
    }

    private ApplicationContextRunner gmailRunner(ApplicationContextRunner databaseRunner) {
        return databaseRunner.withPropertyValues("spring.mail.host=smtp.gmail.com", "spring.mail.port=587",
                "spring.mail.username=grownupsvet.fixture@gmail.com", "spring.mail.properties.mail.smtp.starttls.enable=true",
                "grownupsvet.appointments.sender-address=grownupsvet.fixture@gmail.com");
    }

    private ApplicationContextRunner externalPostgresRunner() {
        return baseRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "external-postgres"))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://db.render-demo.dev:5432/grownupsvet",
                        "spring.datasource.hikari.data-source-properties.sslmode=verify-full",
                        "spring.datasource.hikari.data-source-properties.sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory");
    }

    private ApplicationContextRunner configDataRunner(String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(FrontendConfiguration.class, ProductionConfigurationValidator.class)
                .withPropertyValues("DB_USERNAME=app_user", "DB_PASSWORD=fixture-password", "MAIL_HOST=sandbox.smtp.mailtrap.io",
                        "MAIL_USERNAME=fixture-mail-user", "MAIL_PASSWORD=fixture-mail-password", "MAIL_FROM=no-reply@vet-demo.dev");
    }
}
