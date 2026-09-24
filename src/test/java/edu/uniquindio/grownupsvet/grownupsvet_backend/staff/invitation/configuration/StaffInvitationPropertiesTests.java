package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffInvitationPropertiesTests {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void disabledFeatureNeedsNoCredentials() {
        assertThatCode(() -> new StaffInvitationProperties(false, "", "", "", 5)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain-text-key", "YQ=="})
    void enabledFeatureRejectsMissingOrInvalidKeys(String encryptionKey) {
        assertThatThrownBy(() -> new StaffInvitationProperties(true, "https://portal.example.test/activate",
                encryptionKey, "no-reply@example.test", 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Staff invitations require a Base64 encoded 32-byte encryption key.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://portal.example.test/activate", "https://user:secret@portal.example.test/activate",
            "https://portal.example.test/activate?source=private", "https://portal.example.test/activate#fragment", "/activate"})
    void enabledFeatureRejectsUnsafeActivationUrls(String url) {
        assertThatThrownBy(() -> new StaffInvitationProperties(true, url, KEY, "no-reply@example.test", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Staff invitations require an HTTPS activation URL without credentials, query or fragment.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://portal.example.invalid/activate-account", "http://localhost:3000/activate", "http://127.0.0.1:3000/activate"})
    void acceptsHttpsAndExplicitLocalDevelopmentUrlsWithoutExposingTheKey(String url) {
        StaffInvitationProperties properties = new StaffInvitationProperties(true, url, KEY, "no-reply@example.test", 5);
        assertThat(properties.toString()).doesNotContain(KEY);
    }
}
