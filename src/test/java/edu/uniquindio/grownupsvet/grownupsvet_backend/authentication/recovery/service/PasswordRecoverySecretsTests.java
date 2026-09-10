package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordRecoverySecretsTests {
    private final PasswordRecoveryProperties properties = new PasswordRecoveryProperties(true,
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "no-reply@example.test");

    @Test
    void lowRandomNumbersAreEncodedAsExactlySixDigits() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(0, 42, 999999);
        PasswordRecoverySecrets secrets = new PasswordRecoverySecrets(properties, random);
        assertThat(secrets.newCode()).isEqualTo("000000");
        assertThat(secrets.newCode()).isEqualTo("000042");
        assertThat(secrets.newCode()).isEqualTo("999999");
    }

    @Test
    void codeAuthenticatorCannotBeMovedToAnotherChallenge() {
        PasswordRecoverySecrets secrets = new PasswordRecoverySecrets(properties);
        UUID challenge = UUID.randomUUID();
        byte[] digest = secrets.codeDigest(challenge, "000042");
        assertThat(digest).hasSize(32);
        assertThat(secrets.matchesCode(challenge, "000042", digest)).isTrue();
        assertThat(secrets.matchesCode(UUID.randomUUID(), "000042", digest)).isFalse();
        assertThat(secrets.matchesCode(challenge, "000043", digest)).isFalse();
        assertThat(secrets.rateLimitKey("request-email", "person@example.com"))
                .isNotEqualTo(secrets.rateLimitKey("verify-email", "person@example.com"));
    }
}
