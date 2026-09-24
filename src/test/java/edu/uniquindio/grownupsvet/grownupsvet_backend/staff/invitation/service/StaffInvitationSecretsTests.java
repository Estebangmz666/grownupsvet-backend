package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffInvitationSecretsTests {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final StaffInvitationSecrets secrets = new StaffInvitationSecrets(new StaffInvitationProperties(
            true, "https://portal.example.invalid/activate-account", KEY, "no-reply@example.test", 5));

    @Test
    void generatesIndependentUrlSafeTokensWithStableSha256Hashes() {
        String token = secrets.generateToken();
        assertThat(token).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(secrets.generateToken());
        assertThat(secrets.hash(token)).matches("[0-9a-f]{64}").isEqualTo(secrets.hash(token));
        assertThat(secrets.hash(token)).isNotEqualTo(secrets.hash(secrets.generateToken()));
    }

    @Test
    void authenticatesEncryptedDeliveryAgainstItsInvitationIdAndUsesIndependentNonces() {
        UUID invitationId = UUID.randomUUID();
        String token = secrets.generateToken();
        byte[] first = secrets.encrypt(invitationId, token);
        byte[] second = secrets.encrypt(invitationId, token);
        assertThat(first).hasSize(71).isNotEqualTo(second);
        assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain(token);
        assertThat(secrets.decrypt(invitationId, first)).isEqualTo(token);
        assertThatThrownBy(() -> secrets.decrypt(UUID.randomUUID(), first)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invitation delivery encryption is unavailable.");
        first[first.length - 1] ^= 1;
        assertThatThrownBy(() -> secrets.decrypt(invitationId, first)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTruncatedDeliveryPayloadsWithoutLeakingTheirBytes() {
        assertThatThrownBy(() -> secrets.decrypt(UUID.randomUUID(), new byte[10]))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invitation delivery payload is unavailable.");
    }
}
