package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.util.Base64;

@ConfigurationProperties("grownupsvet.staff.invitations")
public record StaffInvitationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://portal.example.invalid/activate-account") String activationUrl,
        @DefaultValue("") String encryptionKey,
        @DefaultValue("") String senderAddress,
        @DefaultValue("5") int maxDeliveryAttempts) {
    public StaffInvitationProperties {
        if (maxDeliveryAttempts < 1 || maxDeliveryAttempts > 10) {
            throw new IllegalArgumentException("Staff invitations require between one and ten delivery attempts.");
        }
        if (enabled) {
            try {
                if (encryptionKey == null || Base64.getDecoder().decode(encryptionKey).length != 32) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Staff invitations require a Base64 encoded 32-byte encryption key.");
            }
            if (senderAddress == null || senderAddress.isBlank()
                    || senderAddress.contains("\r") || senderAddress.contains("\n")) {
                throw new IllegalArgumentException("Staff invitations require a sender address.");
            }
            try {
                if (activationUrl == null) { throw new IllegalArgumentException(); }
                URI uri = URI.create(activationUrl);
                boolean localHttp = "http".equals(uri.getScheme())
                        && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
                if ((!"https".equals(uri.getScheme()) && !localHttp) || uri.getHost() == null
                        || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Staff invitations require an HTTPS activation URL without credentials, query or fragment.");
            }
        }
    }

    @Override public String toString() {
        return "StaffInvitationProperties[enabled=" + enabled + ", credentials=[REDACTED]]";
    }
}
