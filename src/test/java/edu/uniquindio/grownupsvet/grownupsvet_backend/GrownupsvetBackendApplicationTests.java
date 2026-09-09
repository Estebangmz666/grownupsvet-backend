package edu.uniquindio.grownupsvet.grownupsvet_backend;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GrownupsvetBackendApplicationTests {

	@Autowired
	private EntityManager entityManager;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void persistsAnAccountWithGeneratedIdentityAndEncodedPassword() {
		String passwordHash = PasswordEncoderFactories.createDelegatingPasswordEncoder()
				.encode("A fictitious test passphrase");
		User user = new User("  Persona@Example.COM  ", passwordHash, UserRole.OWNER);
		entityManager.persist(user);
		entityManager.flush();
		entityManager.clear();

		assertThat(user.getId()).isNotNull();
		User storedUser = entityManager.find(User.class, user.getId());
		assertThat(storedUser.getEmail()).isEqualTo("persona@example.com");
		assertThat(storedUser.getPasswordHash()).isEqualTo(passwordHash);
		assertThat(storedUser.getRole()).isEqualTo(UserRole.OWNER);
		assertThat(storedUser.isActive()).isTrue();
	}

	@Test
	@Transactional
	void rejectsDuplicateEmailsAfterNormalization() {
		String passwordHash = PasswordEncoderFactories.createDelegatingPasswordEncoder()
				.encode("A fictitious test passphrase");
		entityManager.persist(new User("Persona@Example.COM", passwordHash, UserRole.OWNER));
		entityManager.flush();

		entityManager.persist(new User("persona@example.com", passwordHash, UserRole.VETERINARIAN));

		assertThatThrownBy(entityManager::flush)
				.isInstanceOf(PersistenceException.class)
				.hasMessageContaining("uk_users_email");
	}
}
