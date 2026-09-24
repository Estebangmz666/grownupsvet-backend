package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.bootstrap;

import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "grownupsvet.staff.bootstrap.enabled=false")
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class SuperAdministratorBootstrapIntegrationTests {
    private static final String PASSWORD = "  Frase inicial privada del sistema 🌳  ";
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Validator validator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private String email;

    @BeforeEach
    void useAnIsolatedBootstrapIdentity() {
        email = "bootstrap+" + UUID.randomUUID() + "@example.test";
        assertThat(users.findByRole(UserRole.SUPER_ADMIN)).isEmpty();
    }

    @AfterEach
    void removeOnlyThisTestsIdentity() {
        users.findByEmail(email).ifPresent(users::delete);
        users.flush();
    }

    @Test
    void concurrentStartsCreateExactlyOneSuperAdministratorWithAnEncodedPassword() throws Exception {
        var service = service(email.toUpperCase(Locale.ROOT), PASSWORD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { ready.countDown(); start.await(); return initialize(service); });
            var second = executor.submit(() -> { ready.countDown(); start.await(); return initialize(service); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new Boolean[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
        }

        User created = users.findByEmail(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getPasswordHash()).startsWith("{argon2id}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD.strip(), created.getPasswordHash())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='SUPER_ADMIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void subsequentStartsCannotOverwriteTheEstablishedPasswordOrProvisionAnotherIdentity() {
        assertThat(initialize(service(email, PASSWORD))).isTrue();
        User created = users.findByEmail(email).orElseThrow();
        String originalHash = created.getPasswordHash();
        long originalAuthenticationVersion = created.getAuthenticationVersion();

        assertThat(initialize(service(email, "Otra frase privada distinta para arranque"))).isFalse();
        assertThatThrownBy(() -> initialize(service("another-super@example.test", PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(email).hasMessageNotContaining("another-super@example.test")
                .hasMessageNotContaining(PASSWORD);

        User preserved = users.findByEmail(email).orElseThrow();
        assertThat(preserved.getId()).isEqualTo(created.getId());
        assertThat(preserved.getPasswordHash()).isEqualTo(originalHash);
        assertThat(preserved.getAuthenticationVersion()).isEqualTo(originalAuthenticationVersion);
    }

    @Test
    void bootstrapNeverElevatesAnExistingOwnerWithTheConfiguredEmail() {
        User owner = users.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), UserRole.OWNER));

        assertThatThrownBy(() -> initialize(service(email, PASSWORD)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(email).hasMessageNotContaining(PASSWORD);

        User preserved = users.findById(owner.getId()).orElseThrow();
        assertThat(preserved.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(preserved.getPasswordHash()).isEqualTo(owner.getPasswordHash());
        assertThat(users.findByRole(UserRole.SUPER_ADMIN)).isEmpty();
    }

    @Test
    void invalidConfigurationCannotCreateAnAccountOrLeakRejectedValues() {
        for (String password : new String[]{"", "short-private", "x".repeat(129)}) {
            assertThatThrownBy(() -> initialize(service(email, password)))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(email);
        }
        assertThatThrownBy(() -> initialize(service("private-invalid-email", PASSWORD)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-invalid-email")
                .hasMessageNotContaining(PASSWORD);
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(users.findByRole(UserRole.SUPER_ADMIN)).isEmpty();
    }

    private SuperAdministratorBootstrapService service(String configuredEmail, String configuredPassword) {
        return new SuperAdministratorBootstrapService(new SuperAdministratorBootstrapProperties(true,
                configuredEmail, configuredPassword), users, passwordEncoder, validator, jdbc);
    }

    private boolean initialize(SuperAdministratorBootstrapService service) {
        // The manually constructed instance receives the same transaction boundary as its proxied application bean.
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> service.initialize()));
    }
}
