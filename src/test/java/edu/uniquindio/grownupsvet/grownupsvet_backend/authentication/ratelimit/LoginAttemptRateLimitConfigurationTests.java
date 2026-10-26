package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration.LoginAttemptRateLimitConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration.LoginAttemptRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptRateLimitConfigurationTests {
    private static final String PREFIX = "grownupsvet.security.login-rate-limit.";
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LoginAttemptRateLimitConfiguration.class);

    @Test
    void disabledPolicyRequiresNoSecretAndRedactsItsRepresentation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LoginAttemptRateLimitProperties.class).enabled()).isFalse();
        });
        assertThat(new LoginAttemptRateLimitProperties(false, TEST_SECRET, "PEER", 10,
                Duration.ofMinutes(15), 60, Duration.ofMinutes(1)).toString()).doesNotContain(TEST_SECRET);
    }

    @Test
    void missingMalformedAndShortSecretsRejectEnabledStartup() {
        for (String secret : new String[]{"", "invalid:base64", "YWJj"}) {
            enabled(PREFIX + "hmac-secret=" + secret).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void permitsOnlyTransportPeerAndBoundedPositivePolicies() {
        for (String policy : new String[]{"origin-mode=FORWARDED", "identifier-origin-maximum=0",
                "origin-maximum=-1", "identifier-origin-window=PT0S", "origin-window=PT25H"}) {
            enabled(PREFIX + policy).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void nativeForwardingCannotRewriteThePeer() {
        enabled("server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=^$")
                .run(context -> assertThat(context).hasNotFailed());
        for (String proxy : new String[]{"", ".*", "10\\..*", "127\\.0\\.0\\.1"}) {
            enabled("server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=" + proxy)
                    .run(context -> assertThat(context).hasFailed());
        }
        enabled("server.forward-headers-strategy=native", "server.tomcat.remoteip.internal-proxies=^$",
                "server.tomcat.remoteip.trusted-proxies=10\\.0\\.0\\.1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void frameworkForwardingIsRejectedAndIgnoredHeadersArePermitted() {
        enabled("server.forward-headers-strategy=framework").run(context -> assertThat(context).hasFailed());
        enabled("server.forward-headers-strategy=none").run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner enabled(String... additionalProperties) {
        return runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "hmac-secret=" + TEST_SECRET)
                .withPropertyValues(additionalProperties);
    }
}
