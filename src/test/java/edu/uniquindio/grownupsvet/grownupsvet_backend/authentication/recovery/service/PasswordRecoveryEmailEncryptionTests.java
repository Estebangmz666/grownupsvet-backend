package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryEmailPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRecoveryEmailEncryptionTests {
    private final PasswordRecoveryProperties properties = new PasswordRecoveryProperties(true,
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "no-reply@example.test",
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=");

    @Test
    void ciphertextSurvivesAnotherCipherInstanceWithoutPersistingTheOtpOrRecipientAsPlaintext() {
        UUID taskId=UUID.randomUUID(), userId=UUID.randomUUID(), challengeId=UUID.randomUUID();
        var first=new PasswordRecoveryEmailEncryption(properties, JsonMapper.builder().build());
        var payload=new PasswordRecoveryEmailPayload("recipient@example.test", "María", "000042");
        var encrypted=first.encrypt(taskId, userId, challengeId, "RECOVERY_CODE", payload);
        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(new String(encrypted.ciphertext(), StandardCharsets.UTF_8)).doesNotContain("000042", "recipient@example.test");
        var restarted=new PasswordRecoveryEmailEncryption(properties, JsonMapper.builder().build());
        assertThat(restarted.decrypt(taskId, userId, challengeId, "RECOVERY_CODE", encrypted.ciphertext(), encrypted.nonce()))
                .isEqualTo(payload);
        assertThat(payload.toString()).doesNotContain("000042", "recipient@example.test");
    }

    @Test
    void alteredCiphertextOrAnyOtherTaskContextCannotAuthenticateThePayload() {
        UUID taskId=UUID.randomUUID(), userId=UUID.randomUUID(), challengeId=UUID.randomUUID();
        var cipher=new PasswordRecoveryEmailEncryption(properties, JsonMapper.builder().build());
        var encrypted=cipher.encrypt(taskId, userId, challengeId, "RECOVERY_CODE",
                new PasswordRecoveryEmailPayload("recipient@example.test", null, "000042"));
        byte[] altered=encrypted.ciphertext().clone();
        altered[0]^=1;
        assertThatThrownBy(() -> cipher.decrypt(taskId, userId, challengeId, "RECOVERY_CODE", altered, encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), userId, challengeId, "RECOVERY_CODE", encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(taskId, UUID.randomUUID(), challengeId, "RECOVERY_CODE", encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(taskId, userId, UUID.randomUUID(), "RECOVERY_CODE", encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(taskId, userId, challengeId, "RESET_NOTIFICATION", encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class);
    }
}
