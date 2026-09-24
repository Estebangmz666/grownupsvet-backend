package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.bootstrap;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation.ValidUserPassword;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperAdministratorBootstrapService {
    private final SuperAdministratorBootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final JdbcTemplate jdbc;

    public SuperAdministratorBootstrapService(SuperAdministratorBootstrapProperties properties,
            UserRepository users, PasswordEncoder passwordEncoder, Validator validator, JdbcTemplate jdbc) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.jdbc = jdbc;
    }

    /** A database lock serializes concurrent application starts; the unique index is the final guard. */
    @Transactional
    public boolean initialize() {
        if (!properties.enabled()) {
            return false;
        }
        BootstrapCredentials credentials = new BootstrapCredentials(properties.email(), properties.password());
        if (!validator.validate(credentials).isEmpty()) {
            throw new IllegalStateException("Super administrator bootstrap requires a valid email and password configuration.");
        }
        jdbc.query("select pg_advisory_xact_lock(714936280451)", resultSet -> { });
        String email = User.normalizeEmail(properties.email());
        User existing = users.findByRole(UserRole.SUPER_ADMIN).orElse(null);
        if (existing != null) {
            if (!existing.getEmail().equals(email)) {
                throw new IllegalStateException("The super administrator has already been provisioned with another identity.");
            }
            return false;
        }
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("The bootstrap identity is already assigned to an existing account.");
        }
        try {
            users.saveAndFlush(new User(email, passwordEncoder.encode(properties.password()), UserRole.SUPER_ADMIN));
        } catch (DataIntegrityViolationException exception) {
            // A concurrent registration can reserve the same email after the precheck.
            // Startup diagnostics must not retain the database exception's rejected row.
            throw new IllegalStateException("The super administrator identity could not be provisioned because of a persistence conflict.");
        }
        return true;
    }

    private record BootstrapCredentials(@NotBlank @Email @Size(max = 254) String email,
                                        @NotBlank @ValidUserPassword String password) {
        @Override
        public String toString() { return "BootstrapCredentials[REDACTED]"; }
    }
}
