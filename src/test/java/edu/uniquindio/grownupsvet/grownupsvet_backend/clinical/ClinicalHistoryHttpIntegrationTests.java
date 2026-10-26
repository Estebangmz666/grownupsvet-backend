package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentCutoffScheduler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.AdministratorProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.AdministratorProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianQualificationRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No outer test transaction: HTTP requests observe committed fixtures and real reader snapshots. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00", "spring.datasource.hikari.maximum-pool-size=6"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, ClinicalHistoryHttpIntegrationTests.HistoryClockConfiguration.class})
@MockitoBean(types = {AppointmentCutoffScheduler.class, AppointmentEmailDispatcher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicalHistoryHttpIntegrationTests {
    private static final String SCHEMA = "clinical_history_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ENCOUNTERS = "/api/v1/clinical-encounters";
    private static final String APPOINTMENTS = "/api/v1/appointments";
    private static final Instant INITIAL_TIME = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant FIRST_SLOT = Instant.parse("2026-10-06T14:00:00Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    @DynamicPropertySource
    static void isolateCommittedHistoryFixtures(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private MockMvc http;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private PetRepository pets;
    @Autowired private AdministratorProfileRepository administrators;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private VeterinarianQualificationRepository qualifications;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private HistoryClock clock;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private User administrator;
    private User owner;
    private User otherOwner;
    private User veterinarian;
    private User otherVeterinarian;
    private Pet pet;

    @BeforeEach
    void commitIndependentAccountsAndPet() {
        clock.set(INITIAL_TIME);
        administrator = account(UserRole.ADMINISTRATOR);
        administrators.saveAndFlush(new AdministratorProfile(administrator, "Administradora ficticia", administrator.getId(), INITIAL_TIME));
        owner = owner();
        otherOwner = owner();
        veterinarian = veterinarian();
        otherVeterinarian = veterinarian();
        pet = pets.saveAndFlush(new Pet(owners.findById(owner.getId()).orElseThrow(), "Luna", PetSpecies.DOG,
                null, null, null, false, INITIAL_TIME));
    }

    @AfterAll
    void dropOnlyThisOwnedSchema() {
        if (!SCHEMA.matches("clinical_history_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected test schema identifier."); }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void ownerReadsPublishedHistoryDetailTreatmentAndAppointmentDiscoveryWithoutDraftContent() throws Exception {
        PreparedEncounter published = prepareEncounter(veterinarian, FIRST_SLOT);
        PreparedEncounter draft = prepareEncounter(veterinarian, FIRST_SLOT.plusSeconds(1800));
        saveTreatment(published);
        clock.set(FIRST_SLOT);
        publish(published);
        MvcResult result = http.perform(authenticated(get(historyPath()), owner))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(published.encounterId().toString()))
                .andExpect(jsonPath("$.items[0].veterinarianFullName").value("Dra. Laura Ramírez"))
                .andExpect(jsonPath("$.items[0].finalizedAt").value(FIRST_SLOT.toString())).andReturn();
        assertThat(response(result).path("items").get(0).properties()).hasSize(7);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("anamnesis", "physicalExamination", "diagnosis",
                "carePlan", "prescriptions", draft.encounterId().toString(), owner.getEmail());
        saveExample("clinical-history.json", result);
        for (String path : publishedPaths(published)) {
            http.perform(authenticated(get(path), owner)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store, private"));
        }
        http.perform(authenticated(get(treatmentPath(published.encounterId())), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriptions[0].dose").value(1.25))
                .andExpect(jsonPath("$.controls[0].reason").value("Control ficticio"));
        for (String path : publishedPaths(draft)) {
            http.perform(authenticated(get(path), owner)).andExpect(status().isNotFound());
        }
    }

    @Test
    void unrelatedOwnersAndVeterinariansCannotEnumeratePublishedHistoryOrDetails() throws Exception {
        PreparedEncounter published = prepareEncounter(veterinarian, FIRST_SLOT);
        clock.set(FIRST_SLOT);
        publish(published);
        for (User actor : List.of(otherOwner, otherVeterinarian)) {
            http.perform(authenticated(get(historyPath()), actor)).andExpect(status().isNotFound());
            for (String path : publishedPaths(published)) {
                http.perform(authenticated(get(path), actor)).andExpect(status().isNotFound());
            }
        }
        http.perform(authenticated(get("/api/v1/pets/" + UUID.randomUUID() + "/clinical-history"), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedPetsKeepOwnPublishedHistoryAndAccountDeactivationRevokesTheSession() throws Exception {
        PreparedEncounter published = prepareEncounter(veterinarian, FIRST_SLOT);
        clock.set(FIRST_SLOT);
        publish(published);
        jdbc.update("update pets set active=false where id=?", pet.getId());
        http.perform(authenticated(get(historyPath()), owner)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), owner)).andExpect(status().isOk()); }
        String session = token(owner);
        jdbc.update("update users set status='DISABLED' where id=?", owner.getId());
        http.perform(get(historyPath()).header("Authorization", "Bearer " + session)).andExpect(status().isUnauthorized());
    }

    @Test
    void emptyHistoryStillRequiresOwnershipAndAdministrativeRolesHaveNoClinicalAccess() throws Exception {
        MvcResult empty = http.perform(authenticated(get(historyPath()), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andReturn();
        saveExample("clinical-history-empty.json", empty);
        http.perform(authenticated(get(historyPath()), otherOwner)).andExpect(status().isNotFound());
        http.perform(get(historyPath())).andExpect(status().isUnauthorized());
        for (User actor : List.of(administrator, account(UserRole.SUPER_ADMIN))) {
            http.perform(authenticated(get(historyPath()), actor)).andExpect(status().isForbidden());
        }
    }

    @Test
    void currentAssignedVeterinarianReadsAntecedentsButNotAnotherAppointmentsDraftAndLosesAccessOnCancellation() throws Exception {
        PreparedEncounter published = prepareEncounter(veterinarian, FIRST_SLOT);
        PreparedEncounter privateDraft = prepareEncounter(veterinarian, FIRST_SLOT.plusSeconds(1800));
        UUID currentAppointment = confirmedAppointment(otherVeterinarian, FIRST_SLOT.plusSeconds(7200));
        clock.set(FIRST_SLOT);
        publish(published);
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(published.encounterId().toString()));
        for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isOk()); }
        for (String path : publishedPaths(privateDraft)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isNotFound()); }
        http.perform(authenticated(patch(APPOINTMENTS + "/" + currentAppointment + "/status"), administrator,
                Map.of("status", "CANCELLED", "expectedVersion", 1, "reason", "Cancelación ficticia"))).andExpect(status().isOk());
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isNotFound());
        for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isNotFound()); }
    }

    @Test
    void reassignmentRevokesThePreviousVeterinariansPublishedHistoryAccess() throws Exception {
        PreparedEncounter published = prepareEncounter(veterinarian, FIRST_SLOT);
        UUID currentAppointment = confirmedAppointment(otherVeterinarian, FIRST_SLOT.plusSeconds(7200));
        VeterinarianAvailabilitySlot replacement = slot(veterinarian, FIRST_SLOT.plusSeconds(7200));
        clock.set(FIRST_SLOT);
        publish(published);
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isOk());
        setVeterinarianStatus(otherVeterinarian, "DISABLED");
        long appointmentVersion = jdbc.queryForObject("select version from appointments where id=?", Long.class, currentAppointment);
        http.perform(authenticated(post(APPOINTMENTS + "/" + currentAppointment + "/veterinarian-reassignments"), administrator,
                Map.of("clientRequestId", UUID.randomUUID(), "availabilitySlotId", replacement.getId(),
                        "expectedAvailabilitySlotVersion", replacement.getVersion(), "expectedVersion", appointmentVersion,
                        "reason", "Continuidad ficticia"))).andExpect(status().isOk());
        setVeterinarianStatus(otherVeterinarian, "ACTIVE");
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isNotFound());
        for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isNotFound()); }
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isOk());
    }

    @Test
    void historyOrdersEqualDatesDeterministicallyAndValidatesPagination() throws Exception {
        PreparedEncounter first = prepareEncounter(veterinarian, FIRST_SLOT);
        PreparedEncounter second = prepareEncounter(veterinarian, FIRST_SLOT.plusSeconds(1800));
        PreparedEncounter newer = prepareEncounter(veterinarian, FIRST_SLOT.plusSeconds(3600));
        clock.set(FIRST_SLOT.plusSeconds(3600));
        publish(first);
        publish(second);
        clock.set(FIRST_SLOT.plusSeconds(3660));
        publish(newer);
        List<String> expected = new ArrayList<>();
        expected.add(newer.encounterId().toString());
        expected.addAll(List.of(first.encounterId().toString(), second.encounterId().toString()).stream()
                .sorted(Comparator.reverseOrder()).toList());
        JsonNode firstPage = response(http.perform(authenticated(get(historyPath()).param("size", "1"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andReturn());
        JsonNode secondPage = response(http.perform(authenticated(get(historyPath()).param("page", "1").param("size", "1"), owner))
                .andExpect(status().isOk()).andReturn());
        JsonNode thirdPage = response(http.perform(authenticated(get(historyPath()).param("page", "2").param("size", "1"), owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(List.of(firstPage.path("items").get(0).path("id").asText(), secondPage.path("items").get(0).path("id").asText(),
                thirdPage.path("items").get(0).path("id").asText())).isEqualTo(expected);
        http.perform(authenticated(get(historyPath()).param("page", "3").param("size", "1"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        for (Map<String, String> invalid : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
                Map.of("page", "2147483647", "size", "100"), Map.of("page", "not-a-number"))) {
            var request = get(historyPath());
            invalid.forEach(request::param);
            http.perform(authenticated(request, owner)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void finalizingVeterinarianRetainsOnlyOwnPublishedEncountersUntilANewAssignmentTemporarilyExpandsAccess() throws Exception {
        PreparedEncounter own = prepareEncounter(veterinarian, FIRST_SLOT);
        PreparedEncounter other = prepareEncounter(otherVeterinarian, FIRST_SLOT.plusSeconds(1800));
        PreparedEncounter later = prepareEncounter(otherVeterinarian, FIRST_SLOT.plusSeconds(3600));
        UUID futureAssignment = requestedAppointment(veterinarian, FIRST_SLOT.plusSeconds(86400));
        clock.set(FIRST_SLOT.plusSeconds(1800));
        publish(own);
        publish(other);
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(own.encounterId().toString()));
        for (String path : publishedPaths(own)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isOk()); }
        for (String path : publishedPaths(other)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isNotFound()); }

        clock.set(FIRST_SLOT.plusSeconds(3600));
        publish(later);
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String path : publishedPaths(later)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isNotFound()); }
        http.perform(authenticated(get(historyPath()), owner)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));

        http.perform(authenticated(patch(APPOINTMENTS + "/" + futureAssignment + "/status"), administrator,
                Map.of("status", "CONFIRMED", "expectedVersion", 0))).andExpect(status().isOk());
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items.length()").value(3));
        for (PreparedEncounter published : List.of(other, later)) {
            for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isOk()); }
        }
        http.perform(authenticated(patch(APPOINTMENTS + "/" + futureAssignment + "/status"), administrator,
                Map.of("status", "CANCELLED", "expectedVersion", 1, "reason", "Cancelación ficticia"))).andExpect(status().isOk());
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items.length()").value(1));
        for (String path : publishedPaths(own)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isOk()); }
        for (PreparedEncounter published : List.of(other, later)) {
            for (String path : publishedPaths(published)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isNotFound()); }
        }
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for (String path : publishedPaths(own)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isNotFound()); }
    }

    @Test
    void creatingADraftDoesNotGrantRetentionWhenAnotherVeterinarianFinalizesIt() throws Exception {
        PreparedEncounter draft = prepareEncounter(veterinarian, FIRST_SLOT);
        VeterinarianAvailabilitySlot replacement = slot(otherVeterinarian, FIRST_SLOT);
        setVeterinarianStatus(veterinarian, "DISABLED");
        long appointmentVersion = jdbc.queryForObject("select version from appointments where id=?", Long.class, draft.appointmentId());
        http.perform(authenticated(post(APPOINTMENTS + "/" + draft.appointmentId() + "/veterinarian-reassignments"), administrator,
                Map.of("clientRequestId", UUID.randomUUID(), "availabilitySlotId", replacement.getId(),
                        "expectedAvailabilitySlotVersion", replacement.getVersion(), "expectedVersion", appointmentVersion,
                        "reason", "Finalización por otro profesional"))).andExpect(status().isOk());
        setVeterinarianStatus(veterinarian, "ACTIVE");
        clock.set(FIRST_SLOT);
        PreparedEncounter finalizedByOther = new PreparedEncounter(draft.encounterId(), draft.appointmentId(), otherVeterinarian);
        publish(finalizedByOther);
        http.perform(authenticated(get(historyPath()), veterinarian)).andExpect(status().isNotFound());
        for (String path : publishedPaths(draft)) { http.perform(authenticated(get(path), veterinarian)).andExpect(status().isNotFound()); }
        http.perform(authenticated(get(historyPath()), otherVeterinarian)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String path : publishedPaths(draft)) { http.perform(authenticated(get(path), otherVeterinarian)).andExpect(status().isOk()); }
        http.perform(authenticated(get(ENCOUNTERS + "/" + draft.encounterId()), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(veterinarian.getId().toString()))
                .andExpect(jsonPath("$.veterinarianId").value(otherVeterinarian.getId().toString()));
    }

    @Test
    void oneHistoryResponseKeepsCountAndRowsOnTheSameSnapshotDuringAnotherPublication() throws Exception {
        PreparedEncounter first = prepareEncounter(veterinarian, FIRST_SLOT);
        PreparedEncounter later = prepareEncounter(veterinarian, FIRST_SLOT.plusSeconds(1800));
        clock.set(FIRST_SLOT.plusSeconds(1800));
        publish(first);
        CountDownLatch counted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (invocation.getArgument(0, String.class).startsWith("select count(*) from clinical_encounters e")) {
                counted.countDown();
                if (!release.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("History snapshot coordination timed out."); }
            }
            return result;
        }).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reader = executor.submit(() -> http.perform(authenticated(get(historyPath()), owner)).andExpect(status().isOk()).andReturn());
            try {
                assertThat(counted.await(10, TimeUnit.SECONDS)).isTrue();
                publish(later);
                release.countDown();
                JsonNode snapshot = response(reader.get(20, TimeUnit.SECONDS));
                assertThat(snapshot.path("totalElements").asLong()).isEqualTo(1);
                assertThat(snapshot.path("items").size()).isEqualTo(1);
                assertThat(snapshot.path("items").get(0).path("id").asText()).isEqualTo(first.encounterId().toString());
            } finally { release.countDown(); }
        } finally { reset(namedJdbc); }
        http.perform(authenticated(get(historyPath()), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(2));
    }

    private PreparedEncounter prepareEncounter(User attendingVeterinarian, Instant startsAt) throws Exception {
        UUID appointmentId = confirmedAppointment(attendingVeterinarian, startsAt);
        UUID encounterId = UUID.fromString(response(http.perform(authenticated(post(ENCOUNTERS), attendingVeterinarian,
                Map.of("clientRequestId", UUID.randomUUID(), "appointmentId", appointmentId))).andExpect(status().isCreated()).andReturn())
                .path("id").asText());
        http.perform(authenticated(put(ENCOUNTERS + "/" + encounterId), attendingVeterinarian,
                Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", 0, "reason", "Consulta ficticia",
                        "anamnesis", "Antecedentes ficticios", "physicalExamination", "Examen ficticio",
                        "diagnosis", "Diagnóstico ficticio", "carePlan", "Indicaciones ficticias"))).andExpect(status().isOk());
        return new PreparedEncounter(encounterId, appointmentId, attendingVeterinarian);
    }

    private UUID confirmedAppointment(User assignedVeterinarian, Instant startsAt) throws Exception {
        UUID appointmentId = requestedAppointment(assignedVeterinarian, startsAt);
        http.perform(authenticated(patch(APPOINTMENTS + "/" + appointmentId + "/status"), administrator,
                Map.of("status", "CONFIRMED", "expectedVersion", 0))).andExpect(status().isOk());
        return appointmentId;
    }

    private UUID requestedAppointment(User assignedVeterinarian, Instant startsAt) throws Exception {
        VeterinarianAvailabilitySlot slot = slot(assignedVeterinarian, startsAt);
        return UUID.fromString(response(http.perform(authenticated(post(APPOINTMENTS), owner,
                new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(), slot.getId(), slot.getVersion(), "Consulta ficticia")))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
    }

    private void publish(PreparedEncounter encounter) throws Exception {
        long version = jdbc.queryForObject("select version from clinical_encounters where id=?", Long.class, encounter.encounterId());
        long appointmentVersion = jdbc.queryForObject("select version from appointments where id=?", Long.class, encounter.appointmentId());
        http.perform(authenticated(post(ENCOUNTERS + "/" + encounter.encounterId() + "/finalizations"), encounter.veterinarian(),
                Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", version, "expectedAppointmentVersion", appointmentVersion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    private void saveTreatment(PreparedEncounter encounter) throws Exception {
        http.perform(authenticated(put(treatmentPath(encounter.encounterId())), encounter.veterinarian(),
                Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", 1,
                        "prescriptions", List.of(Map.of("id", UUID.randomUUID(), "medicationName", "Medicamento ficticio",
                                "dose", new BigDecimal("1.25"), "doseUnit", "mL", "administrationRoute", "Oral", "intervalHours", 8,
                                "startsAt", "2026-10-06T09:00:00-05:00", "endsAt", "2026-10-07T09:00:00-05:00")),
                        "controls", List.of(Map.of("id", UUID.randomUUID(), "scheduledAt", "2026-10-10T09:00:00-05:00",
                                "reason", "Control ficticio"))))).andExpect(status().isOk());
    }

    private String historyPath() { return "/api/v1/pets/" + pet.getId() + "/clinical-history"; }
    private String treatmentPath(UUID encounterId) { return ENCOUNTERS + "/" + encounterId + "/treatment"; }
    private List<String> publishedPaths(PreparedEncounter encounter) {
        return List.of(ENCOUNTERS + "/" + encounter.encounterId(), treatmentPath(encounter.encounterId()),
                APPOINTMENTS + "/" + encounter.appointmentId() + "/clinical-encounter");
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("history-" + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }

    private User owner() {
        User user = account(UserRole.OWNER);
        owners.saveAndFlush(new OwnerProfile(user, "Propietaria ficticia", LocalDate.of(1955, 5, 20), "+573001234567"));
        return user;
    }

    private User veterinarian() {
        User user = account(UserRole.VETERINARIAN);
        VeterinarianProfile profile = veterinarians.saveAndFlush(new VeterinarianProfile(user, "Dra. Laura Ramírez", "+573001234567",
                "MV-" + UUID.randomUUID().toString().substring(0, 12), "Perfil ficticio", administrator.getId(), INITIAL_TIME));
        qualifications.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                "Medicina veterinaria", "Universidad ficticia", 2020, true, INITIAL_TIME));
        return user;
    }

    private VeterinarianAvailabilitySlot slot(User assignedVeterinarian, Instant startsAt) {
        return slots.saveAndFlush(new VeterinarianAvailabilitySlot(assignedVeterinarian.getId(), startsAt,
                startsAt.plusSeconds(1800), administrator.getId(), INITIAL_TIME));
    }

    private void setVeterinarianStatus(User user, String status) throws Exception {
        http.perform(authenticated(patch("/api/v1/veterinarians/" + user.getId() + "/status"), administrator,
                Map.of("status", status))).andExpect(status().isOk());
    }

    private String token(User user) {
        User current = users.findById(user.getId()).orElseThrow();
        return tokens.issue(current, permissions.resolve(current.getRole())).accessToken();
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + token(actor));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        return authenticated(request, actor).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    }

    private JsonNode response(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }

    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), json.writerWithDefaultPrettyPrinter().writeValueAsString(response(result)));
    }

    private record PreparedEncounter(UUID encounterId, UUID appointmentId, User veterinarian) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class HistoryClockConfiguration {
        @Bean @Primary HistoryClock historyClock() { return new HistoryClock(); }
    }

    static final class HistoryClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Instant instant() { return current.get(); }
        @Override public Clock withZone(ZoneId zone) {
            return new Clock() {
                @Override public ZoneId getZone() { return zone; }
                @Override public Instant instant() { return current.get(); }
                @Override public Clock withZone(ZoneId nextZone) { return HistoryClock.this.withZone(nextZone); }
            };
        }
    }
}
