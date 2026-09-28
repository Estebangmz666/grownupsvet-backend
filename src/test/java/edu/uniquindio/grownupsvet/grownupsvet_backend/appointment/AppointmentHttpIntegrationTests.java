package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.AdministratorProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.AdministratorProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianQualificationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, AppointmentHttpIntegrationTests.AppointmentClockConfiguration.class})
@Transactional
class AppointmentHttpIntegrationTests {
    private static final String BASE="/api/v1/appointments";
    private static final ZoneId ZONE=ZoneId.of("America/Bogota");
    private static final Instant INITIAL_TIME=Instant.parse("2026-10-05T01:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private PetRepository pets;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private VeterinarianQualificationRepository veterinarianQualifications;
    @Autowired private AdministratorProfileRepository administratorProfiles;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @Autowired private JwtTokenService tokenService;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private AppointmentTestClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentService appointments;

    private User administrator;
    private User ownerOne;
    private User ownerTwo;
    private User veterinarianOne;
    private User veterinarianTwo;
    private Pet petOne;
    private Pet petTwo;
    private VeterinarianAvailabilitySlot slotOne;
    private VeterinarianAvailabilitySlot slotTwo;

    @BeforeEach
    void createFixture() {
        clock.set(INITIAL_TIME);
        administrator=account(UserRole.ADMINISTRATOR);
        administratorProfiles.saveAndFlush(new AdministratorProfile(administrator,"Admin Pruebas",administrator.getId(),clock.instant()));
        ownerOne=owner("owner-one");
        ownerTwo=owner("owner-two");
        veterinarianOne=veterinarian("veterinarian-one",administrator.getId());
        veterinarianTwo=veterinarian("veterinarian-two",administrator.getId());
        petOne=pet(ownerOne,"Luna");
        petTwo=pet(ownerTwo,"Toby");
        LocalDate tomorrow=LocalDate.now(clock.withZone(ZONE)).plusDays(1);
        Instant start=tomorrow.atTime(23,0).atZone(ZONE).toInstant();
        slotOne=slot(veterinarianOne,start,administrator.getId());
        slotTwo=slot(veterinarianTwo,start,administrator.getId());
    }

    @Test
    void ownerRequestIsIdempotentAndReservesTheTurnForOnlyOneAppointment() throws Exception {
        var request=request(UUID.randomUUID(),petOne,slotOne);
        var created=mvc.perform(authenticated(post(BASE),ownerOne,request)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(header().string("Cache-Control","no-store, private")).andReturn();
        String id=body(created).path("id").asText();
        assertThat(body(created).path("status").asText()).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=?",Integer.class,UUID.fromString(id))).isEqualTo(1);

        var replay=mvc.perform(authenticated(post(BASE),ownerOne,request)).andExpect(status().isOk()).andReturn();
        assertThat(body(replay).path("id").asText()).isEqualTo(id);

        var available=mvc.perform(authenticated(get("/api/v1/availability-slots").param("from",LocalDate.now(clock.withZone(ZONE)).plusDays(1).toString())
                .param("to",LocalDate.now(clock.withZone(ZONE)).plusDays(1).toString()),ownerOne)).andExpect(status().isOk()).andReturn();
        assertThat(body(available).path("items").toString()).doesNotContain(slotOne.getId().toString());

        var conflicting=request(UUID.randomUUID(),petTwo,slotOne);
        mvc.perform(authenticated(post(BASE),ownerTwo,conflicting)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_SLOT_OCCUPIED"));
        assertThat(jdbc.queryForObject("select count(*) from appointments",Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectingReleasesTheSlotAndDifferentOwnersCanThenRequestIt() throws Exception {
        var request=request(UUID.randomUUID(),petOne,slotOne);
        String id=body(mvc.perform(authenticated(post(BASE),ownerOne,request)).andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(authenticated(patch(BASE+"/"+id+"/status"),administrator,
                Map.of("status","REJECTED","expectedVersion",0,"reason","No hay disponibilidad clínica.")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        var second=request(UUID.randomUUID(),petTwo,slotOne);
        mvc.perform(authenticated(post(BASE),ownerTwo,second)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from appointments where status='REQUESTED'",Integer.class)).isEqualTo(1);
    }

    @Test
    void differentPetsOfOneOwnerCanUseDifferentVeterinariansAtTheSameTime() throws Exception {
        Pet secondPet=pet(ownerOne,"Milo");
        mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),petOne,slotOne))).andExpect(status().isCreated());
        mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),secondPet,slotTwo))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from appointments where owner_id=? and starts_at=? and status='REQUESTED'",
                Integer.class,ownerOne.getId(),java.sql.Timestamp.from(slotOne.getStartsAt()))).isEqualTo(2);
    }

    @Test
    void onePetCannotHaveOverlappingAppointmentsWithDifferentVeterinarians() throws Exception {
        mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),petOne,slotOne))).andExpect(status().isCreated());
        mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),petOne,slotTwo))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_PET_ALREADY_SCHEDULED"));
    }

    @Test
    void dailyCutoffCancelsEveryExpiredRequestAndKeepsConfirmedAppointments() throws Exception {
        String pendingId=body(mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),petOne,slotOne)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        String confirmedId=body(mvc.perform(authenticated(post(BASE),ownerTwo,request(UUID.randomUUID(),petTwo,slotTwo)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(authenticated(patch(BASE+"/"+confirmedId+"/status"),administrator,
                Map.of("status","CONFIRMED","expectedVersion",0,"reason",""))).andExpect(status().isOk());
        java.sql.Timestamp expired=java.sql.Timestamp.from(clock.instant().minusSeconds(1));
        jdbc.update("update appointments set confirmation_cutoff_at=? where id in (?,?)",expired,UUID.fromString(pendingId),UUID.fromString(confirmedId));

        assertThat(appointments.expirePendingAppointments()).isEqualTo(1);
        assertThat(appointments.expirePendingAppointments()).isZero();
        assertThat(jdbc.queryForObject("select status from appointments where id=?",String.class,UUID.fromString(pendingId))).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from appointments where id=?",String.class,UUID.fromString(confirmedId))).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='DAILY_CUTOFF'",
                Integer.class,UUID.fromString(pendingId))).isEqualTo(1);
    }

    @Test
    void veterinarianEmailAccessIsAuditedAndOnlyTheCurrentAssigneeCanRevealIt() throws Exception {
        var request=request(UUID.randomUUID(),petOne,slotOne);
        String id=body(mvc.perform(authenticated(post(BASE),ownerOne,request)).andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(authenticated(patch(BASE+"/"+id+"/status"),administrator,
                Map.of("status","CONFIRMED","expectedVersion",0,"reason",""))).andExpect(status().isOk());
        String accessPath=BASE+"/"+id+"/owner-email-accesses";
        mvc.perform(authenticated(post(accessPath),veterinarianOne,Map.of("reason","Intenté contactar por WhatsApp sin respuesta.")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ownerOne.getEmail()));
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_access_audits where appointment_id=?",Integer.class,UUID.fromString(id))).isEqualTo(1);
        disableVeterinarian(veterinarianOne);
        var reassignment=Map.of("clientRequestId",UUID.randomUUID(),"availabilitySlotId",slotTwo.getId(),"expectedAvailabilitySlotVersion",slotTwo.getVersion(),"expectedVersion",3,
                "reason","Reasignación por continuidad de atención.");
        mvc.perform(authenticated(post(BASE+"/"+id+"/veterinarian-reassignments"),administrator,reassignment)).andExpect(status().isOk());
        reactivateVeterinarian(veterinarianOne);
        mvc.perform(authenticated(post(accessPath),veterinarianOne,Map.of("reason","Nuevo intento."))).andExpect(status().isNotFound());
        mvc.perform(authenticated(post(accessPath),veterinarianTwo,Map.of("reason","Contacto por WhatsApp sin respuesta.")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ownerOne.getEmail()));
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='OWNER_REASSIGNED'",Integer.class,UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void generatesAppointmentOpenApiAndRealHttpExamples() throws Exception {
        JsonNode specification=mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        Path output=Path.of("target","generated-openapi");
        Files.createDirectories(output);
        Files.writeString(output.resolve("openapi.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(specification));

        var createRequest=request(UUID.randomUUID(),petOne,slotOne);
        var created=mvc.perform(authenticated(post(BASE),ownerOne,createRequest)).andExpect(status().isCreated()).andReturn();
        String appointmentId=body(created).path("id").asText();
        saveExample("appointment-created.json",created);
        saveExample("appointment-replay.json",mvc.perform(authenticated(post(BASE),ownerOne,createRequest)).andExpect(status().isOk()).andReturn());
        saveExample("appointment-conflict-error.json",mvc.perform(authenticated(post(BASE),ownerTwo,
                request(UUID.randomUUID(),petTwo,slotOne))).andExpect(status().isConflict()).andReturn());
        saveExample("appointment-page.json",mvc.perform(authenticated(get(BASE).param("from",LocalDate.now(clock.withZone(ZONE)).plusDays(1).toString())
                .param("to",LocalDate.now(clock.withZone(ZONE)).plusDays(1).toString()),ownerOne)).andExpect(status().isOk()).andReturn());
        saveExample("appointment-event-page.json",mvc.perform(authenticated(get(BASE+"/"+appointmentId+"/events"),ownerOne))
                .andExpect(status().isOk()).andReturn());
        mvc.perform(authenticated(patch(BASE+"/"+appointmentId+"/status"),administrator,
                Map.of("status","CONFIRMED","expectedVersion",0,"reason",""))).andExpect(status().isOk());
        saveExample("appointment-owner-email.json",mvc.perform(authenticated(post(BASE+"/"+appointmentId+"/owner-email-accesses"),
                veterinarianOne,Map.of("reason","Contacto por WhatsApp sin respuesta."))).andExpect(status().isOk()).andReturn());
    }

    @Test
    void reprogrammingRequiresRecordedPriorAgreementAndPreservesTheOriginalAppointmentOnValidationFailure() throws Exception {
        var request=request(UUID.randomUUID(),petOne,slotOne);
        String id=body(mvc.perform(authenticated(post(BASE),ownerOne,request)).andExpect(status().isCreated()).andReturn()).path("id").asText();
        disableVeterinarian(veterinarianOne);
        Instant alternateStart=slotOne.getStartsAt().plusSeconds(86400);
        VeterinarianAvailabilitySlot alternative=slot(veterinarianTwo,alternateStart,administrator.getId());
        var missingAgreement=Map.of("clientRequestId",UUID.randomUUID(),"availabilitySlotId",alternative.getId(),
                "expectedAvailabilitySlotVersion",alternative.getVersion(),"expectedVersion",1,
                "reason","Cambio acordado de horario.");
        mvc.perform(authenticated(post(BASE+"/"+id+"/veterinarian-reassignments"),administrator,missingAgreement))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?",UUID.class,UUID.fromString(id)))
                .isEqualTo(slotOne.getId());
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='OWNER_REASSIGNED'",Integer.class,UUID.fromString(id))).isZero();

        var agreed=Map.of("clientRequestId",UUID.randomUUID(),"availabilitySlotId",alternative.getId(),
                "expectedAvailabilitySlotVersion",alternative.getVersion(),"expectedVersion",1,
                "reason","Cambio acordado de horario.","agreementChannel","WHATSAPP",
                "agreementContactedAt",clock.instant(),"agreementNote","El propietario aceptó mañana a la misma hora.");
        mvc.perform(authenticated(post(BASE+"/"+id+"/veterinarian-reassignments"),administrator,agreed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startsAt").value(alternateStart.toString()));
        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?",UUID.class,UUID.fromString(id)))
                .isEqualTo(alternative.getId());
        assertThat(jdbc.queryForObject("select agreement_channel from appointment_events where appointment_id=? and event_type='REASSIGNED'",
                String.class,UUID.fromString(id))).isEqualTo("WHATSAPP");
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='OWNER_REASSIGNED'",
                Integer.class,UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void disablingVeterinarianKeepsTheAppointmentAndDurablyNotifiesAnActiveAdministrator() throws Exception {
        String id=body(mvc.perform(authenticated(post(BASE),ownerOne,request(UUID.randomUUID(),petOne,slotOne)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        var disabled=mvc.perform(authenticated(patch("/api/v1/veterinarians/"+veterinarianOne.getId()+"/status"),administrator,
                Map.of("status","DISABLED"))).andReturn();
        assertThat(disabled.getResponse().getStatus()).withFailMessage(disabled.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from appointments where id=?",String.class,UUID.fromString(id))).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select assignment_status from appointments where id=?",String.class,UUID.fromString(id))).isEqualTo("NEEDS_REASSIGNMENT");
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='VETERINARIAN_DISABLED'",
                Integer.class,UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='ADMIN_REASSIGNMENT' and recipient_id=? and status='PENDING'",
                Integer.class,UUID.fromString(id),administrator.getId())).isEqualTo(1);
    }

    @Test
    void blankCreationReasonReturnsValidationErrorWithoutPersistingAnything() throws Exception {
        for (String reason : List.of("   ", "\t\n")) {
            var invalid = new CreateAppointmentRequestDTO(UUID.randomUUID(), petOne.getId(), slotOne.getId(), slotOne.getVersion(), reason);
            mvc.perform(authenticated(post(BASE), ownerOne, invalid)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from appointments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from appointment_events", Integer.class)).isZero();
    }

    @Test
    void blankReassignmentReasonPreservesTheAssignmentAndItsHistory() throws Exception {
        String id = createAppointment();
        disableVeterinarian(veterinarianOne);
        var operation = reassignment(slotTwo, 1);
        operation.put("reason", " \t ");
        mvc.perform(authenticated(post(BASE + "/" + id + "/veterinarian-reassignments"), administrator, operation))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?", UUID.class, UUID.fromString(id)))
                .isEqualTo(slotOne.getId());
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='REASSIGNED'", Integer.class, UUID.fromString(id))).isZero();
    }

    @Test
    void statusReplayRequiresTheOriginalActorAndNormalizedReason() throws Exception {
        String id = createAppointment();
        String path = BASE + "/" + id + "/status";
        var rejection = Map.of("status", "REJECTED", "expectedVersion", 0, "reason", "No hay capacidad clínica.");
        mvc.perform(authenticated(patch(path), administrator, rejection)).andExpect(status().isOk());
        mvc.perform(authenticated(patch(path), administrator,
                Map.of("status", "REJECTED", "expectedVersion", 0, "reason", "  No hay capacidad clínica.  ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(authenticated(patch(path), administrator,
                Map.of("status", "REJECTED", "expectedVersion", 0, "reason", "Otro motivo.")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        User secondAdministrator = account(UserRole.ADMINISTRATOR);
        mvc.perform(authenticated(patch(path), secondAdministrator, rejection))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='REJECTED'", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reason from appointment_events where appointment_id=? and event_type='REJECTED'", String.class, UUID.fromString(id)))
                .isEqualTo("No hay capacidad clínica.");
    }

    @Test
    void systemCancellationCannotBeReplayedAsAnAdministrativeTransition() throws Exception {
        String id = createAppointment();
        clock.set(LocalDate.now(clock.withZone(ZONE)).plusDays(1).atTime(6, 0).atZone(ZONE).toInstant());
        assertThat(appointments.expirePendingAppointments()).isEqualTo(1);
        mvc.perform(authenticated(patch(BASE + "/" + id + "/status"), administrator,
                Map.of("status", "CANCELLED", "expectedVersion", 0, "reason", "DAILY_CONFIRMATION_CUTOFF")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("APPOINTMENT_TRANSITION_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=?", Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void confirmationReplayNormalizesAnOmittedOrBlankOptionalReason() throws Exception {
        String id = createAppointment();
        String path = BASE + "/" + id + "/status";
        mvc.perform(authenticated(patch(path), administrator, Map.of("status", "CONFIRMED", "expectedVersion", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(authenticated(patch(path), administrator, Map.of("status", "CONFIRMED", "expectedVersion", 0, "reason", " \t ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='CONFIRMED'", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void creationReplaySurvivesRejectionAndPetArchivalButChangedIntentConflicts() throws Exception {
        var original = request(UUID.randomUUID(), petOne, slotOne);
        String id = body(mvc.perform(authenticated(post(BASE), ownerOne, original)).andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(authenticated(patch(BASE + "/" + id + "/status"), administrator,
                Map.of("status", "REJECTED", "expectedVersion", 0, "reason", "El propietario decidió esperar."))).andExpect(status().isOk());
        mvc.perform(authenticated(patch("/api/v1/pets/" + petOne.getId()), ownerOne, Map.of("active", false))).andExpect(status().isOk());
        mvc.perform(authenticated(post(BASE), ownerOne, original)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("REJECTED"));
        var different = new CreateAppointmentRequestDTO(original.clientRequestId(), petOne.getId(), slotOne.getId(), slotOne.getVersion(), "Una intención diferente.");
        mvc.perform(authenticated(post(BASE), ownerOne, different)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_IDEMPOTENCY_CONFLICT"));
        mvc.perform(authenticated(post(BASE), ownerTwo, request(original.clientRequestId(), petTwo, slotOne))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ownerTwo.getId().toString()));
        assertThat(jdbc.queryForObject("select count(*) from appointments", Integer.class)).isEqualTo(2);
    }

    @Test
    void deactivatedOwnerAndTokensWithObsoletePermissionsCannotReplayACreation() throws Exception {
        var original = request(UUID.randomUUID(), petOne, slotOne);
        mvc.perform(authenticated(post(BASE), ownerOne, original)).andExpect(status().isCreated());
        String obsoletePermissionsToken = tokenService.issue(ownerOne, List.of("PROFILE_READ_SELF")).accessToken();
        mvc.perform(post(BASE).header("Authorization", "Bearer " + obsoletePermissionsToken)
                        .contentType("application/json").content(mapper.writeValueAsBytes(original)))
                .andExpect(status().isUnauthorized());
        String currentToken = tokenService.issue(ownerOne, permissions.resolve(ownerOne.getRole())).accessToken();
        ownerOne.deactivate();
        users.saveAndFlush(ownerOne);
        mvc.perform(post(BASE).header("Authorization", "Bearer " + currentToken)
                        .contentType("application/json").content(mapper.writeValueAsBytes(original)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from appointments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void ownerAndVeterinarianQueriesFilterBeforePaginationAndDenyForeignDetails() throws Exception {
        String ownId = createAppointment();
        String otherId = body(mvc.perform(authenticated(post(BASE), ownerTwo, request(UUID.randomUUID(), petTwo, slotTwo)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        String date = slotOne.getStartsAt().atZone(ZONE).toLocalDate().toString();
        for (User reader : List.of(ownerOne, veterinarianOne)) {
            mvc.perform(authenticated(get(BASE).param("from", date).param("to", date).param("size", "1"), reader))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(ownId));
            mvc.perform(authenticated(get(BASE + "/" + otherId), reader)).andExpect(status().isNotFound());
            mvc.perform(authenticated(get(BASE + "/" + otherId + "/events"), reader)).andExpect(status().isNotFound());
        }
        mvc.perform(authenticated(get(BASE).param("from", date).param("to", date), administrator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        User superAdministrator = account(UserRole.SUPER_ADMIN);
        mvc.perform(authenticated(get(BASE).param("from", date).param("to", date), superAdministrator)).andExpect(status().isForbidden());
        mvc.perform(authenticated(get(BASE + "/" + ownId), superAdministrator)).andExpect(status().isForbidden());
        mvc.perform(authenticated(post(BASE), administrator, request(UUID.randomUUID(), petOne, slotTwo))).andExpect(status().isForbidden());
        mvc.perform(authenticated(patch(BASE + "/" + ownId + "/status"), ownerOne, Map.of("status", "CONFIRMED", "expectedVersion", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerEmailRequiresConfirmationCurrentAssignmentReasonAndAnUnfinishedInterval() throws Exception {
        String id = createAppointment();
        String path = BASE + "/" + id + "/owner-email-accesses";
        var explanation = Map.of("reason", "No hubo respuesta al contacto por WhatsApp.");
        mvc.perform(authenticated(post(path), veterinarianOne, explanation)).andExpect(status().isNotFound());
        mvc.perform(authenticated(patch(BASE + "/" + id + "/status"), administrator, Map.of("status", "CONFIRMED", "expectedVersion", 0)))
                .andExpect(status().isOk());
        for (User actor : List.of(ownerOne, administrator, account(UserRole.SUPER_ADMIN))) {
            mvc.perform(authenticated(post(path), actor, explanation)).andExpect(status().isForbidden());
        }
        mvc.perform(authenticated(post(path), veterinarianTwo, explanation)).andExpect(status().isNotFound());
        mvc.perform(authenticated(post(path), veterinarianOne, Map.of("reason", "   "))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_access_audits where appointment_id=?", Integer.class, UUID.fromString(id))).isZero();
        mvc.perform(authenticated(post(path), veterinarianOne, explanation)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"));
        mvc.perform(authenticated(get(BASE + "/" + id), veterinarianOne)).andExpect(status().isOk()).andExpect(jsonPath("$.email").doesNotExist());
        String visibleHistory = mvc.perform(authenticated(get(BASE + "/" + id + "/events"), ownerOne)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(visibleHistory).doesNotContain("EMAIL_ACCESSED", explanation.get("reason"), ownerOne.getEmail());
        clock.set(slotOne.getEndsAt());
        mvc.perform(authenticated(post(path), veterinarianOne, explanation)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_access_audits where appointment_id=?", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void newRequestsExcludeTodayButAllowTomorrowWithLessThanTwentyFourHoursOfNotice() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        var sameDay = slot(veterinarianOne, today.atTime(23, 0).atZone(ZONE).toInstant(), administrator.getId());
        var tomorrowMorning = slot(veterinarianOne, today.plusDays(1).atTime(8, 0).atZone(ZONE).toInstant(), administrator.getId());
        assertThat(Duration.between(clock.instant(), tomorrowMorning.getStartsAt())).isLessThan(Duration.ofHours(24));
        mvc.perform(authenticated(post(BASE), ownerOne, request(UUID.randomUUID(), petOne, sameDay))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_OUTSIDE_REQUEST_WINDOW"));
        String options = mvc.perform(authenticated(get("/api/v1/availability-slots").param("from", today.toString())
                .param("to", today.plusDays(1).toString()), ownerOne)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(options).doesNotContain(sameDay.getId().toString()).contains(tomorrowMorning.getId().toString());
        mvc.perform(authenticated(post(BASE), ownerOne, request(UUID.randomUUID(), petOne, tomorrowMorning)))
                .andExpect(status().isCreated());
    }

    @Test
    void agreementMetadataRejectsInvalidChannelNumericTimestampsAndMissingOffsets() throws Exception {
        String id = createAppointment();
        disableVeterinarian(veterinarianOne);
        var alternative = slot(veterinarianTwo, slotOne.getStartsAt().plusSeconds(86400), administrator.getId());
        var operation = reassignment(alternative, 1);
        operation.put("agreementChannel", "WHATSAPP");
        operation.put("agreementContactedAt", clock.instant().toString());
        operation.put("agreementNote", "El propietario aceptó el nuevo horario.");
        String path = BASE + "/" + id + "/veterinarian-reassignments";
        for (Object invalidInstant : List.of(clock.instant().getEpochSecond(), "2026-10-04T19:00:00", true)) {
            operation.put("agreementContactedAt", invalidInstant);
            mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isBadRequest());
        }
        operation.put("agreementContactedAt", clock.instant().toString());
        operation.put("agreementChannel", "UNKNOWN");
        mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?", UUID.class, UUID.fromString(id))).isEqualTo(slotOne.getId());
        assertThat(jdbc.queryForObject("select count(*) from appointment_operation_idempotency where appointment_id=?", Integer.class, UUID.fromString(id))).isZero();
    }

    @Test
    void reassignmentReplayReturnsTheResultAfterTheNeedWasResolvedAndConflictingIntentFails() throws Exception {
        String id = createAppointment();
        var operation = reassignment(slotTwo, 0);
        String path = BASE + "/" + id + "/veterinarian-reassignments";
        mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_REASSIGNMENT_NOT_REQUIRED"));
        disableVeterinarian(veterinarianOne);
        operation.put("expectedVersion", 1);
        mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"));
        mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isOk())
                .andExpect(jsonPath("$.veterinarianId").value(veterinarianTwo.getId().toString()));
        operation.put("reason", "Una intención distinta.");
        mvc.perform(authenticated(post(path), administrator, operation)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_IDEMPOTENCY_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='REASSIGNED'", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='OWNER_REASSIGNED'", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    private String createAppointment() throws Exception {
        return body(mvc.perform(authenticated(post(BASE), ownerOne, request(UUID.randomUUID(), petOne, slotOne)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
    }

    private Map<String, Object> reassignment(VeterinarianAvailabilitySlot destination, long version) {
        return new HashMap<>(Map.of("clientRequestId", UUID.randomUUID(), "availabilitySlotId", destination.getId(),
                "expectedAvailabilitySlotVersion", destination.getVersion(), "expectedVersion", version,
                "reason", "Sustitución del profesional deshabilitado."));
    }

    private void disableVeterinarian(User veterinarian) throws Exception {
        mvc.perform(authenticated(patch("/api/v1/veterinarians/" + veterinarian.getId() + "/status"), administrator,
                Map.of("status", "DISABLED"))).andExpect(status().isOk());
        // MockMvc joins this test's transaction; expose JPA changes to the next JDBC-backed request.
        users.flush();
    }

    private void reactivateVeterinarian(User veterinarian) throws Exception {
        mvc.perform(authenticated(patch("/api/v1/veterinarians/" + veterinarian.getId() + "/status"), administrator,
                Map.of("status", "ACTIVE"))).andExpect(status().isOk());
        users.flush();
    }

    private void saveExample(String filename,org.springframework.test.web.servlet.MvcResult result) throws Exception {
        Path directory=Path.of("target","generated-openapi","examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename),mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mapper.readTree(result.getResponse().getContentAsByteArray())));
    }

    private CreateAppointmentRequestDTO request(UUID requestId,Pet pet,VeterinarianAvailabilitySlot slot) {
        return new CreateAppointmentRequestDTO(requestId,pet.getId(),slot.getId(),slot.getVersion(),"Consulta general para la mascota.");
    }
    private User owner(String prefix) {
        User user=users.saveAndFlush(new User(prefix+"-"+UUID.randomUUID()+"@example.test","{argon2id}fixture-only",UserRole.OWNER));
        owners.saveAndFlush(new OwnerProfile(user,"María Gómez",LocalDate.of(1955,5,20),"+573001234567"));
        return user;
    }
    private User veterinarian(String prefix,UUID actorId) {
        User user=users.saveAndFlush(new User(prefix+"-"+UUID.randomUUID()+"@example.test","{argon2id}fixture-only",UserRole.VETERINARIAN));
        VeterinarianProfile profile=veterinarians.saveAndFlush(new VeterinarianProfile(user,"Dra. "+prefix,"+573001234567","MV-"+UUID.randomUUID().toString().substring(0,12),"Perfil de prueba.",actorId,clock.instant()));
        veterinarianQualifications.saveAndFlush(new VeterinarianQualification(profile,QualificationType.UNDERGRADUATE,
                "Medicina veterinaria","Universidad de Ejemplo",2020,true,clock.instant()));
        return user;
    }
    private Pet pet(User owner,String name) {
        OwnerProfile profile=owners.findById(owner.getId()).orElseThrow();
        return pets.saveAndFlush(new Pet(profile,name,PetSpecies.DOG,null,null,null,false,clock.instant()));
    }
    private VeterinarianAvailabilitySlot slot(User veterinarian,Instant start,UUID actorId) {
        return slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(),start,start.plusSeconds(1800),actorId,clock.instant()));
    }
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request,User user,Object body) throws Exception {
        String token=tokenService.issue(user,permissions.resolve(user.getRole())).accessToken();
        return request.header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsBytes(body));
    }
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request,User user) {
        String token=tokenService.issue(user,permissions.resolve(user.getRole())).accessToken();
        return request.header("Authorization","Bearer "+token);
    }
    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private User account(UserRole role) {
        return users.saveAndFlush(new User(role.name().toLowerCase()+"-"+UUID.randomUUID()+"@example.test","{argon2id}fixture-only",role));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AppointmentClockConfiguration {
        @Bean @Primary AppointmentTestClock appointmentTestClock() { return new AppointmentTestClock(); }
    }

    static final class AppointmentTestClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Instant instant() { return current.get(); }
        @Override public Clock withZone(ZoneId requestedZone) {
            return new Clock() {
                @Override public ZoneId getZone() { return requestedZone; }
                @Override public Instant instant() { return current.get(); }
                @Override public Clock withZone(ZoneId nextZone) { return AppointmentTestClock.this.withZone(nextZone); }
            };
        }
    }
}
