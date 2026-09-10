package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class PasswordRecoverySecrets {
    private final SecureRandom random;
    private final byte[] key;

    @Autowired
    public PasswordRecoverySecrets(PasswordRecoveryProperties properties) {
        this(properties, new SecureRandom());
    }

    PasswordRecoverySecrets(PasswordRecoveryProperties properties, SecureRandom random) {
        this.random = random;
        this.key = properties.enabled() ? Base64.getDecoder().decode(properties.hmacSecret()) : new byte[0];
    }

    public String newCode() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }

    public String newResetToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public byte[] codeDigest(UUID challengeId, String code) {
        return hmac("code:" + challengeId + ":" + code);
    }

    public String rateLimitKey(String purpose, String identifier) {
        return HexFormat.of().formatHex(hmac("rate:" + purpose + ":" + identifier));
    }

    public String resetTokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required recovery digest algorithm is unavailable.");
        }
    }

    public boolean matchesCode(UUID challengeId, String code, byte[] expected) {
        return expected != null && MessageDigest.isEqual(codeDigest(challengeId, code), expected);
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required recovery authentication algorithm is unavailable.");
        }
    }
}
