package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises authenticated HTTP, the real appointment lifecycle, PostgreSQL and durable clinical audit. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, ClinicalEncounterHttpIntegrationTests.ClinicalClockConfiguration.class})
@Transactional
class ClinicalEncounterHttpIntegrationTests {
    private static final String BASE_PATH = "/api/v1/clinical-encounters";
    private static final String APPOINTMENTS_PATH = "/api/v1/appointments";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final Instant CURRENT_TIME = Instant.parse("2026-10-05T12:00:00Z");
    private static final List<String> CLINICAL_FIELDS = List.of("reason", "anamnesis", "physicalExamination", "diagnosis", "carePlan");

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerProfileRepository ownerProfileRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private VeterinarianProfileRepository veterinarianProfileRepository;
    @Autowired private VeterinarianQualificationRepository veterinarianQualificationRepository;
    @Autowired private AdministratorProfileRepository administratorProfileRepository;
    @Autowired private VeterinarianAvailabilitySlotRepository availabilitySlotRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserPermissionResolver userPermissionResolver;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ClinicalTestClock clinicalClock;

    private User administrator;
    private User owner;
    private User otherOwner;
    private User veterinarian;
    private User otherVeterinarian;
    private Pet pet;
    private VeterinarianAvailabilitySlot appointmentSlot;
    private VeterinarianAvailabilitySlot replacementSlot;

    @BeforeEach
    void createFictionalAccountsAndAvailableAppointments() {
        clinicalClock.set(CURRENT_TIME);
        administrator = account(UserRole.ADMINISTRATOR);
        administratorProfileRepository.saveAndFlush(new AdministratorProfile(administrator,
                "Administradora de pruebas", administrator.getId(), CURRENT_TIME));
        owner = owner();
        otherOwner = owner();
        veterinarian = veterinarian();
        otherVeterinarian = veterinarian();
        pet = petRepository.saveAndFlush(new Pet(ownerProfileRepository.findById(owner.getId()).orElseThrow(),
                "Luna", PetSpecies.DOG, null, null, null, false, CURRENT_TIME));
        Instant startsAt = LocalDate.now(Clock.fixed(CURRENT_TIME, BUSINESS_ZONE)).plusDays(1)
                .atTime(9, 0).atZone(BUSINESS_ZONE).toInstant();
        appointmentSlot = slot(veterinarian, startsAt);
        replacementSlot = slot(otherVeterinarian, startsAt);
    }

