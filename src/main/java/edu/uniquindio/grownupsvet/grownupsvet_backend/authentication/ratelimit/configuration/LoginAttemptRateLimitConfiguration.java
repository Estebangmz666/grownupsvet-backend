package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** PEER must remain the actual transport peer, never a client-supplied forwarding header. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginAttemptRateLimitProperties.class)
public class LoginAttemptRateLimitConfiguration {
    private final LoginAttemptRateLimitProperties properties;
    private final Environment environment;

    public LoginAttemptRateLimitConfiguration(LoginAttemptRateLimitProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validateTransportPeerConfiguration() {
        if (!properties.enabled()) { return; }
        String strategy = environment.getProperty("server.forward-headers-strategy", "none");
        if ("none".equalsIgnoreCase(strategy)) { return; }
        String internalProxies = environment.getProperty("server.tomcat.remoteip.internal-proxies", "");
        String trustedProxies = environment.getProperty("server.tomcat.remoteip.trusted-proxies", "");
        if (!"native".equalsIgnoreCase(strategy) || !"^$".equals(internalProxies)
                || !(trustedProxies.isBlank() || "^$".equals(trustedProxies))) {
            throw new IllegalStateException("Login PEER rate limiting requires ignored forwarding headers; "
                    + "use strategy none or native with internal-proxies ^$ and no trusted proxies.");
        }
    }
}
