package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.ReassignAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.UpdateAppointmentStatusRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.exception.AppointmentOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentCutoffScheduler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Committed fixtures and separate PostgreSQL connections; deliberately no outer test transaction. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00"})
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, AppointmentTransactionIntegrationTests.ClockConfiguration.class})
@MockitoBean(types = {AppointmentCutoffScheduler.class, AppointmentEmailDispatcher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentTransactionIntegrationTests {
    private static final String SCHEMA = "appointment_transactions_" + UUID.randomUUID().toString().replace("-", "");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final Instant INITIAL_TIME = Instant.parse("2026-10-05T15:00:00Z");

    @DynamicPropertySource
    static void isolateCommittedFixtures(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private AppointmentService appointments;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private PetRepository pets;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MutableAppointmentClock clock;

    private User administrator;
    private User secondAdministrator;
    private User owner;
    private User veterinarian;
    private User replacement;
    private Pet pet;
    private VeterinarianAvailabilitySlot slot;

    @BeforeEach
    void commitFixtures() {
        clock.set(INITIAL_TIME);
        inNewTransaction(() -> {
            administrator = account(UserRole.ADMINISTRATOR);
            secondAdministrator = account(UserRole.ADMINISTRATOR);
            owner = account(UserRole.OWNER);
            OwnerProfile profile = owners.saveAndFlush(new OwnerProfile(owner, "Propietaria de prueba",
                    LocalDate.of(1955, 5, 20), "+573001234567"));
            pet = pets.saveAndFlush(new Pet(profile, "Luna", PetSpecies.DOG, null, null, null, false, clock.instant()));
            veterinarian = veterinaryAccount();
            replacement = veterinaryAccount();
            slot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(),
                    Instant.parse("2026-10-06T15:00:00Z"), Instant.parse("2026-10-06T15:30:00Z"),
                    administrator.getId(), clock.instant()));
            return null;
        });
    }

    @AfterAll
    void dropOnlyThisTestSchema() {
        if (!SCHEMA.matches("appointment_transactions_[0-9a-f]{32}")) {
            throw new IllegalStateException("Unexpected test schema identifier.");
        }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void concurrentReplaysCommitOneAppointmentAndOneInitialEvent() throws Exception {
        CreateAppointmentRequestDTO request = request(slot);
        List<AppointmentService.AppointmentCreationResult> results = raceWithLockedAccount(owner.getId(),
                () -> appointments.create(owner.getId(), request),
                () -> appointments.create(owner.getId(), request));
        assertThat(results.stream().map(AppointmentService.AppointmentCreationResult::created)).containsExactlyInAnyOrder(true, false);
        UUID id = results.getFirst().appointment().id();
        assertThat(results.getLast().appointment().id()).isEqualTo(id);
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select count(*) from appointments where owner_id=?", Integer.class, owner.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=?", Integer.class, id)).isEqualTo(1);
            return null;
        });
    }

    @Test
    void confirmationAndRejectionCommitOnlyOneEffectiveTransition() throws Exception {
        UUID id = appointments.create(owner.getId(), request(slot)).appointment().id();
        List<String> results = raceWithLockedAccount(veterinarian.getId(),
                () -> transitionResult(administrator.getId(), id, AppointmentStatus.CONFIRMED, null),
                () -> transitionResult(secondAdministrator.getId(), id, AppointmentStatus.REJECTED, "No es posible atender."));
        assertThat(results).contains("CONCURRENT_UPDATE");
        assertThat(results.stream().filter(value -> value.equals("CONFIRMED") || value.equals("REJECTED"))).hasSize(1);
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=?", Integer.class, id)).isEqualTo(2);
            return null;
        });
    }

    @Test
    void confirmationWaitingForCutoffDoesNotHoldTheSlotNeededByItsEventForeignKeys() throws Exception {
        UUID id = appointments.create(owner.getId(), request(slot)).appointment().id();
        clock.set(Instant.parse("2026-10-06T11:00:00Z"));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> confirmation;
            try (Connection cutoff = dataSource.getConnection()) {
                cutoff.setAutoCommit(false);
                try {
                    try (var statement = cutoff.createStatement()) { statement.execute("SET LOCAL lock_timeout='3s'"); }
                    lockRow(cutoff, "appointments", id);
                    int backendId = backendId(cutoff);
                    confirmation = executor.submit(() -> transitionResult(administrator.getId(), id, AppointmentStatus.CONFIRMED, null));
                    awaitBlockedRequests(backendId, 1);
                    // Execute the cutoff's actual row update/event FK shape while confirmation waits.
                    // The previous implementation held the slot FOR UPDATE and timed out here.
                    try (var update = cutoff.prepareStatement("update appointments set status='CANCELLED',version=1,updated_at=? where id=?")) {
                        update.setTimestamp(1, Timestamp.from(clock.instant())); update.setObject(2, id); update.executeUpdate();
                    }
                    try (var event = cutoff.prepareStatement("insert into appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status,previous_assignment_status,new_assignment_status,previous_slot_id,new_slot_id,actor_type,occurred_at,reason) values(?,?,1,'DAILY_CUTOFF','REQUESTED','CANCELLED','ASSIGNED','ASSIGNED',?,?,'SYSTEM',?,'DAILY_CONFIRMATION_CUTOFF')")) {
                        event.setObject(1, UUID.randomUUID()); event.setObject(2, id);
                        event.setObject(3, slot.getId()); event.setObject(4, slot.getId());
                        event.setTimestamp(5, Timestamp.from(clock.instant())); event.executeUpdate();
                    }
                    cutoff.commit();
                } finally { cutoff.rollback(); }
            }
            assertThat(confirmation.get(20, TimeUnit.SECONDS)).isEqualTo("CONCURRENT_UPDATE");
        }
        assertThat(appointments.expirePendingAppointments()).isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='DAILY_CUTOFF'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void cutoffProcessesMoreThanOnePageAfterDowntimeAndKeepsConfirmedAppointments() {
        List<UUID> pendingIds = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            VeterinarianAvailabilitySlot nextSlot = index == 0 ? slot : newSlot(veterinarian, slot.getStartsAt().plusSeconds(index * 1800L));
            pendingIds.add(appointments.create(owner.getId(), request(nextSlot)).appointment().id());
        }
        UUID confirmed = pendingIds.removeLast();
        appointments.updateStatus(administrator.getId(), confirmed, new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        clock.set(Instant.parse("2026-10-10T15:00:00Z"));
        assertThat(appointments.expirePendingAppointments()).isGreaterThanOrEqualTo(104);
        assertThat(appointments.expirePendingAppointments()).isZero();
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select count(*) from appointments where owner_id=? and status='CANCELLED'", Integer.class, owner.getId())).isEqualTo(104);
            assertThat(jdbc.queryForObject("select count(*) from appointment_events e join appointments a on a.id=e.appointment_id where a.owner_id=? and e.event_type='DAILY_CUTOFF'", Integer.class, owner.getId())).isEqualTo(104);
            assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, confirmed)).isEqualTo("CONFIRMED");
            return null;
        });
    }

    @Test
    void reassignmentRequiresUnavailabilityAndActiveParticipantsButReplaySurvivesLaterArchival() {
        UUID id = appointments.create(owner.getId(), request(slot)).appointment().id();
        appointments.updateStatus(administrator.getId(), id, new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        VeterinarianAvailabilitySlot destination = newSlot(replacement, slot.getStartsAt());
        ReassignAppointmentRequestDTO beforeDisabling = reassignment(destination, 1L);
        assertFailure(() -> appointments.reassign(administrator.getId(), id, beforeDisabling), "APPOINTMENT_REASSIGNMENT_NOT_REQUIRED");
        disableOriginalVeterinarian();
        ReassignAppointmentRequestDTO reassignment = reassignment(destination, 2L);
        inNewTransaction(() -> { jdbc.update("update pets set active=false where id=?", pet.getId()); return null; });
        assertFailure(() -> appointments.reassign(administrator.getId(), id, reassignment), "APPOINTMENT_PARTICIPANT_INACTIVE");
        inNewTransaction(() -> { jdbc.update("update pets set active=true where id=?", pet.getId()); return null; });
        assertThat(appointments.reassign(administrator.getId(), id, reassignment).veterinarianId()).isEqualTo(replacement.getId());
        inNewTransaction(() -> { jdbc.update("update pets set active=false where id=?", pet.getId()); return null; });
        assertThat(appointments.reassign(administrator.getId(), id, reassignment).id()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='REASSIGNED'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void anEndedConfirmedAppointmentCannotBeMovedToTheFuture() {
        UUID id = appointments.create(owner.getId(), request(slot)).appointment().id();
        appointments.updateStatus(administrator.getId(), id, new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        disableOriginalVeterinarian();
        VeterinarianAvailabilitySlot destination = newSlot(replacement, slot.getStartsAt().plusSeconds(86400));
        clock.set(slot.getEndsAt());
        assertFailure(() -> appointments.reassign(administrator.getId(), id, reassignment(destination, 2L)), "APPOINTMENT_ALREADY_ENDED");
        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?", UUID.class, id)).isEqualTo(slot.getId());
    }

    @Test
    void databaseProtectsImmutableHistoryAndMatchingSlotSnapshotsOutsideTheService() {
        UUID id = appointments.create(owner.getId(), request(slot)).appointment().id();
        assertThatThrownBy(() -> inNewTransaction(() -> jdbc.update(
                "update appointment_events set reason='Modificación inválida' where appointment_id=?", id)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inNewTransaction(() -> jdbc.update(
                "delete from appointment_assignments where appointment_id=?", id)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inNewTransaction(() -> jdbc.update(
                "update appointments set starts_at=starts_at+interval '30 minutes',ends_at=ends_at+interval '30 minutes' where id=?", id)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inNewTransaction(() -> jdbc.update(
                "update veterinarian_availability_slots set starts_at=starts_at+interval '30 minutes',ends_at=ends_at+interval '30 minutes' where id=?", slot.getId())))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inNewTransaction(() -> jdbc.update(
                "insert into appointment_assignments(id,appointment_id,slot_id,veterinarian_id,starts_at,ends_at,assigned_at,actor_id,actor_type,reason) values(?,?,?,?,?,?,?,?,'HUMAN','Invalid snapshot')",
                UUID.randomUUID(), id, slot.getId(), replacement.getId(), Timestamp.from(slot.getStartsAt()),
                Timestamp.from(slot.getEndsAt()), Timestamp.from(clock.instant()), administrator.getId())))
                .isInstanceOf(DataAccessException.class);
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=?", Integer.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_assignments where appointment_id=?", Integer.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select starts_at from appointments where id=?", Timestamp.class, id).toInstant()).isEqualTo(slot.getStartsAt());
            return null;
        });
    }

    private void disableOriginalVeterinarian() {
        inNewTransaction(() -> {
            jdbc.queryForObject("select id from users where id=? for update", UUID.class, veterinarian.getId());
            jdbc.update("update users set status='DISABLED' where id=?", veterinarian.getId());
            appointments.markVeterinarianDisabled(veterinarian.getId(), administrator.getId());
            return null;
        });
    }

    private ReassignAppointmentRequestDTO reassignment(VeterinarianAvailabilitySlot destination, long version) {
        return new ReassignAppointmentRequestDTO(UUID.randomUUID(), destination.getId(), destination.getVersion(), version,
                "Reemplazo del veterinario no disponible.", "PHONE", clock.instant(), "Se acordó el destino con la propietaria.");
    }

    private void assertFailure(Runnable operation, String errorCode) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(AppointmentOperationException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(errorCode));
    }

    private String transitionResult(UUID actor, UUID id, AppointmentStatus target, String reason) {
        try { return appointments.updateStatus(actor, id, new UpdateAppointmentStatusRequestDTO(target, 0L, reason)).status().name(); }
        catch (AppointmentOperationException conflict) { return conflict.getErrorCode(); }
    }

    private <T> List<T> raceWithLockedAccount(UUID accountId, Callable<T> firstOperation, Callable<T> secondOperation) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<T> first;
            Future<T> second;
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    lockRow(blocker, "users", accountId);
                    int backendId = backendId(blocker);
                    first = executor.submit(firstOperation);
                    second = executor.submit(secondOperation);
                    awaitBlockedRequests(backendId, 2);
                    blocker.commit();
                } finally { blocker.rollback(); }
            }
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private void lockRow(Connection connection, String table, UUID id) throws Exception {
        if (!List.of("users", "appointments").contains(table)) { throw new IllegalArgumentException("Unexpected table"); }
        try (var statement = connection.prepareStatement("select id from " + table + " where id=? for update")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
        }
    }

    private int backendId(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("select pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private void awaitBlockedRequests(int backendId, int expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                WITH RECURSIVE waiting(pid) AS (
                    SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                    UNION
                    SELECT activity.pid FROM pg_stat_activity activity
                    JOIN waiting ON waiting.pid = ANY(pg_blocking_pids(activity.pid))
                ) SELECT COUNT(DISTINCT pid) FROM waiting
                """, Integer.class, backendId)).isEqualTo(expected));
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("appointment-transaction-" + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }

    private User veterinaryAccount() {
        User account = account(UserRole.VETERINARIAN);
        veterinarians.saveAndFlush(new VeterinarianProfile(account, "Veterinaria de prueba", "+573001234567",
                "MV-" + account.getId(), "Perfil ficticio", administrator.getId(), clock.instant()));
        return account;
    }

    private VeterinarianAvailabilitySlot newSlot(User veterinarianAccount, Instant start) {
        return inNewTransaction(() -> slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarianAccount.getId(),
                start, start.plusSeconds(1800), administrator.getId(), clock.instant())));
    }

    private CreateAppointmentRequestDTO request(VeterinarianAvailabilitySlot target) {
        return new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(), target.getId(), target.getVersion(), "Consulta de prueba.");
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary MutableAppointmentClock appointmentTransactionClock() { return new MutableAppointmentClock(); }
    }

    static class MutableAppointmentClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current.get(), zone); }
        @Override public Instant instant() { return current.get(); }
    }
}
