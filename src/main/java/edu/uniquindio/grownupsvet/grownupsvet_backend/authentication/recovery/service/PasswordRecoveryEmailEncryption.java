package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailPayload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class PasswordRecoveryEmailEncryption {
    private final byte[] key;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryEmailEncryption(PasswordRecoveryProperties properties, ObjectMapper mapper) {
        this.key = properties.enabled() ? Base64.getDecoder().decode(properties.emailEncryptionKey()) : new byte[0];
        this.mapper = mapper;
    }

    public EncryptedPayload encrypt(UUID taskId, UUID userId, UUID challengeId, String taskType, PasswordRecoveryEmailPayload payload) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, taskId, userId, challengeId, taskType, nonce);
            return new EncryptedPayload(cipher.doFinal(mapper.writeValueAsBytes(payload)), nonce);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt password recovery email.");
        }
    }

    public PasswordRecoveryEmailPayload decrypt(UUID taskId, UUID userId, UUID challengeId, String taskType,
                                                byte[] encryptedPayload, byte[] nonce) {
        try {
            return mapper.readValue(cipher(Cipher.DECRYPT_MODE, taskId, userId, challengeId, taskType, nonce).doFinal(encryptedPayload),
                    PasswordRecoveryEmailPayload.class);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not authenticate password recovery email.");
        }
    }

    private Cipher cipher(int operation, UUID taskId, UUID userId, UUID challengeId, String taskType, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(operation, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("password-recovery-mail:" + taskType + ":" + taskId + ":" + userId + ":" + challengeId)
                .getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    public record EncryptedPayload(byte[] ciphertext, byte[] nonce) {
        @Override public String toString() { return "EncryptedRecoveryEmail[contents=[REDACTED]]"; }
    }
}
