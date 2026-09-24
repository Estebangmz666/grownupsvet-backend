package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Tokens are persisted only as a digest and a temporary, authenticated delivery ciphertext. */
@Component
public class StaffInvitationSecrets {
    private static final int NONCE_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();
    private final StaffInvitationProperties properties;

    public StaffInvitationSecrets(StaffInvitationProperties properties) { this.properties = properties; }

    public String generateToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invitation hashing is unavailable.");
        }
    }

    public byte[] encrypt(UUID invitationId, String token) {
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, invitationId, nonce, token.getBytes(StandardCharsets.US_ASCII));
        return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
    }

    public String decrypt(UUID invitationId, byte[] encrypted) {
        if (encrypted == null || encrypted.length != NONCE_LENGTH + 43 + 16) {
            throw new IllegalStateException("Invitation delivery payload is unavailable.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new String(crypt(Cipher.DECRYPT_MODE, invitationId, nonce, payload), StandardCharsets.US_ASCII);
    }

    private byte[] crypt(int mode, UUID invitationId, byte[] nonce, byte[] payload) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKey()), "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(invitationId.toString().getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(payload);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invitation delivery encryption is unavailable.");
        }
    }
}
