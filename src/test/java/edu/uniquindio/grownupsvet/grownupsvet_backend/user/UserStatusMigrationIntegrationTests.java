package edu.uniquindio.grownupsvet.grownupsvet_backend.user;

import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the actual upgrade scripts against an isolated schema, preserving the shared test schema. */
@SpringBootTest(properties = "grownupsvet.staff.bootstrap.enabled=false")
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class UserStatusMigrationIntegrationTests {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migratesExistingAccountsWithoutLosingCredentialsAndEnforcesNewAccountStateInvariants() {
        String schema = "staff_migration_" + UUID.randomUUID().toString().replace("-", "");
        String usersTable = schema + ".users";
        UUID activeOwnerId = UUID.randomUUID();
        UUID disabledVeterinarianId = UUID.randomUUID();
        try {
            migrateTo(schema, "6");
            jdbc.update("INSERT INTO " + usersTable
                            + " (id,email,password_hash,role,active,authentication_version) VALUES (?,?,?,?,?,?)",
                    activeOwnerId, "active-owner@example.test", "{argon2id}preserved-owner-hash", "OWNER", true, 3);
            jdbc.update("INSERT INTO " + usersTable
                            + " (id,email,password_hash,role,active,authentication_version) VALUES (?,?,?,?,?,?)",
                    disabledVeterinarianId, "disabled-vet@example.test", "{argon2id}preserved-vet-hash", "VETERINARIAN", false, 8);

            migrateTo(schema, "7");

            Map<String, Object> owner = jdbc.queryForMap("SELECT * FROM " + usersTable + " WHERE id=?", activeOwnerId);
            assertThat(owner).containsEntry("email", "active-owner@example.test")
                    .containsEntry("role", "OWNER").containsEntry("status", "ACTIVE")
                    .containsEntry("password_hash", "{argon2id}preserved-owner-hash")
                    .containsEntry("authentication_version", 3L).doesNotContainKey("active");
            Map<String, Object> veterinarian = jdbc.queryForMap("SELECT * FROM " + usersTable + " WHERE id=?", disabledVeterinarianId);
            assertThat(veterinarian).containsEntry("status", "DISABLED")
                    .containsEntry("password_hash", "{argon2id}preserved-vet-hash")
                    .containsEntry("authentication_version", 9L);

            UUID invitedVeterinarianId = UUID.randomUUID();
            insertAccount(usersTable, invitedVeterinarianId, "pending-vet@example.test", null,
                    "VETERINARIAN", "PENDING_ACTIVATION");
            assertThat(jdbc.queryForMap("SELECT * FROM " + usersTable + " WHERE id=?", invitedVeterinarianId))
                    .containsEntry("status", "PENDING_ACTIVATION").containsEntry("password_hash", null);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + usersTable + " SET status='ACTIVE' WHERE id=?",
                    invitedVeterinarianId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertAccount(usersTable, UUID.randomUUID(), "pending-owner@example.test",
                    null, "OWNER", "PENDING_ACTIVATION")).isInstanceOf(DataIntegrityViolationException.class);

            UUID superAdministratorId = UUID.randomUUID();
            insertAccount(usersTable, superAdministratorId, "super@example.test", "{argon2id}bootstrap-hash",
                    "SUPER_ADMIN", "ACTIVE");
            assertThatThrownBy(() -> insertAccount(usersTable, UUID.randomUUID(), "other-super@example.test",
                    "{argon2id}other-hash", "SUPER_ADMIN", "ACTIVE"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + usersTable + " SET status='DISABLED' WHERE id=?",
                    superAdministratorId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + usersTable, Integer.class)).isEqualTo(4);
        } finally {
            // The schema identifier is generated above solely from a constant prefix and UUID hex digits.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void migrateTo(String schema, String version) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    private void insertAccount(String table, UUID id, String email, String passwordHash, String role, String status) {
        jdbc.update("INSERT INTO " + table + " (id,email,password_hash,role,status) VALUES (?,?,?,?,?)",
                id, email, passwordHash, role, status);
    }
}
