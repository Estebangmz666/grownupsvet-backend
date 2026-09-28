package edu.uniquindio.grownupsvet.grownupsvet_backend.availability;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianQualificationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP security, independent commits and PostgreSQL lock waits; no outer test transaction. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VeterinarianAvailabilityConcurrencyIntegrationTests {
    private static final String SCHEMA = "availability_concurrency_" + UUID.randomUUID().toString().replace("-", "");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    @DynamicPropertySource
    static void isolateCommittedFixtures(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        // SET search_path accepts a not-yet-created schema; Flyway creates it before JPA starts.
        // This also isolates the native JDBC invitation queries executed when disabling a veterinarian.
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private VeterinarianProfileRepository profiles;
    @Autowired private VeterinarianQualificationRepository qualifications;
    @Autowired private OwnerProfileRepository ownerProfiles;
    @Autowired private PetRepository pets;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private JwtTokenService tokens;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private UUID veterinarianId;
    private UUID ownerId;
    private UUID firstAdministratorId;
    private UUID secondAdministratorId;
    private String firstAdministratorToken;
    private String secondAdministratorToken;
    private String ownerToken;
    private String secondOwnerToken;
    private LocalDate appointmentDate;

    @BeforeEach
    void commitFictionalAccountsBeforeStartingIndependentRequests() {
        inNewTransaction(() -> {
            User firstAdministrator = account(UserRole.ADMINISTRATOR);
            User secondAdministrator = account(UserRole.ADMINISTRATOR);
            User veterinarian = account(UserRole.VETERINARIAN);
            User owner = account(UserRole.OWNER);
            firstAdministratorId = firstAdministrator.getId();
            secondAdministratorId = secondAdministrator.getId();
            veterinarianId = veterinarian.getId();
            ownerId = owner.getId();
            ownerProfiles.saveAndFlush(new OwnerProfile(owner, "María Gómez", LocalDate.of(1955, 5, 20), "+573001234567"));
            Instant now = clock.instant();
            VeterinarianProfile profile = profiles.saveAndFlush(new VeterinarianProfile(veterinarian,
                    "Dra. Laura Marcela Ramírez", "+573001234567", "MV-" + veterinarianId,
                    "Perfil ficticio de pruebas concurrentes.", firstAdministratorId, now));
            qualifications.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                    "Medicina veterinaria", "Universidad de Ejemplo", 2020, true, now));
            firstAdministratorToken = token(firstAdministrator);
            secondAdministratorToken = token(secondAdministrator);
            ownerToken = token(owner);
            return null;
        });
        appointmentDate = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(2);
    }

    @AfterAll
    void dropOnlyTheIsolatedSchemaCreatedByThisClass() {
        if (!SCHEMA.matches("availability_concurrency_[0-9a-f]{32}")) {
            throw new IllegalStateException("Unexpected concurrency test schema identifier.");
        }
        // Immutable audit triggers prohibit deleting individual committed events. Dropping this
        // exclusively owned schema removes only this class's fictional data, never public tables.
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void concurrentAdministratorsCreatingTheSameStartCommitOneSlotAndOneEvent() throws Exception {
        List<MvcResult> results = raceWhileVeterinarianIsLocked(
                () -> createSlot(firstAdministratorToken, "09:00"),
                () -> createSlot(secondAdministratorToken, "09:00"), false);

        assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(201, 409);
        int winner = results.get(0).getResponse().getStatus() == 201 ? 0 : 1;
        assertThat(body(results.get(1 - winner)).path("errorCode").asText()).isEqualTo("AVAILABILITY_SLOT_CONFLICT");
        UUID winningAdministratorId = winner == 0 ? firstAdministratorId : secondAdministratorId;
        UUID createdSlotId = UUID.fromString(body(results.get(winner)).path("id").asText());

        inNewTransaction(() -> {
            assertThat(slotCount()).isEqualTo(1);
            assertThat(eventCount()).isEqualTo(1);
            assertThat(jdbc.queryForMap("SELECT id, version, created_by FROM veterinarian_availability_slots WHERE veterinarian_id=?",
                    veterinarianId)).containsEntry("id", createdSlotId).containsEntry("version", 0L)
                    .containsEntry("created_by", winningAdministratorId);
            assertThat(jdbc.queryForMap("SELECT slot_version, event_type, actor_id FROM veterinarian_availability_events WHERE slot_id=?",
                    createdSlotId)).containsEntry("slot_version", 0L).containsEntry("event_type", "CREATED")
                    .containsEntry("actor_id", winningAdministratorId);
            return null;
        });
    }

    @Test
    void concurrentChangesWithTheSameVersionCommitExactlyOneRescheduleAndItsAudit() throws Exception {
        MvcResult created = createSlot(firstAdministratorToken, "09:00");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID slotId = UUID.fromString(body(created).path("id").asText());
        List<MvcResult> results = raceWhileVeterinarianIsLocked(
                () -> reschedule(firstAdministratorToken, slotId, "10:00", "Primer ajuste ficticio"),
                () -> reschedule(secondAdministratorToken, slotId, "11:00", "Segundo ajuste ficticio"), false);

        assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(200, 409);
        int winner = results.get(0).getResponse().getStatus() == 200 ? 0 : 1;
        assertThat(body(results.get(1 - winner)).path("errorCode").asText()).isEqualTo("CONCURRENT_UPDATE");
        Instant winningStart = start(winner == 0 ? "10:00" : "11:00");
        UUID winningAdministratorId = winner == 0 ? firstAdministratorId : secondAdministratorId;
        String winningReason = winner == 0 ? "Primer ajuste ficticio" : "Segundo ajuste ficticio";

        inNewTransaction(() -> {
            assertThat(slotCount()).isEqualTo(1);
            assertThat(eventCount()).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT id, version, updated_by FROM veterinarian_availability_slots WHERE veterinarian_id=?",
                    veterinarianId)).containsEntry("id", slotId).containsEntry("version", 1L)
                    .containsEntry("updated_by", winningAdministratorId);
            assertThat(jdbc.<Instant>queryForObject("SELECT starts_at FROM veterinarian_availability_slots WHERE id=?",
                    (row, index) -> row.getTimestamp(1).toInstant(), slotId)).isEqualTo(winningStart);
            assertThat(jdbc.queryForMap("SELECT slot_version, event_type, actor_id, reason FROM veterinarian_availability_events WHERE slot_id=? AND slot_version=1",
                    slotId)).containsEntry("slot_version", 1L).containsEntry("event_type", "RESCHEDULED")
                    .containsEntry("actor_id", winningAdministratorId).containsEntry("reason", winningReason);
            assertThat(jdbc.<Instant>queryForObject("SELECT previous_starts_at FROM veterinarian_availability_events WHERE slot_id=? AND slot_version=1",
                    (row, index) -> row.getTimestamp(1).toInstant(), slotId)).isEqualTo(start("09:00"));
            assertThat(jdbc.<Instant>queryForObject("SELECT new_starts_at FROM veterinarian_availability_events WHERE slot_id=? AND slot_version=1",
                    (row, index) -> row.getTimestamp(1).toInstant(), slotId)).isEqualTo(winningStart);
            assertThat(jdbc.<Instant>queryForObject("SELECT previous_ends_at FROM veterinarian_availability_events WHERE slot_id=? AND slot_version=1",
                    (row, index) -> row.getTimestamp(1).toInstant(), slotId)).isEqualTo(start("09:30"));
            assertThat(jdbc.<Instant>queryForObject("SELECT new_ends_at FROM veterinarian_availability_events WHERE slot_id=? AND slot_version=1",
                    (row, index) -> row.getTimestamp(1).toInstant(), slotId)).isEqualTo(winningStart.plusSeconds(1800));
            return null;
        });
    }

    @Test
    void publicationQueuedBeforeDisablingCommitsThenBecomesInvisibleToOwners() throws Exception {
        List<MvcResult> results = raceWhileVeterinarianIsLocked(
                () -> createSlot(firstAdministratorToken, "09:00"),
                () -> disableVeterinarian(secondAdministratorToken), true);

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(201);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        assertDisabledAndNotVisibleToOwner(1);
    }

    @Test
    void disablingQueuedBeforePublicationRejectsThePublicationAfterTheAccountCommit() throws Exception {
        List<MvcResult> results = raceWhileVeterinarianIsLocked(
                () -> disableVeterinarian(firstAdministratorToken),
                () -> createSlot(secondAdministratorToken, "09:00"), true);

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(409);
        assertThat(body(results.get(1)).path("errorCode").asText()).isEqualTo("VETERINARIAN_NOT_ACTIVE");
        assertDisabledAndNotVisibleToOwner(0);
    }

    @Test
    void aLateBatchCollisionRollsBackEarlierInsertsAndAuditAcrossTheTransactionBoundary() throws Exception {
        MvcResult existing = createSlot(firstAdministratorToken, "10:00");
        assertThat(existing.getResponse().getStatus()).isEqualTo(201);
        UUID existingSlotId = UUID.fromString(body(existing).path("id").asText());
        MvcResult rejectedBatch = mvc.perform(authenticated(post("/api/v1/veterinarians/" + veterinarianId + "/availability-slot-batches"),
                firstAdministratorToken, Map.of("startDate", appointmentDate.toString(), "endDate", appointmentDate.toString(),
                        "daysOfWeek", List.of(appointmentDate.getDayOfWeek().getValue()),
                        "dailyStartTime", "09:00", "dailyEndTime", "10:30")))
                .andExpect(status().isConflict()).andReturn();
        assertThat(body(rejectedBatch).path("errorCode").asText()).isEqualTo("AVAILABILITY_SLOT_CONFLICT");

        // The 09:00 and 09:30 rows precede the conflicting 10:00 insert. A fresh transaction
        // must observe neither of them, and no audit from the failed batch may remain.
        inNewTransaction(() -> {
            assertThat(slotCount()).isEqualTo(1);
            assertThat(eventCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT id FROM veterinarian_availability_slots WHERE veterinarian_id=?",
                    UUID.class, veterinarianId)).isEqualTo(existingSlotId);
            return null;
        });
        assertThat(createSlot(secondAdministratorToken, "09:00").getResponse().getStatus()).isEqualTo(201);
        inNewTransaction(() -> {
            assertThat(slotCount()).isEqualTo(2);
            assertThat(eventCount()).isEqualTo(2);
            return null;
        });
    }

    @Test
    void concurrentOwnersRequestingTheSameSlotCommitOneAppointmentAndOneRequestEvent() throws Exception {
        MvcResult published = createSlot(firstAdministratorToken, "09:00");
        assertThat(published.getResponse().getStatus()).isEqualTo(201);
        UUID slotId = UUID.fromString(body(published).path("id").asText());
        UUID[] petIds = inNewTransaction(() -> {
            // Use the committed owner represented by the already-issued token and a second independent owner.
            User first = users.findById(ownerId).orElseThrow();
            User second = account(UserRole.OWNER);
            ownerProfiles.saveAndFlush(new OwnerProfile(second, "Carlos Pérez", LocalDate.of(1950, 3, 12), "+573001234568"));
            Pet firstPet = pets.saveAndFlush(new Pet(ownerProfiles.findById(ownerId).orElseThrow(), "Luna", PetSpecies.DOG,
                    null, null, null, false, clock.instant()));
            Pet secondPet = pets.saveAndFlush(new Pet(ownerProfiles.findById(second.getId()).orElseThrow(), "Toby", PetSpecies.CAT,
                    null, null, null, false, clock.instant()));
            secondOwnerToken = token(second);
            return new UUID[]{firstPet.getId(), secondPet.getId()};
        });
        long slotVersion = jdbc.queryForObject("SELECT version FROM veterinarian_availability_slots WHERE id=?", Long.class, slotId);
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        List<MvcResult> results = raceWhileVeterinarianIsLocked(
                () -> requestAppointment(ownerToken, firstRequest, petIds[0], slotId, slotVersion),
                () -> requestAppointment(secondOwnerToken, secondRequest, petIds[1], slotId, slotVersion), false);

        assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(201, 409);
        int winner = results.get(0).getResponse().getStatus() == 201 ? 0 : 1;
        assertThat(body(results.get(1 - winner)).path("errorCode").asText()).isEqualTo("APPOINTMENT_SLOT_OCCUPIED");
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments WHERE current_slot_id=?", Integer.class, slotId)).isEqualTo(1);
            UUID appointmentId = jdbc.queryForObject("SELECT id FROM appointments WHERE current_slot_id=?", UUID.class, slotId);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_events WHERE appointment_id=? AND event_type='REQUESTED'", Integer.class, appointmentId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_assignments WHERE appointment_id=?", Integer.class, appointmentId)).isEqualTo(1);
            return null;
        });
    }

    private MvcResult requestAppointment(String token, UUID requestId, UUID petId, UUID slotId, long slotVersion) throws Exception {
        return mvc.perform(authenticated(post("/api/v1/appointments"), token, Map.of("clientRequestId", requestId,
                "petId", petId, "availabilitySlotId", slotId, "expectedAvailabilitySlotVersion", slotVersion,
                "reason", "Consulta general para la mascota."))).andReturn();
    }

    private List<MvcResult> raceWhileVeterinarianIsLocked(Callable<MvcResult> firstRequest,
            Callable<MvcResult> secondRequest, boolean preserveArrivalOrder) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first;
            Future<MvcResult> second;
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    try (var statement = blocker.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                        statement.setObject(1, veterinarianId);
                        try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
                    }
                    int blockerId;
                    try (var statement = blocker.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                        assertThat(result.next()).isTrue();
                        blockerId = result.getInt(1);
                    }
                    first = executor.submit(firstRequest);
                    if (preserveArrivalOrder) { awaitBlockedRequests(blockerId, 1); }
                    second = executor.submit(secondRequest);
                    // PostgreSQL, rather than thread timing, proves two distinct HTTP database
                    // transactions are waiting on the veterinarian lock before we release it.
                    awaitBlockedRequests(blockerId, 2);
                    blocker.commit();
                } finally {
                    // Release the database lock before executor.close even when a barrier fails.
                    blocker.rollback();
                }
            }
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private void awaitBlockedRequests(int blockerId, int expectedCount) {
        await().alias("independent PostgreSQL requests waiting on the veterinarian account")
                .atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    Integer waiting = jdbc.queryForObject("""
                            WITH RECURSIVE waiting(pid) AS (
                                SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                                UNION
                                SELECT activity.pid FROM pg_stat_activity activity
                                JOIN waiting ON waiting.pid = ANY(pg_blocking_pids(activity.pid))
                            ) SELECT COUNT(DISTINCT pid) FROM waiting
                            """, Integer.class, blockerId);
                    assertThat(waiting).isEqualTo(expectedCount);
                });
    }

    private void assertDisabledAndNotVisibleToOwner(int expectedSlots) throws Exception {
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, veterinarianId))
                    .isEqualTo("DISABLED");
            assertThat(slotCount()).isEqualTo(expectedSlots);
            assertThat(eventCount()).isEqualTo(expectedSlots);
            if (expectedSlots > 0) {
                assertThat(jdbc.queryForObject("SELECT status FROM veterinarian_availability_slots WHERE veterinarian_id=?",
                        String.class, veterinarianId)).isEqualTo("PUBLISHED");
            }
            return null;
        });
        MvcResult options = mvc.perform(get("/api/v1/availability-slots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("from", appointmentDate.toString()).param("to", appointmentDate.toString())
                        .param("veterinarianId", veterinarianId.toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(options).path("items")).isEmpty();
        assertThat(body(options).path("totalElements").asLong()).isZero();
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("availability-concurrency-" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", role));
    }

    private String token(User user) { return tokens.issue(user, permissions.resolve(user.getRole())).accessToken(); }
    private String schedulePath() { return "/api/v1/veterinarians/" + veterinarianId + "/availability-slots"; }
    private Instant start(String time) { return appointmentDate.atTime(java.time.LocalTime.parse(time)).atZone(BUSINESS_ZONE).toInstant(); }

    private MvcResult createSlot(String actorToken, String time) throws Exception {
        return mvc.perform(authenticated(post(schedulePath()), actorToken, Map.of("startsAt", start(time).toString()))).andReturn();
    }

    private MvcResult reschedule(String actorToken, UUID slotId, String time, String reason) throws Exception {
        return mvc.perform(authenticated(put(schedulePath() + "/" + slotId), actorToken,
                Map.of("startsAt", start(time).toString(), "expectedVersion", 0, "reason", reason))).andReturn();
    }

    private MvcResult disableVeterinarian(String actorToken) throws Exception {
        return mvc.perform(authenticated(patch("/api/v1/veterinarians/" + veterinarianId + "/status"), actorToken,
                Map.of("status", "DISABLED"))).andReturn();
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, String token, Object value) throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(value));
    }

    private int slotCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM veterinarian_availability_slots WHERE veterinarian_id=?", Integer.class, veterinarianId);
    }

    private int eventCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM veterinarian_availability_events event JOIN veterinarian_availability_slots slot ON slot.id=event.slot_id WHERE slot.veterinarian_id=?",
                Integer.class, veterinarianId);
    }

    private <T> T inNewTransaction(Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> operation.get());
    }

    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
