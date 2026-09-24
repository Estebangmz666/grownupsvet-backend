package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTests {

    @Test
    void normalizesEmailOnCreationAndChangeWithoutAlteringThePasswordHash() {
        String passwordHash = "{bcrypt}$2a$12$testHashWithCaseAndSymbols";
        User user = new User("  Persona@Example.COM  ", passwordHash, UserRole.OWNER);

        assertThat(user.getEmail()).isEqualTo("persona@example.com");
        assertThat(user.getPasswordHash()).isEqualTo(passwordHash);

        user.changeEmail("  Otro@EXAMPLE.com  ");

        assertThat(user.getEmail()).isEqualTo("otro@example.com");
    }

    @Test
    void doesNotExposeThePasswordHashInJsonOrDiagnosticText() {
        String passwordHash = "{bcrypt}$2a$12$testHashWithCaseAndSymbols";
        User user = new User("persona@example.com", passwordHash, UserRole.OWNER);
        var mapper = new JacksonJsonHttpMessageConverter().getMapper();
        String json = mapper.writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash", "password_hash", passwordHash);
        assertThat(user.toString()).doesNotContain(passwordHash, "persona@example.com");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMINISTRATOR", "VETERINARIAN"})
    void invitedStaffCannotBecomeActiveWithoutSettingTheirOwnPassword(UserRole role) {
        User user = User.pendingActivation("  Invitado@Example.COM  ", role);

        assertThat(user.getEmail()).isEqualTo("invitado@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getPasswordHash()).isNull();
        assertThatThrownBy(user::activate).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThatThrownBy(() -> user.activateWithPasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPasswordHash()).isNull();

        user.activateWithPasswordHash("{argon2id}private-invitation-password-hash");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("{argon2id}private-invitation-password-hash");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OWNER", "SUPER_ADMIN"})
    void invitationFlowCannotCreateOwnersOrTheBootstrapAccount(UserRole role) {
        assertThatThrownBy(() -> User.pendingActivation("persona@example.com", role))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disablingAnAccountPermanentlyInvalidatesItsExistingSessionsEvenAfterReactivation() {
        User user = new User("persona@example.com", "{argon2id}existing-hash", UserRole.OWNER);
        long previousAuthenticationVersion = user.getAuthenticationVersion();

        user.deactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getAuthenticationVersion()).isGreaterThan(previousAuthenticationVersion);
        long disabledAuthenticationVersion = user.getAuthenticationVersion();
        user.deactivate();
        assertThat(user.getAuthenticationVersion()).isEqualTo(disabledAuthenticationVersion);

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getAuthenticationVersion()).isGreaterThan(previousAuthenticationVersion);
        assertThat(user.getPasswordHash()).isEqualTo("{argon2id}existing-hash");
    }

    @Test
    void invitationsCannotReplaceEstablishedStaffCredentials() {
        User user = new User("veterinario@example.com", "{argon2id}previous-hash", UserRole.VETERINARIAN);
        long previousAuthenticationVersion = user.getAuthenticationVersion();

        assertThatThrownBy(user::setPendingActivation).isInstanceOf(IllegalStateException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("{argon2id}previous-hash");
        assertThat(user.getAuthenticationVersion()).isEqualTo(previousAuthenticationVersion);
    }

    @Test
    void bootstrapAccountCannotBeDisabledOrMovedIntoTheInvitationFlow() {
        User user = new User("superadmin@example.com", "{argon2id}bootstrap-hash", UserRole.SUPER_ADMIN);

        assertThatThrownBy(user::deactivate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(user::setPendingActivation).isInstanceOf(IllegalStateException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("{argon2id}bootstrap-hash");
    }
}
