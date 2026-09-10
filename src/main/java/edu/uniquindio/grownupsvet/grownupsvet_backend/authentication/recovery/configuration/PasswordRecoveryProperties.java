package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Base64;

@ConfigurationProperties("grownupsvet.security.password-recovery")
public record PasswordRecoveryProperties(@DefaultValue("false") boolean enabled,
                                         @DefaultValue("") String hmacSecret,
                                         @DefaultValue("") String senderAddress) {
    public PasswordRecoveryProperties {
        if (enabled) {
            byte[] secret;
            try {
                secret = Base64.getDecoder().decode(hmacSecret);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Password recovery requires a Base64 HMAC secret.");
            }
            if (secret.length < 32) {
                throw new IllegalArgumentException("Password recovery requires at least 32 bytes of HMAC key material.");
            }
            if (senderAddress == null || senderAddress.isBlank()
                    || senderAddress.contains("\r") || senderAddress.contains("\n")) {
                throw new IllegalArgumentException("Password recovery requires a sender address.");
            }
        }
    }

    @Override
    public String toString() {
        return "PasswordRecoveryProperties[enabled=" + enabled + ", credentials=[REDACTED]]";
    }
}
