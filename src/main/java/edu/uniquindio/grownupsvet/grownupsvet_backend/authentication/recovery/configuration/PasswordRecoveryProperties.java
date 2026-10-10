package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Base64;
import java.security.MessageDigest;

@ConfigurationProperties("grownupsvet.security.password-recovery")
public record PasswordRecoveryProperties(@DefaultValue("false") boolean enabled,
                                         @DefaultValue("") String hmacSecret,
                                         @DefaultValue("") String senderAddress,
                                         @DefaultValue("") String emailEncryptionKey,
                                         @DefaultValue("5") int maximumEmailAttempts,
                                         @DefaultValue("64") int emailQueueCapacity,
                                         @DefaultValue("15000") long emailDispatchDelayMillis,
                                         @DefaultValue("120") long emailLeaseSeconds,
                                         @DefaultValue("60") long emailRetryDelaySeconds) {
    public PasswordRecoveryProperties(boolean enabled, String hmacSecret, String senderAddress, String emailEncryptionKey) {
        this(enabled, hmacSecret, senderAddress, emailEncryptionKey, 5, 64, 15000, 120, 60);
    }

    @ConstructorBinding
    public PasswordRecoveryProperties {
        if (maximumEmailAttempts < 1 || maximumEmailAttempts > 10 || emailQueueCapacity < 1
                || emailDispatchDelayMillis < 1 || emailLeaseSeconds < 30 || emailRetryDelaySeconds < 1) {
            throw new IllegalArgumentException("Password recovery delivery limits are invalid.");
        }
        if (enabled) {
            byte[] secret;
            byte[] encryptionKey;
            try {
                secret = Base64.getDecoder().decode(hmacSecret);
                encryptionKey = Base64.getDecoder().decode(emailEncryptionKey);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Password recovery requires Base64 HMAC and email encryption secrets.");
            }
            if (secret.length < 32) {
                throw new IllegalArgumentException("Password recovery requires at least 32 bytes of HMAC key material.");
            }
            if (encryptionKey.length != 32 || MessageDigest.isEqual(secret, encryptionKey)) {
                throw new IllegalArgumentException("Password recovery requires a separate 32-byte email encryption key.");
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
