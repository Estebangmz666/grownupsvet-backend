package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("grownupsvet.security.login-rate-limit")
public record LoginAttemptRateLimitProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String hmacSecret,
        @DefaultValue("PEER") String originMode,
        @DefaultValue("10") int identifierOriginMaximum,
        @DefaultValue("PT15M") Duration identifierOriginWindow,
        @DefaultValue("60") int originMaximum,
        @DefaultValue("PT1M") Duration originWindow) {
    public LoginAttemptRateLimitProperties {
        if (!"PEER".equals(originMode) || identifierOriginMaximum < 1 || identifierOriginMaximum > 10000
                || originMaximum < 1 || originMaximum > 100000
                || !validWindow(identifierOriginWindow) || !validWindow(originWindow)) {
            throw new IllegalArgumentException("Login rate limit policy is invalid; only transport PEER is supported.");
        }
        if (enabled) {
            byte[] key;
            try { key = Base64.getDecoder().decode(hmacSecret); }
            catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Login rate limiting requires a Base64 HMAC secret.");
            }
            if (key.length < 32) {
                throw new IllegalArgumentException("Login rate limiting requires at least 32 bytes of HMAC key material.");
            }
        }
    }

    private static boolean validWindow(Duration window) {
        return window != null && window.compareTo(Duration.ofSeconds(1)) >= 0
                && window.compareTo(Duration.ofHours(24)) <= 0;
    }

    @Override public String toString() {
        return "LoginAttemptRateLimitProperties[enabled=" + enabled + ", originMode=" + originMode
                + ", credentials=[REDACTED]]";
    }
}
