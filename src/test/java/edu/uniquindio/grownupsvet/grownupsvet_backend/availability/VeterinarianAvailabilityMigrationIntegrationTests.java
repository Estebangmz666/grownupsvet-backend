package edu.uniquindio.grownupsvet.grownupsvet_backend.availability;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false"})
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
@Transactional
class VeterinarianAvailabilityMigrationIntegrationTests {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private Clock clock;

    private User administrator;
    private User veterinarian;
    private UUID slotId;
    private java.time.Instant startsAt;
    private java.time.Instant now;

    @BeforeEach
    void createFictionalReferencesForDatabaseConstraints() {
        administrator = users.saveAndFlush(new User("availability-db-admin+" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", UserRole.ADMINISTRATOR));
        veterinarian = users.saveAndFlush(new User("availability-db-vet+" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", UserRole.VETERINARIAN));
        now = clock.instant();
        veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian, "Dra. Laura Marcela Ramírez",
                "+573001234567", "MV-" + veterinarian.getId().toString().substring(0, 12), null,
                administrator.getId(), now));
        startsAt = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).plusDays(10)
                .atTime(9, 0).atZone(ZoneId.of("America/Bogota")).toInstant();
        slotId = UUID.randomUUID();
        insertSlot(slotId, startsAt, startsAt.plusSeconds(1800));
    }

    @Test
    void enforcesFixedDurationGridAndUnconditionalVeterinarianStartUniqueness() {
        UUID durationId = UUID.randomUUID();
        assertSqlRejected("INSERT INTO veterinarian_availability_slots (id, veterinarian_id, starts_at, ends_at, status, created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)",
                "23514", durationId, veterinarian.getId(), startsAt.plusSeconds(1800),
                startsAt.plusSeconds(1800 + 29 * 60L), now, now, administrator.getId(), administrator.getId());

        var offGridStart = startsAt.plusSeconds(15 * 60L);
        assertSqlRejected("INSERT INTO veterinarian_availability_slots (id, veterinarian_id, starts_at, ends_at, status, created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)",
                "23514", UUID.randomUUID(), veterinarian.getId(), offGridStart, offGridStart.plusSeconds(1800),
                now, now, administrator.getId(), administrator.getId());

        assertSqlRejected("INSERT INTO veterinarian_availability_slots (id, veterinarian_id, starts_at, ends_at, status, created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, 'BLOCKED', ?, ?, ?, ?)",
                "23505", UUID.randomUUID(), veterinarian.getId(), startsAt, startsAt.plusSeconds(1800),
                now, now, administrator.getId(), administrator.getId());
    }

    @Test
    void makesAvailabilityEventsImmutableAtTheDatabaseBoundary() {
        assertSqlRejected("INSERT INTO veterinarian_availability_events (id, slot_id, slot_version, actor_id, occurred_at, event_type, previous_starts_at, previous_ends_at, previous_status, new_starts_at, new_ends_at, new_status, reason) VALUES (?, ?, 1, ?, ?, 'BLOCKED', ?, ?, 'PUBLISHED', ?, ?, 'PUBLISHED', 'invalid transition')",
                "23514", UUID.randomUUID(), slotId, administrator.getId(), Timestamp.from(now),
                Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)));
        assertSqlRejected("INSERT INTO veterinarian_availability_events (id, slot_id, slot_version, actor_id, occurred_at, event_type, previous_starts_at, previous_ends_at, previous_status, new_starts_at, new_ends_at, new_status, reason) VALUES (?, ?, 1, ?, ?, 'RESCHEDULED', ?, ?, 'PUBLISHED', ?, ?, 'PUBLISHED', 'invalid interval')",
                "23514", UUID.randomUUID(), slotId, administrator.getId(), Timestamp.from(now),
                Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                Timestamp.from(startsAt.plusSeconds(3600)), Timestamp.from(startsAt.plusSeconds(3600 + 29 * 60L)));

        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO veterinarian_availability_events (id, slot_id, slot_version, actor_id, occurred_at, event_type, new_starts_at, new_ends_at, new_status) VALUES (?, ?, 0, ?, ?, 'CREATED', ?, ?, 'PUBLISHED')",
                eventId, slotId, administrator.getId(), Timestamp.from(now), Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)));

        assertSqlRejected("UPDATE veterinarian_availability_events SET reason = 'changed' WHERE id = ?", "P0001", eventId);
        assertSqlRejected("DELETE FROM veterinarian_availability_events WHERE id = ?", "P0001", eventId);
    }

    private void insertSlot(UUID id, java.time.Instant start, java.time.Instant end) {
        jdbc.update("INSERT INTO veterinarian_availability_slots (id, veterinarian_id, starts_at, ends_at, status, created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)",
                id, veterinarian.getId(), Timestamp.from(start), Timestamp.from(end), Timestamp.from(now), Timestamp.from(now),
                administrator.getId(), administrator.getId());
    }

    private void assertSqlRejected(String sql, String expectedSqlState, Object... values) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (Object value : values) {
                    statement.setObject(index++, value instanceof java.time.Instant instant ? Timestamp.from(instant) : value);
                }
                statement.executeUpdate();
                throw new AssertionError("Expected PostgreSQL to reject the invalid availability write.");
            } catch (SQLException exception) {
                connection.rollback(savepoint);
                assertThat(exception.getSQLState()).isEqualTo(expectedSqlState);
            } finally {
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }
}
