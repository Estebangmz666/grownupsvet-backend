package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.bootstrap;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SuperAdministratorBootstrapTests {
    @Test
    void provisioningRequiresExplicitOptInAndNeverExposesConfiguredCredentials() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SuperAdministratorBootstrapProperties.class).enabled()).isFalse();
        });
        var properties = new SuperAdministratorBootstrapProperties(false, "private-super@example.test", "private-bootstrap-password");
        assertThat(properties.toString()).doesNotContain(properties.email(), properties.password());
        var users = mock(UserRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        var validator = mock(Validator.class);
        var jdbc = mock(JdbcTemplate.class);

        boolean created = new SuperAdministratorBootstrapService(properties, users, passwordEncoder, validator, jdbc).initialize();

        assertThat(created).isFalse();
        verifyNoInteractions(users, passwordEncoder, validator, jdbc);
    }

    @Test
    void aConcurrentEmailCollisionFailsProvisioningWithoutExposingDatabaseDetailsOrCredentialValues() {
        String privateEmail = "private-bootstrap@example.test";
        String privatePassword = "Private configured bootstrap password";
        String privateHash = "{argon2id}PRIVATE_HASH_MARKER";
        var users = mock(UserRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        var validator = mock(Validator.class);
        var jdbc = mock(JdbcTemplate.class);
        when(validator.validate(any())).thenReturn(Set.of());
        when(users.findByRole(UserRole.SUPER_ADMIN)).thenReturn(Optional.empty());
        when(users.existsByEmail(privateEmail)).thenReturn(false);
        when(passwordEncoder.encode(privatePassword)).thenReturn(privateHash);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "SQL rejected user row " + privateEmail + " " + privateHash + " " + privatePassword));
        var service = new SuperAdministratorBootstrapService(
                new SuperAdministratorBootstrapProperties(true, privateEmail, privatePassword),
                users, passwordEncoder, validator, jdbc);

        assertThatThrownBy(service::initialize).isInstanceOf(IllegalStateException.class).hasNoCause()
                .hasMessageNotContaining(privateEmail).hasMessageNotContaining(privatePassword)
                .hasMessageNotContaining(privateHash).hasMessageNotContaining("SQL");
        verify(users).saveAndFlush(any(User.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SuperAdministratorBootstrapProperties.class)
    static class PropertiesConfiguration { }
}
