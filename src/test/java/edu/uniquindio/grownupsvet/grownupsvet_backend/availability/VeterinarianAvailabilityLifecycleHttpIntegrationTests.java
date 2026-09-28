package edu.uniquindio.grownupsvet.grownupsvet_backend.availability;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each HTTP mutation owns its transaction, so follow-up reads observe committed state or a real rollback. */
@SpringBootTest(properties = {
        "grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.staff.invitations.dispatch-delay=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, VeterinarianAvailabilityLifecycleHttpIntegrationTests.AvailabilityLifecycleClockConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VeterinarianAvailabilityLifecycleHttpIntegrationTests {
    private static final Instant INITIAL_TIME = Instant.parse("2026-09-24T13:00:00Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final String SCHEMA = "availability_lifecycle_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolateDatabaseSchema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        // PostgreSQL accepts a not-yet-created schema in search_path; Flyway creates it before JPA validates.
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private VeterinarianProfileRepository veterinarianProfiles;
    @Autowired private JwtTokenService tokenService;
    @Autowired private UserPermissionResolver permissionResolver;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AvailabilityLifecycleClock clock;

    private User administrator;
    private User owner;
    private User veterinarian;

    @BeforeEach
    void createOnlyFictionalFixturesInTheIsolatedSchema() {
        clock.set(INITIAL_TIME);
        transactions.executeWithoutResult(transaction -> {
            administrator = newUser(UserRole.ADMINISTRATOR);
            owner = newUser(UserRole.OWNER);
            veterinarian = newUser(UserRole.VETERINARIAN);
            veterinarianProfiles.saveAndFlush(new VeterinarianProfile(veterinarian,
                    "Laura Marcela Ramírez", "+573001234567", "MV-" + UUID.randomUUID(),
                    "Profesional ficticio para pruebas de ciclo de disponibilidad.", administrator.getId(), clock.instant()));
        });
    }

    @AfterAll
    void removeOnlyThisClassGeneratedSchema() {
        assertThat(SCHEMA).matches("availability_lifecycle_[0-9a-f]{32}");
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void reschedulingPreservesIdentityAndCommitsOneEventWithOldAndNewValues() throws Exception {
        Instant originalStart = INITIAL_TIME.plus(Duration.ofDays(1));
        JsonNode created = createSlot(originalStart);
        String slotId = created.path("id").asText();
        Instant newStart = originalStart.plus(Duration.ofHours(1));
        clock.set(INITIAL_TIME.plusSeconds(60));

        JsonNode updated = body(mvc.perform(json(put(slotPath(slotId)), administrator,
                        Map.of("startsAt", newStart.toString(), "expectedVersion", 0, "reason", "Cambio de jornada")))
                .andExpect(status().isOk()).andReturn());

        assertThat(updated.path("id").asText()).isEqualTo(slotId);
        assertThat(updated.path("version").asLong()).isEqualTo(1);
        assertThat(updated.path("startsAt").asText()).isEqualTo(newStart.toString());
        assertThat(updated.path("endsAt").asText()).isEqualTo(newStart.plusSeconds(1800).toString());
        assertThat(updated.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(readSlot(slotId)).isEqualTo(updated);

        JsonNode events = readEvents(slotId).path("items");
        assertThat(events.size()).isEqualTo(2);
        assertThat(events.get(0).path("eventType").asText()).isEqualTo("CREATED");
        assertThat(events.get(0).path("slotVersion").asLong()).isZero();
        assertTransition(events.get(1), slotId, 1, "RESCHEDULED", originalStart, newStart,
                "PUBLISHED", "PUBLISHED", "Cambio de jornada");
        assertThat(events.get(1).path("occurredAt").asText()).isEqualTo(clock.instant().toString());

        AvailabilityLifecycleSnapshot beforeNoOp = snapshot(slotId);
        mvc.perform(json(put(slotPath(slotId)), administrator,
                        Map.of("startsAt", newStart.toString(), "expectedVersion", 1, "reason", "Misma hora")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(snapshot(slotId)).isEqualTo(beforeNoOp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "BLOCKED"})
    void occupiedDestinationRollsBackTheSourceAndPreservesBothHistories(String destinationStatus) throws Exception {
        Instant originalStart = INITIAL_TIME.plus(Duration.ofDays(1));
        Instant destinationStart = originalStart.plusSeconds(3600);
        String sourceId = createSlot(originalStart).path("id").asText();
        String destinationId = createSlot(destinationStart).path("id").asText();
        if ("BLOCKED".equals(destinationStatus)) {
            changeStatus(destinationId, "BLOCKED", 0, "Destino no disponible");
        }
        AvailabilityLifecycleSnapshot originalSource = snapshot(sourceId);
        AvailabilityLifecycleSnapshot originalDestination = snapshot(destinationId);

        mvc.perform(json(put(slotPath(sourceId)), administrator,
                        Map.of("startsAt", destinationStart.toString(), "expectedVersion", 0, "reason", "Mover al destino ocupado")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AVAILABILITY_SLOT_CONFLICT"));

        assertThat(snapshot(sourceId)).isEqualTo(originalSource);
        assertThat(snapshot(destinationId)).isEqualTo(originalDestination);
        assertThat(readSlot(sourceId).path("startsAt").asText()).isEqualTo(originalStart.toString());
        assertThat(readSlot(destinationId).path("status").asText()).isEqualTo(destinationStatus);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM veterinarian_availability_slots WHERE veterinarian_id = ?",
                Integer.class, veterinarian.getId())).isEqualTo(2);
    }

    @Test
    void anAlreadyStartedSlotCannotBeRescuedByMovingItBackIntoTheFuture() throws Exception {
        Instant start = INITIAL_TIME.plus(Duration.ofHours(2));
        String slotId = createSlot(start).path("id").asText();
        AvailabilityLifecycleSnapshot original = snapshot(slotId);
        clock.set(start);

        mvc.perform(json(put(slotPath(slotId)), administrator,
                        Map.of("startsAt", start.plusSeconds(1800).toString(), "expectedVersion", 0, "reason", "Intento después de iniciar")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AVAILABILITY_SLOT_NOT_EDITABLE"));
        mvc.perform(json(patch(slotPath(slotId) + "/status"), administrator,
                        Map.of("status", "BLOCKED", "expectedVersion", 0, "reason", "Intento después de iniciar")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AVAILABILITY_SLOT_NOT_EDITABLE"));
        assertThat(snapshot(slotId)).isEqualTo(original);
        assertThat(readSlot(slotId).path("startsAt").asText()).isEqualTo(start.toString());
    }

    @Test
    void staleVersionRejectsBothReschedulingAndStatusChangesWithoutAdditionalEvents() throws Exception {
        Instant start = INITIAL_TIME.plus(Duration.ofDays(1));
        String slotId = createSlot(start).path("id").asText();
        changeStatus(slotId, "BLOCKED", 0, "Versión actualizada");
        AvailabilityLifecycleSnapshot current = snapshot(slotId);

        mvc.perform(json(put(slotPath(slotId)), administrator,
                        Map.of("startsAt", start.plusSeconds(3600).toString(), "expectedVersion", 0, "reason", "Datos obsoletos")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        mvc.perform(json(patch(slotPath(slotId) + "/status"), administrator,
                        Map.of("status", "BLOCKED", "expectedVersion", 0, "reason", "Estado igual con versión obsoleta")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        assertThat(snapshot(slotId)).isEqualTo(current);
    }

    @Test
    void blockingAndRepublishingRestoreOwnerVisibilityAndNoOpsDoNotChangeAuditOrVersion() throws Exception {
        Instant start = INITIAL_TIME.plus(Duration.ofDays(1));
        String slotId = createSlot(start).path("id").asText();
        assertOwnerVisibility(start, slotId, true);

        JsonNode blocked = changeStatus(slotId, "BLOCKED", 0, "Ausencia temporal");
        assertThat(blocked.path("id").asText()).isEqualTo(slotId);
        assertThat(blocked.path("version").asLong()).isEqualTo(1);
        assertOwnerVisibility(start, slotId, false);
        AvailabilityLifecycleSnapshot beforeNoOp = snapshot(slotId);
        changeStatus(slotId, "BLOCKED", 1, "Sigue bloqueado");
        assertThat(snapshot(slotId)).isEqualTo(beforeNoOp);

        JsonNode published = changeStatus(slotId, "PUBLISHED", 1, "Retorno a la agenda");
        assertThat(published.path("id").asText()).isEqualTo(slotId);
        assertThat(published.path("version").asLong()).isEqualTo(2);
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertOwnerVisibility(start, slotId, true);
        beforeNoOp = snapshot(slotId);
        changeStatus(slotId, "PUBLISHED", 2, "Sigue publicado");
        assertThat(snapshot(slotId)).isEqualTo(beforeNoOp);

        JsonNode events = readEvents(slotId).path("items");
        assertThat(events.size()).isEqualTo(3);
        assertTransition(events.get(1), slotId, 1, "BLOCKED", start, start,
                "PUBLISHED", "BLOCKED", "Ausencia temporal");
        assertTransition(events.get(2), slotId, 2, "PUBLISHED", start, start,
                "BLOCKED", "PUBLISHED", "Retorno a la agenda");
    }

    @Test
    void ownersCannotRequestTodayAndCanRequestTomorrowWithLessThanTwentyFourHoursNotice() throws Exception {
        Instant todayStart = INITIAL_TIME.plus(Duration.ofHours(3));
        String todaySlotId = createSlot(todayStart).path("id").asText();
        assertOwnerVisibility(todayStart, todaySlotId, false);

        Instant tomorrowStart = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(1).atTime(8, 0)
                .atZone(BUSINESS_ZONE).toInstant();
        String tomorrowSlotId = createSlot(tomorrowStart).path("id").asText();
        clock.set(INITIAL_TIME.plus(Duration.ofHours(10)));
        assertThat(Duration.between(clock.instant(), tomorrowStart)).isLessThan(Duration.ofHours(24));
        assertOwnerVisibility(tomorrowStart, tomorrowSlotId, true);
    }

    @Test
    void ownerHorizonIncludesExactlySixtyDaysAndExcludesOneSecondBeyondIt() throws Exception {
        Instant start = INITIAL_TIME.plus(Duration.ofDays(60));
        String slotId = createSlot(start).path("id").asText();
        assertOwnerVisibility(start, slotId, true);
        clock.set(INITIAL_TIME.minusSeconds(1));
        assertOwnerVisibility(start, slotId, false);
        clock.set(INITIAL_TIME.plusSeconds(1));
        assertOwnerVisibility(start, slotId, true);
    }

    private User newUser(UserRole role) {
        return users.saveAndFlush(new User("availability-lifecycle-" + UUID.randomUUID() + "@example.test",
                "fixture-hash-only", role));
    }

    private JsonNode createSlot(Instant start) throws Exception {
        return body(mvc.perform(json(post(schedulePath()), administrator, Map.of("startsAt", start.toString())))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode changeStatus(String slotId, String newStatus, long version, String reason) throws Exception {
        return body(mvc.perform(json(patch(slotPath(slotId) + "/status"), administrator,
                        Map.of("status", newStatus, "expectedVersion", version, "reason", reason)))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode readSlot(String slotId) throws Exception {
        return body(mvc.perform(authenticated(get(slotPath(slotId)), administrator)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode readEvents(String slotId) throws Exception {
        return body(mvc.perform(authenticated(get(slotPath(slotId) + "/events"), administrator))
                .andExpect(status().isOk()).andReturn());
    }

    private void assertOwnerVisibility(Instant start, String slotId, boolean visible) throws Exception {
        LocalDate date = start.atZone(BUSINESS_ZONE).toLocalDate();
        JsonNode page = body(mvc.perform(authenticated(get("/api/v1/availability-slots")
                        .param("from", date.toString()).param("to", date.toString())
                        .param("veterinarianId", veterinarian.getId().toString()), owner))
                .andExpect(status().isOk()).andReturn());
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asText()));
        if (visible) { assertThat(ids).containsExactly(slotId); }
        else { assertThat(ids).isEmpty(); }
        assertThat(page.path("totalElements").asLong()).isEqualTo(visible ? 1 : 0);
    }

    private void assertTransition(JsonNode event, String slotId, long version, String type,
            Instant previousStart, Instant newStart, String previousStatus, String newStatus, String reason) {
        assertThat(event.path("slotId").asText()).isEqualTo(slotId);
        assertThat(event.path("slotVersion").asLong()).isEqualTo(version);
        assertThat(event.path("actorId").asText()).isEqualTo(administrator.getId().toString());
        assertThat(event.path("eventType").asText()).isEqualTo(type);
        assertThat(event.path("previousStartsAt").asText()).isEqualTo(previousStart.toString());
        assertThat(event.path("previousEndsAt").asText()).isEqualTo(previousStart.plusSeconds(1800).toString());
        assertThat(event.path("previousStatus").asText()).isEqualTo(previousStatus);
        assertThat(event.path("newStartsAt").asText()).isEqualTo(newStart.toString());
        assertThat(event.path("newEndsAt").asText()).isEqualTo(newStart.plusSeconds(1800).toString());
        assertThat(event.path("newStatus").asText()).isEqualTo(newStatus);
        assertThat(event.path("reason").asText()).isEqualTo(reason);
    }

    private AvailabilityLifecycleSnapshot snapshot(String slotId) {
        UUID id = UUID.fromString(slotId);
        return new AvailabilityLifecycleSnapshot(
                jdbc.queryForMap("SELECT * FROM veterinarian_availability_slots WHERE id = ?", id),
                jdbc.queryForList("SELECT * FROM veterinarian_availability_events WHERE slot_id = ? ORDER BY slot_version", id));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, User actor) {
        String token = tokenService.issue(actor, permissionResolver.resolve(actor.getRole())).accessToken();
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User actor, Object payload) throws Exception {
        return authenticated(request, actor).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload));
    }

    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private String schedulePath() { return "/api/v1/veterinarians/" + veterinarian.getId() + "/availability-slots"; }
    private String slotPath(String slotId) { return schedulePath() + "/" + slotId; }

    private record AvailabilityLifecycleSnapshot(Map<String, Object> slot, List<Map<String, Object>> events) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class AvailabilityLifecycleClockConfiguration {
        @Bean @Primary AvailabilityLifecycleClock availabilityLifecycleClock() { return new AvailabilityLifecycleClock(); }
    }

    static class AvailabilityLifecycleClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) { return this; }
            return new Clock() {
                @Override public ZoneId getZone() { return zone; }
                @Override public Clock withZone(ZoneId requestedZone) { return AvailabilityLifecycleClock.this.withZone(requestedZone); }
                @Override public Instant instant() { return current.get(); }
            };
        }
        @Override public Instant instant() { return current.get(); }
    }
}