    @Test
    void createsAnEmptyDraftWithServerResolvedRelationshipsAndDurableAudit() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        MvcResult result = mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.appointmentId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.petId").value(pet.getId().toString()))
                .andExpect(jsonPath("$.veterinarianId").value(veterinarian.getId().toString()))
                .andExpect(jsonPath("$.createdBy").value(veterinarian.getId().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0)).andReturn();
        JsonNode encounter = response(result);
        UUID encounterId = UUID.fromString(encounter.path("id").asText());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(BASE_PATH + "/" + encounterId);
        for (String field : CLINICAL_FIELDS) {
            assertThat(encounter.has(field)).isTrue();
            assertThat(encounter.path(field).isNull()).isTrue();
        }
        assertThat(encounter.has("finalizedAt")).isTrue();
        assertThat(encounter.path("finalizedAt").isNull()).isTrue();
        assertThat(encounter.path("createdAt").asText()).isEqualTo(CURRENT_TIME.toString());
        assertThat(result.getResponse().getContentAsString()).doesNotContain(owner.getEmail(), "password", "clientRequestId");
        assertAudit(encounterId, 0, 1, 1);
        Map<String, Object> event = jdbcTemplate.queryForMap("select event_type,actor_id,veterinarian_id,previous_veterinarian_id " +
                "from clinical_encounter_events where encounter_id=?", encounterId);
        assertThat(event.get("event_type")).isEqualTo("CREATED");
        assertThat(event.get("actor_id")).isEqualTo(veterinarian.getId());
        assertThat(event.get("veterinarian_id")).isEqualTo(veterinarian.getId());
        assertThat(event.get("previous_veterinarian_id")).isNull();
        saveExample("clinical-created.json", result);
    }

    @Test
    void equivalentCreationReplayReturnsTheSameDraftWithoutAnotherOperationOrEvent() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        Map<String, Object> request = creation(appointmentId);
        JsonNode first = response(mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, request))
                .andExpect(status().isCreated()).andReturn());
        JsonNode replay = response(mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, request))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private")).andReturn());
        assertThat(replay).isEqualTo(first);
        assertAudit(UUID.fromString(first.path("id").asText()), 0, 1, 1);
    }

    @Test
    void aSecondCreationIntentCannotCreateAnotherEncounterForTheSameAppointment() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        MvcResult conflict = mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLINICAL_ENCOUNTER_ALREADY_EXISTS"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        assertAudit(encounterId, 0, 1, 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinical_encounters where appointment_id=?",
                Integer.class, appointmentId)).isEqualTo(1);
        saveExample("clinical-conflict-error.json", conflict);
    }

    @Test
    void creationRequestKeyCannotBeReusedForAnotherAppointment() throws Exception {
        UUID firstAppointmentId = createConfirmedAppointment(appointmentSlot);
        UUID secondAppointmentId = createConfirmedAppointment(slot(veterinarian, appointmentSlot.getEndsAt()));
        Map<String, Object> originalRequest = creation(firstAppointmentId);
        UUID encounterId = UUID.fromString(response(mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, originalRequest))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
        Map<String, Object> changedRequest = new HashMap<>(originalRequest);
        changedRequest.put("appointmentId", secondAppointmentId);
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, changedRequest)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLINICAL_IDEMPOTENCY_CONFLICT"));
        assertAudit(encounterId, 0, 1, 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinical_encounters where appointment_id=?",
                Integer.class, secondAppointmentId)).isZero();
    }

    @Test
    void savesAndReadsAllClinicalFieldsWithAnExactVersionAndRecordedAuthor() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        MvcResult saved = mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.reason").value("Consulta de seguimiento"))
                .andExpect(jsonPath("$.anamnesis").value("Antecedentes descritos por el propietario"))
                .andExpect(jsonPath("$.physicalExamination").value("Examen registrado por el profesional"))
                .andExpect(jsonPath("$.diagnosis").value("Diagnóstico registrado por el profesional"))
                .andExpect(jsonPath("$.carePlan").value("Recomendaciones registradas por el profesional"))
                .andReturn();
        JsonNode read = response(mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), veterinarian))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private")).andReturn());
        assertThat(read).isEqualTo(response(saved));
        assertAudit(encounterId, 1, 2, 2);
        assertThat(jdbcTemplate.queryForObject("select actor_id from clinical_encounter_events where encounter_id=? and encounter_version=1",
                UUID.class, encounterId)).isEqualTo(veterinarian.getId());
        saveExample("clinical-updated.json", saved);
    }

    @Test
    void equivalentSaveReplayDoesNotAdvanceVersionOrDuplicateAudit() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        Map<String, Object> request = update(0);
        JsonNode first = response(mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request))
                .andExpect(status().isOk()).andReturn());
        JsonNode replay = response(mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request))
                .andExpect(status().isOk()).andReturn());
        assertThat(replay).isEqualTo(first);
        assertAudit(encounterId, 1, 2, 2);
    }

    @Test
    void aSaveRequestKeyCannotOverwriteItsOriginalIntent() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        Map<String, Object> request = update(0);
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isOk());
        request.put("diagnosis", "Otra intención clínica");
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLINICAL_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId))
                .isEqualTo("Diagnóstico registrado por el profesional");
        assertAudit(encounterId, 1, 2, 2);
    }

    @Test
    void aStaleVersionCannotOverwriteANewerClinicalDraft() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        assertAudit(encounterId, 1, 2, 2);
    }

    @Test
    void replacementClearsOmittedAndBlankFieldsAndNormalizesWhitespace() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("clientRequestId", UUID.randomUUID());
        replacement.put("expectedVersion", 1);
        replacement.put("reason", "  Motivo actualizado  ");
        replacement.put("anamnesis", " \t\n ");
        JsonNode result = response(mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, replacement))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Motivo actualizado")).andReturn());
        for (String field : CLINICAL_FIELDS.subList(1, CLINICAL_FIELDS.size())) {
            assertThat(result.path(field).isNull()).as("PUT clears %s", field).isTrue();
        }
        assertAudit(encounterId, 2, 3, 3);
    }

    @Test
    void anotherVeterinarianCannotCreateReadDiscoverOrUpdateAnUnassignedDraft() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        mockMvc.perform(authenticated(post(BASE_PATH), otherVeterinarian, creation(appointmentId)))
                .andExpect(status().isNotFound());
        UUID encounterId = createDraft(appointmentId);
        MvcResult denied = mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), otherVeterinarian))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("CLINICAL_ENCOUNTER_NOT_FOUND"))
                .andReturn();
        mockMvc.perform(authenticated(get(discoveryPath(appointmentId)), otherVeterinarian)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), otherVeterinarian, update(0)))
                .andExpect(status().isNotFound());
        assertThat(denied.getResponse().getContentAsString()).doesNotContain(pet.getName(), owner.getEmail(), "Consulta");
        assertAudit(encounterId, 0, 1, 1);
        saveExample("clinical-not-found-error.json", denied);
    }

    @Test
    void ownersAndAdministrativeRolesCannotWriteOrReadClinicalDrafts() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        User superAdministrator = account(UserRole.SUPER_ADMIN);
        for (User actor : List.of(owner, otherOwner, administrator, superAdministrator)) {
            mockMvc.perform(authenticated(post(BASE_PATH), actor, creation(appointmentId))).andExpect(status().isForbidden());
            mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), actor, update(0))).andExpect(status().isForbidden());
            int readStatus = actor.getRole() == UserRole.OWNER ? 404 : 403;
            mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), actor)).andExpect(status().is(readStatus));
            mockMvc.perform(authenticated(get(discoveryPath(appointmentId)), actor)).andExpect(status().is(readStatus));
        }
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void authenticationIsRequiredForEveryClinicalOperation() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsBytes(creation(appointmentId))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE_PATH + "/" + encounterId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(discoveryPath(appointmentId))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE_PATH + "/" + encounterId).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsBytes(update(0))))
                .andExpect(status().isUnauthorized());
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void currentVeterinarianCanDiscoverTheExistingDraftFromItsAppointment() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        mockMvc.perform(authenticated(get(discoveryPath(appointmentId)), veterinarian)).andExpect(status().isNotFound());
        UUID encounterId = createDraft(appointmentId);
        mockMvc.perform(authenticated(get(discoveryPath(appointmentId)), veterinarian)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.id").value(encounterId.toString()));
        mockMvc.perform(authenticated(get(discoveryPath(UUID.randomUUID())), veterinarian)).andExpect(status().isNotFound());
    }

    @Test
    void creatingADraftRequiresAConfirmedAppointment() throws Exception {
        UUID appointmentId = createAppointment(appointmentSlot);
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_APPOINTMENT_NOT_ELIGIBLE"));
        assertNoDraft(appointmentId);
    }

    @Test
    void cancelledAppointmentsCannotStartOrContinueADraft() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        long appointmentVersion = jdbcTemplate.queryForObject("select version from appointments where id=?", Long.class, appointmentId);
        mockMvc.perform(authenticated(patch(APPOINTMENTS_PATH + "/" + appointmentId + "/status"), administrator,
                Map.of("status", "CANCELLED", "expectedVersion", appointmentVersion, "reason", "Cancelación de prueba")))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_APPOINTMENT_NOT_ELIGIBLE"));
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void appointmentsNeedingReassignmentCannotStartOrContinueADrafts() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        jdbcTemplate.update("update appointments set assignment_status='NEEDS_REASSIGNMENT' where id=?", appointmentId);
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_APPOINTMENT_NOT_ELIGIBLE"));
        assertNoDraft(appointmentId);
        jdbcTemplate.update("update appointments set assignment_status='ASSIGNED' where id=?", appointmentId);
        UUID encounterId = createDraft(appointmentId);
        jdbcTemplate.update("update appointments set assignment_status='NEEDS_REASSIGNMENT' where id=?", appointmentId);
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_APPOINTMENT_NOT_ELIGIBLE"));
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void anInactiveOwnerPreventsCreationAndFurtherDraftWrites() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        owner.deactivate();
        userRepository.flush();
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_PARTICIPANT_INACTIVE"));
        assertNoDraft(appointmentId);
        owner.activate();
        userRepository.flush();
        UUID encounterId = createDraft(appointmentId);
        owner.deactivate();
        userRepository.flush();
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_PARTICIPANT_INACTIVE"));
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void anArchivedPetPreventsCreationAndFurtherDraftWrites() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        jdbcTemplate.update("update pets set active=false where id=?", pet.getId());
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_PARTICIPANT_INACTIVE"));
        assertNoDraft(appointmentId);
        jdbcTemplate.update("update pets set active=true where id=?", pet.getId());
        UUID encounterId = createDraft(appointmentId);
        jdbcTemplate.update("update pets set active=false where id=?", pet.getId());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_PARTICIPANT_INACTIVE"));
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void disablingTheVeterinarianRevokesClinicalHttpAccessImmediately() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        String token = token(veterinarian);
        veterinarian.deactivate();
        userRepository.flush();
        mockMvc.perform(get(BASE_PATH + "/" + encounterId).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE_PATH + "/" + encounterId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsBytes(update(0))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsBytes(creation(appointmentId))))
                .andExpect(status().isUnauthorized());
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void malformedCreationBodiesCannotInjectRelationshipsOrProducePartialRows() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        for (String body : List.of("{}", "null", "[]", "{\"clientRequestId\":null,\"appointmentId\":\"" + appointmentId + "\"}",
                "{\"clientRequestId\":\"invalid\",\"appointmentId\":\"" + appointmentId + "\"}",
                "{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"appointmentId\":\"" + appointmentId + "\",\"ownerId\":\"" + otherOwner.getId() + "\"}")) {
            mockMvc.perform(authenticated(post(BASE_PATH), veterinarian).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian).contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType());
        assertNoDraft(appointmentId);
    }

    @Test
    void invalidVersionsTextLengthsAndUnknownSaveFieldsNeverChangeTheDraft() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        for (Object invalidVersion : List.of(-1, "0", 0.5, true)) {
            Map<String, Object> request = update(0);
            request.put("expectedVersion", invalidVersion);
            mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isBadRequest());
        }
        for (String field : List.of("clientRequestId", "expectedVersion")) {
            Map<String, Object> request = update(0);
            request.remove(field);
            mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isBadRequest());
        }
        for (String field : CLINICAL_FIELDS) {
            Map<String, Object> request = update(0);
            request.put(field, "x".repeat(4001));
            mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isBadRequest());
            request.put(field, 42);
            mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request)).andExpect(status().isBadRequest());
        }
        Map<String, Object> injectedRelation = update(0);
        injectedRelation.put("petId", UUID.randomUUID());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, injectedRelation)).andExpect(status().isBadRequest());
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void acceptsTheDocumentedMaximumLengthForEveryClinicalField() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        Map<String, Object> request = update(0);
        CLINICAL_FIELDS.forEach(field -> request.put(field, "x".repeat(4000)));
        JsonNode result = response(mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request))
                .andExpect(status().isOk()).andReturn());
        for (String field : CLINICAL_FIELDS) { assertThat(result.path(field).asText()).hasSize(4000); }
        assertAudit(encounterId, 1, 2, 2);
    }

    @Test
    void aReassignedVeterinarianCanContinueTheDraftWithoutLosingItsCreatorOrAudit() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        Map<String, Object> originalCreation = creation(appointmentId);
        UUID encounterId = UUID.fromString(response(mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, originalCreation))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        setVeterinarianStatus(veterinarian, "DISABLED");
        long appointmentVersion = jdbcTemplate.queryForObject("select version from appointments where id=?", Long.class, appointmentId);
        mockMvc.perform(authenticated(post(APPOINTMENTS_PATH + "/" + appointmentId + "/veterinarian-reassignments"), administrator,
                        Map.of("clientRequestId", UUID.randomUUID(), "availabilitySlotId", replacementSlot.getId(),
                                "expectedAvailabilitySlotVersion", replacementSlot.getVersion(), "expectedVersion", appointmentVersion,
                                "reason", "Continuidad de atención con otro profesional")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.veterinarianId").value(otherVeterinarian.getId().toString()));
        setVeterinarianStatus(veterinarian, "ACTIVE");
        mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), veterinarian)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(discoveryPath(appointmentId)), veterinarian)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(1))).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, originalCreation)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), otherVeterinarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(veterinarian.getId().toString()));
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), otherVeterinarian, update(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.createdBy").value(veterinarian.getId().toString()))
                .andExpect(jsonPath("$.veterinarianId").value(otherVeterinarian.getId().toString()));
        assertAudit(encounterId, 2, 3, 3);
        Map<String, Object> event = jdbcTemplate.queryForMap("select actor_id,previous_veterinarian_id,veterinarian_id " +
                "from clinical_encounter_events where encounter_id=? and encounter_version=2", encounterId);
        assertThat(event.get("actor_id")).isEqualTo(otherVeterinarian.getId());
        assertThat(event.get("previous_veterinarian_id")).isEqualTo(veterinarian.getId());
        assertThat(event.get("veterinarian_id")).isEqualTo(otherVeterinarian.getId());
    }

    @Test
    void clinicalAuditAndIdempotencyRowsCannotBeChangedOrDeleted() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        for (String table : List.of("clinical_encounter_events", "clinical_encounter_operations")) {
            assertDatabaseRejectsMutation("update " + table + " set actor_id=? where encounter_id=?", otherVeterinarian.getId(), encounterId);
            assertDatabaseRejectsMutation("delete from " + table + " where encounter_id=?", encounterId);
        }
        assertAudit(encounterId, 0, 1, 1);
    }

    private UUID createAppointment(VeterinarianAvailabilitySlot slot) throws Exception {
        CreateAppointmentRequestDTO request = new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(),
                slot.getId(), slot.getVersion(), "Consulta general de la mascota");
        return UUID.fromString(response(mockMvc.perform(authenticated(post(APPOINTMENTS_PATH), owner, request))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
    }

    private UUID createConfirmedAppointment(VeterinarianAvailabilitySlot slot) throws Exception {
        UUID appointmentId = createAppointment(slot);
        mockMvc.perform(authenticated(patch(APPOINTMENTS_PATH + "/" + appointmentId + "/status"), administrator,
                Map.of("status", "CONFIRMED", "expectedVersion", 0))).andExpect(status().isOk());
        return appointmentId;
    }

    @Test
    void treatmentRoundTripReplayAndClearingShareTheEncounterVersionWithoutChangingClinicalText() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        Map<String, Object> treatment = treatment(1);
        var saved = mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, treatment))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.prescriptions[0].medicationName").value("Medicamento ficticio"))
                .andExpect(jsonPath("$.prescriptions[0].dose").value(1.25))
                .andExpect(jsonPath("$.prescriptions[0].startsAt").value("2026-10-06T14:00:00Z"))
                .andExpect(jsonPath("$.controls[0].reason").value("Revisión de evolución"))
                .andReturn();
        saveExample("clinical-treatment.json", saved);
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, treatment))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(authenticated(get(treatmentPath(encounterId)), veterinarian))
                .andExpect(status().isOk()).andExpect(content().json(saved.getResponse().getContentAsString()));
        mockMvc.perform(authenticated(get(BASE_PATH + "/" + encounterId), veterinarian))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Consulta de seguimiento"));
        Map<String, Object> empty = Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", 2,
                "prescriptions", List.of(), "controls", List.of());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, empty))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prescriptions").isEmpty())
                .andExpect(jsonPath("$.controls").isEmpty()).andExpect(jsonPath("$.version").value(3));
        assertAudit(encounterId, 3, 4, 4);
    }

    @Test
    void treatmentRejectsAStaleVersionAndAnIdempotencyKeyWithAnotherDose() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        Map<String, Object> request = treatment(0);
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, request)).andExpect(status().isOk());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, treatment(0))).andExpect(status().isConflict());
        Map<String, Object> different = new HashMap<>(request);
        Map<String, Object> prescription = prescription();
        prescription.put("dose", 2);
        different.put("prescriptions", List.of(prescription));
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, different)).andExpect(status().isConflict());
        assertAudit(encounterId, 1, 2, 2);
    }

    @Test
    void treatmentRejectsUnsafeOrAmbiguousPrescriptionValuesWithoutSaving() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        List<Map<String, Object>> invalidValues = List.of(
                Map.of("dose", -1), Map.of("dose", 0), Map.of("dose", "1.0000001"),
                Map.of("intervalHours", 0), Map.of("intervalHours", 169), Map.of("intervalHours", 1.5),
                Map.of("medicationName", " "), Map.of("doseUnit", " "), Map.of("administrationRoute", " "),
                Map.of("endsAt", "2026-10-06T09:00:00-05:00"),
                Map.of("endsAt", "2028-10-06T09:00:00-05:00"),
                Map.of("startsAt", "2026-10-06T09:00:00"), Map.of("startsAt", 1791295200));
        for (Map<String, Object> invalid : invalidValues) {
            Map<String, Object> request = treatment(0);
            Map<String, Object> prescription = prescription();
            prescription.putAll(invalid);
            request.put("prescriptions", List.of(prescription));
            mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, request))
                    .andExpect(status().isBadRequest());
        }
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void treatmentRequiresBothListsAndRejectsDuplicateIdentifiersAndBlankControlReasons() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        for (String missing : List.of("prescriptions", "controls")) {
            Map<String, Object> request = treatment(0); request.remove(missing);
            mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, request)).andExpect(status().isBadRequest());
        }
        Map<String, Object> repeatedPrescription = treatment(0);
        Map<String, Object> medication = prescription();
        repeatedPrescription.put("prescriptions", List.of(medication, medication));
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, repeatedPrescription)).andExpect(status().isBadRequest());
        Map<String, Object> invalidControl = treatment(0);
        invalidControl.put("controls", List.of(Map.of("id", UUID.randomUUID(), "scheduledAt", "2026-10-15T10:00:00-05:00", "reason", " ")));
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, invalidControl)).andExpect(status().isBadRequest());
        Map<String, Object> tooMany = treatment(0);
        tooMany.put("prescriptions", java.util.stream.IntStream.range(0, 51).mapToObj(index -> prescription()).toList());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, tooMany)).andExpect(status().isBadRequest());
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void treatmentUsesTheSameResourcePrivacyAsItsDraft() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        mockMvc.perform(authenticated(get(treatmentPath(encounterId)), owner)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(treatmentPath(encounterId)), otherVeterinarian)).andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(treatmentPath(encounterId)), administrator)).andExpect(status().isForbidden());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), owner, treatment(0))).andExpect(status().isForbidden());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), otherVeterinarian, treatment(0))).andExpect(status().isNotFound());
        assertAudit(encounterId, 0, 1, 1);
    }

    @Test
    void clinicalTextRejectsNullBytesAndControlCharactersWithoutWritingAudit() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        for (String field : CLINICAL_FIELDS) {
            for (char forbidden : new char[]{0, 1, 127}) {
                Map<String, Object> request = update(0);
                request.put(field, "Contenido" + forbidden + " clínico");
                mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, request))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
            }
        }
        assertAudit(encounterId, 0, 1, 1);
        Map<String, Object> multiline = update(0);
        multiline.put("anamnesis", "Primera línea\nSegunda línea\tDetalle");
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, multiline))
                .andExpect(status().isOk()).andExpect(jsonPath("$.anamnesis").value("Primera línea\nSegunda línea\tDetalle"));
        assertAudit(encounterId, 1, 2, 2);
    }

    private String treatmentPath(UUID encounterId) { return BASE_PATH + "/" + encounterId + "/treatment"; }

    private Map<String, Object> treatment(long expectedVersion) {
        return new HashMap<>(Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", expectedVersion,
                "prescriptions", List.of(prescription()), "controls", List.of(Map.of("id", UUID.randomUUID(),
                        "scheduledAt", "2026-10-15T10:00:00-05:00", "reason", "Revisión de evolución"))));
    }

    private Map<String, Object> prescription() {
        Map<String, Object> result = new HashMap<>();
        result.put("id", UUID.randomUUID()); result.put("medicationName", "Medicamento ficticio");
        result.put("dose", new java.math.BigDecimal("1.25")); result.put("doseUnit", "mL");
        result.put("administrationRoute", "Oral"); result.put("intervalHours", 8);
        result.put("startsAt", "2026-10-06T09:00:00-05:00"); result.put("endsAt", "2026-10-13T09:00:00-05:00");
        result.put("instructions", "Instrucciones ficticias registradas por el profesional");
        return result;
    }

    @Test
    void finalizationPublishesImmutableClinicalContentAndCompletesItsAppointmentExactlyOnce() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, treatment(1))).andExpect(status().isOk());
        clinicalClock.set(appointmentSlot.getStartsAt());
        Map<String, Object> request = finalization(2, 1);
        var finalized = mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.finalizedAt").isNotEmpty())
                .andExpect(header().string("Cache-Control", "no-store, private")).andReturn();
        saveExample("clinical-finalized.json", finalized);
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, request))
                .andExpect(status().isOk()).andExpect(content().json(finalized.getResponse().getContentAsString()));
        var completed = mockMvc.perform(authenticated(get(APPOINTMENTS_PATH + "/" + appointmentId), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(2)).andReturn();
        saveExample("clinical-completed-appointment.json", completed);
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(3))).andExpect(status().isConflict());
        mockMvc.perform(authenticated(put(treatmentPath(encounterId)), veterinarian, treatment(3))).andExpect(status().isConflict());
        mockMvc.perform(authenticated(patch(APPOINTMENTS_PATH + "/" + appointmentId + "/status"), administrator,
                Map.of("status", "NO_SHOW", "expectedVersion", 2, "reason", "Cambio no permitido"))).andExpect(status().isConflict());
        assertAudit(encounterId, 3, 4, 4);
        assertThat(jdbcTemplate.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='COMPLETED'", Integer.class, appointmentId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and status in ('PENDING','SENDING')", Integer.class, appointmentId)).isZero();
        assertDatabaseRejectsMutation("update clinical_encounters set diagnosis='Cambio' where id=?", encounterId);
        assertDatabaseRejectsMutation("delete from clinical_prescriptions where encounter_id=?", encounterId);
        assertDatabaseRejectsMutation("update clinical_controls set reason='Cambio' where encounter_id=?", encounterId);
    }

    @Test
    void finalizationRejectsAnEarlyTimeOrMissingClinicalContentWithoutChangingAppointment() throws Exception {
        UUID appointmentId = createConfirmedAppointment(appointmentSlot);
        UUID encounterId = createDraft(appointmentId);
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, finalization(0, 1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLINICAL_APPOINTMENT_NOT_STARTED"));
        clinicalClock.set(appointmentSlot.getStartsAt());
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, finalization(0, 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CLINICAL_REQUIRED_FIELDS_MISSING"));
        assertAudit(encounterId, 0, 1, 1);
        assertThat(jdbcTemplate.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("CONFIRMED");
    }

    @Test
    void finalizationChecksBothVersionsAndDisallowsOtherRolesOrVeterinarians() throws Exception {
        UUID encounterId = createDraft(createConfirmedAppointment(appointmentSlot));
        mockMvc.perform(authenticated(put(BASE_PATH + "/" + encounterId), veterinarian, update(0))).andExpect(status().isOk());
        clinicalClock.set(appointmentSlot.getStartsAt());
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, finalization(0, 1))).andExpect(status().isConflict());
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, finalization(1, 0))).andExpect(status().isConflict());
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), otherVeterinarian, finalization(1, 1))).andExpect(status().isNotFound());
        for (User wrongRole : List.of(owner, administrator, account(UserRole.SUPER_ADMIN))) {
            mockMvc.perform(authenticated(post(finalizationPath(encounterId)), wrongRole, finalization(1, 1))).andExpect(status().isForbidden());
        }
        Map<String, Object> missingVersion = finalization(1, 1); missingVersion.remove("expectedAppointmentVersion");
        mockMvc.perform(authenticated(post(finalizationPath(encounterId)), veterinarian, missingVersion)).andExpect(status().isBadRequest());
        assertAudit(encounterId, 1, 2, 2);
    }

    private String finalizationPath(UUID encounterId) { return BASE_PATH + "/" + encounterId + "/finalizations"; }

    private Map<String, Object> finalization(long encounterVersion, long appointmentVersion) {
        return new HashMap<>(Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", encounterVersion,
                "expectedAppointmentVersion", appointmentVersion));
    }

    private UUID createDraft(UUID appointmentId) throws Exception {
        return UUID.fromString(response(mockMvc.perform(authenticated(post(BASE_PATH), veterinarian, creation(appointmentId)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
    }

    private Map<String, Object> creation(UUID appointmentId) {
        return new HashMap<>(Map.of("clientRequestId", UUID.randomUUID(), "appointmentId", appointmentId));
    }

    private Map<String, Object> update(long expectedVersion) {
        return new HashMap<>(Map.of("clientRequestId", UUID.randomUUID(), "expectedVersion", expectedVersion,
                "reason", "Consulta de seguimiento", "anamnesis", "Antecedentes descritos por el propietario",
                "physicalExamination", "Examen registrado por el profesional", "diagnosis", "Diagnóstico registrado por el profesional",
                "carePlan", "Recomendaciones registradas por el profesional"));
    }

    private void assertAudit(UUID encounterId, long version, int events, int operations) {
        assertThat(jdbcTemplate.queryForObject("select version from clinical_encounters where id=?", Long.class, encounterId)).isEqualTo(version);
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinical_encounter_events where encounter_id=?", Integer.class, encounterId))
                .isEqualTo(events);
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinical_encounter_operations where encounter_id=?", Integer.class, encounterId))
                .isEqualTo(operations);
    }

    private void assertNoDraft(UUID appointmentId) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
    }

    private void assertDatabaseRejectsMutation(String statement, Object... values) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement preparedStatement = connection.prepareStatement(statement)) {
                for (int index = 0; index < values.length; index++) { preparedStatement.setObject(index + 1, values[index]); }
                preparedStatement.executeUpdate();
                throw new AssertionError("PostgreSQL must reject a clinical history mutation.");
            } catch (SQLException exception) {
                connection.rollback(savepoint);
                assertThat(exception.getSQLState()).isEqualTo("P0001");
            } finally {
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }

    private String discoveryPath(UUID appointmentId) { return APPOINTMENTS_PATH + "/" + appointmentId + "/clinical-encounter"; }

    private void setVeterinarianStatus(User user, String status) throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/veterinarians/" + user.getId() + "/status"), administrator, Map.of("status", status)))
                .andExpect(status().isOk());
        userRepository.flush();
    }

    private User account(UserRole role) {
        return userRepository.saveAndFlush(new User(role.name().toLowerCase() + "-clinical-" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", role));
    }

    private User owner() {
        User user = account(UserRole.OWNER);
        ownerProfileRepository.saveAndFlush(new OwnerProfile(user, "María Gómez", LocalDate.of(1955, 5, 20), "+573001234567"));
        return user;
    }

    private User veterinarian() {
        User user = account(UserRole.VETERINARIAN);
        VeterinarianProfile profile = veterinarianProfileRepository.saveAndFlush(new VeterinarianProfile(user,
                "Dra. Laura Ramírez", "+573001234567", "MV-" + UUID.randomUUID().toString().substring(0, 12),
                "Profesional ficticio para pruebas", administrator.getId(), CURRENT_TIME));
        veterinarianQualificationRepository.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                "Medicina veterinaria", "Universidad de Ejemplo", 2020, true, CURRENT_TIME));
        return user;
    }

    private VeterinarianAvailabilitySlot slot(User assignedVeterinarian, Instant startsAt) {
        return availabilitySlotRepository.saveAndFlush(new VeterinarianAvailabilitySlot(assignedVeterinarian.getId(),
                startsAt, startsAt.plusSeconds(1800), administrator.getId(), CURRENT_TIME));
    }

    private String token(User user) { return jwtTokenService.issue(user, userPermissionResolver.resolve(user.getRole())).accessToken(); }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, User user) {
        return request.header("Authorization", "Bearer " + token(user));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, User user, Object body) throws Exception {
        return authenticated(request, user).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsBytes(body));
    }

    private JsonNode response(MvcResult result) throws Exception { return jsonMapper.readTree(result.getResponse().getContentAsByteArray()); }

    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response(result)));
    }

    static class ClinicalTestClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Instant> current = new java.util.concurrent.atomic.AtomicReference<>(CURRENT_TIME);
        void set(Instant value) { current.set(value); }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current.get(), zone); }
        @Override public Instant instant() { return current.get(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClinicalClockConfiguration {
        @Bean @Primary ClinicalTestClock clinicalTestClock() { return new ClinicalTestClock(); }
    }
}
