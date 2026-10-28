package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.UpdateAppointmentStatusRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.ReassignAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentCutoffScheduler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.CreateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalControlRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalPrescriptionRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalPrescriptionResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalControlResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalTreatmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.FinalizeClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception.ClinicalOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalEncounterService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalTreatmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalFinalizationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.CreatePetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.service.PetService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.UserSignupService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperationCommittedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.CommittedBusinessOperationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mockingDetails;

/** Owns a UUID schema, commits fixtures and observes races/rollback through independent PostgreSQL connections. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00", "spring.datasource.hikari.maximum-pool-size=6"})
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, ClinicalEncounterTransactionIntegrationTests.ClinicalClockConfiguration.class})
@MockitoBean(types = {AppointmentCutoffScheduler.class, AppointmentEmailDispatcher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicalEncounterTransactionIntegrationTests {
    private static final String SCHEMA = "clinical_transactions_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant CURRENT_TIME = Instant.parse("2026-10-05T12:00:00Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    @DynamicPropertySource
    static void isolateCommittedClinicalFixtures(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private ClinicalEncounterService clinicalEncounters;
    @Autowired private ClinicalTreatmentService clinicalTreatments;
    @Autowired private ClinicalFinalizationService clinicalFinalizations;
    @Autowired private AppointmentService appointments;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private PetRepository pets;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @MockitoSpyBean private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MutableClinicalClock clock;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private ApplicationEventPublisher applicationEvents;
    @Autowired private UserSignupService userSignup;
    @Autowired private PetService petService;

    private User administrator;
    private User owner;
    private User veterinarian;
    private Pet pet;
    private VeterinarianAvailabilitySlot appointmentSlot;
    private UUID appointmentId;

    @BeforeEach
    void commitIndependentFixtures() {
        clock.set(CURRENT_TIME);
        inNewTransaction(() -> {
            administrator = account(UserRole.ADMINISTRATOR);
            owner = account(UserRole.OWNER);
            OwnerProfile profile = owners.saveAndFlush(new OwnerProfile(owner, "Propietaria de prueba",
                    LocalDate.of(1955, 5, 20), "+573001234567"));
            pet = pets.saveAndFlush(new Pet(profile, "Luna", PetSpecies.DOG, null, null, null, false, CURRENT_TIME));
            veterinarian = veterinaryAccount();
            appointmentSlot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(),
                    Instant.parse("2026-10-06T15:00:00Z"), Instant.parse("2026-10-06T15:30:00Z"),
                    administrator.getId(), CURRENT_TIME));
            return null;
        });
        appointmentId = confirmedAppointment(owner, pet, appointmentSlot);
    }

    @AfterAll
    void dropOnlyThisOwnedTestSchema() {
        if (!SCHEMA.matches("clinical_transactions_[0-9a-f]{32}")) {
            throw new IllegalStateException("Unexpected test schema identifier.");
        }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void concurrentEquivalentCreationRequestsCommitOneEncounterAndOneInitialAudit() throws Exception {
        CreateClinicalEncounterRequestDTO request = creation(appointmentId);
        List<ClinicalEncounterService.CreationResult> results = raceWithLockedRow("users", owner.getId(),
                () -> clinicalEncounters.create(veterinarian.getId(), request),
                () -> clinicalEncounters.create(veterinarian.getId(), request));
        assertThat(results.stream().map(ClinicalEncounterService.CreationResult::created)).containsExactlyInAnyOrder(true, false);
        UUID encounterId = results.getFirst().encounter().id();
        assertThat(results.getLast().encounter().id()).isEqualTo(encounterId);
        assertDurableAudit(encounterId, 0, 1, 1);
    }

    @Test
    void distinctConcurrentCreationIntentsCannotDuplicateAnAppointmentEncounter() throws Exception {
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> createOutcome(creation(appointmentId)),
                () -> createOutcome(creation(appointmentId)));
        assertThat(results).containsExactlyInAnyOrder("CREATED", "CLINICAL_ENCOUNTER_ALREADY_EXISTS");
        UUID encounterId = jdbc.queryForObject("select id from clinical_encounters where appointment_id=?", UUID.class, appointmentId);
        assertDurableAudit(encounterId, 0, 1, 1);
    }

    @Test
    void oneRequestKeyAcrossDifferentOwnersAppointmentsCannotRacePastIdempotency() throws Exception {
        Object[] second = inNewTransaction(() -> {
            User secondOwner = account(UserRole.OWNER);
            OwnerProfile profile = owners.saveAndFlush(new OwnerProfile(secondOwner, "Otra propietaria de prueba",
                    LocalDate.of(1960, 6, 20), "+573001234567"));
            Pet secondPet = pets.saveAndFlush(new Pet(profile, "Toby", PetSpecies.DOG, null, null, null, false, CURRENT_TIME));
            VeterinarianAvailabilitySlot secondSlot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(),
                    appointmentSlot.getEndsAt(), appointmentSlot.getEndsAt().plusSeconds(1800), administrator.getId(), CURRENT_TIME));
            return new Object[] {secondOwner, secondPet, secondSlot};
        });
        UUID secondAppointmentId = confirmedAppointment((User) second[0], (Pet) second[1], (VeterinarianAvailabilitySlot) second[2]);
        UUID requestKey = UUID.randomUUID();
        List<String> results = raceWithLockedRow("users", veterinarian.getId(),
                () -> createOutcome(new CreateClinicalEncounterRequestDTO(requestKey, appointmentId)),
                () -> createOutcome(new CreateClinicalEncounterRequestDTO(requestKey, secondAppointmentId)));
        assertThat(results).containsExactlyInAnyOrder("CREATED", "CLINICAL_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id in (?,?)",
                Integer.class, appointmentId, secondAppointmentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where actor_id=? and client_request_id=?",
                Integer.class, veterinarian.getId(), requestKey)).isEqualTo(1);
    }

    @Test
    void concurrentDistinctUpdatesPreserveOneCompleteVersionAndOneEffectiveAudit() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> updateOutcome(encounterId, update(UUID.randomUUID(), 0, "Primer diagnóstico")),
                () -> updateOutcome(encounterId, update(UUID.randomUUID(), 0, "Segundo diagnóstico")));
        assertThat(results).containsExactlyInAnyOrder("UPDATED", "CONCURRENT_UPDATE");
        assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId))
                .isIn("Primer diagnóstico", "Segundo diagnóstico");
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void concurrentUpdateReplaysDoNotCreateASecondVersionOrAudit() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        UpdateClinicalEncounterRequestDTO request = update(UUID.randomUUID(), 0, "Diagnóstico compartido");
        var results = raceWithLockedRow("users", owner.getId(),
                () -> clinicalEncounters.update(veterinarian.getId(), encounterId, request),
                () -> clinicalEncounters.update(veterinarian.getId(), encounterId, request));
        assertThat(results).allSatisfy(result -> {
            assertThat(result.id()).isEqualTo(encounterId);
            assertThat(result.version()).isEqualTo(1);
            assertThat(result.diagnosis()).isEqualTo("Diagnóstico compartido");
        });
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void lateCreationFailureRollsBackTheEncounterEventAndIdempotencyTogether() {
        CreateClinicalEncounterRequestDTO request = creation(appointmentId);
        installLateOperationFailure("CREATE");
        try {
            assertThatThrownBy(() -> clinicalEncounters.create(veterinarian.getId(), request)).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where actor_id=?", Integer.class, veterinarian.getId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where actor_id=?", Integer.class, veterinarian.getId())).isZero();
        } finally { removeLateOperationFailure(); }
        var retried = clinicalEncounters.create(veterinarian.getId(), request);
        assertThat(retried.created()).isTrue();
        assertDurableAudit(retried.encounter().id(), 0, 1, 1);
    }

    @Test
    void lateSaveFailureRollsBackFieldsVersionEventAndIdempotencyTogether() {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        UpdateClinicalEncounterRequestDTO request = update(UUID.randomUUID(), 0, "No debe quedar guardado");
        installLateOperationFailure("UPDATE");
        try {
            assertThatThrownBy(() -> clinicalEncounters.update(veterinarian.getId(), encounterId, request)).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId)).isNull();
            assertDurableAudit(encounterId, 0, 1, 1);
        } finally { removeLateOperationFailure(); }
        assertThat(clinicalEncounters.update(veterinarian.getId(), encounterId, request).version()).isEqualTo(1);
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void ownerDeactivationCommittedWhileCreationWaitsPreventsANewEncounter() throws Exception {
        String result = whileRowIsLocked("users", owner.getId(), () -> createOutcome(creation(appointmentId)),
                connection -> updateLockedRow(connection, "update users set status='DISABLED' where id=?", owner.getId()));
        assertThat(result).isEqualTo("CLINICAL_PARTICIPANT_INACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
    }

    @Test
    void petArchivingCommittedWhileSavingWaitsPreventsTheClinicalWrite() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        String result = whileRowIsLocked("pets", pet.getId(), () -> updateOutcome(encounterId, update(UUID.randomUUID(), 0, "No guardar")),
                connection -> updateLockedRow(connection, "update pets set active=false where id=?", pet.getId()));
        assertThat(result).isEqualTo("CLINICAL_PARTICIPANT_INACTIVE");
        assertDurableAudit(encounterId, 0, 1, 1);
    }

    @Test
    void veterinarianDeactivationCommittedWhileCreationWaitsIsRevalidated() throws Exception {
        String result = whileRowIsLocked("users", veterinarian.getId(), () -> createOutcome(creation(appointmentId)),
                connection -> updateLockedRow(connection, "update users set status='DISABLED' where id=?", veterinarian.getId()));
        assertThat(result).isEqualTo("CLINICAL_ACCESS_DENIED");
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
    }

    @Test
    void assignmentChangedWhileSavingWaitsRevokesThePreviousVeterinarian() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        VeterinarianAvailabilitySlot newSlot = inNewTransaction(() -> {
            User replacement = veterinaryAccount();
            return slots.saveAndFlush(new VeterinarianAvailabilitySlot(replacement.getId(), appointmentSlot.getStartsAt(),
                    appointmentSlot.getEndsAt(), administrator.getId(), CURRENT_TIME));
        });
        String result = whileRowIsLocked("appointments", appointmentId,
                () -> updateOutcome(encounterId, update(UUID.randomUUID(), 0, "No guardar")),
                connection -> {
                    try (var statement = connection.prepareStatement("update appointments set current_slot_id=? where id=?")) {
                        statement.setObject(1, newSlot.getId());
                        statement.setObject(2, appointmentId);
                        assertThat(statement.executeUpdate()).isEqualTo(1);
                    }
                });
        assertThat(result).isEqualTo("CLINICAL_ENCOUNTER_NOT_FOUND");
        assertDurableAudit(encounterId, 0, 1, 1);
    }

    @Test
    void concurrentEquivalentTreatmentRequestsCommitOneVersionAndOneSetOfChildren() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        UpdateClinicalTreatmentRequestDTO request = treatment(0, "Tratamiento repetido");
        var results = raceWithLockedRow("users", owner.getId(),
                () -> clinicalTreatments.update(veterinarian.getId(), encounterId, request),
                () -> clinicalTreatments.update(veterinarian.getId(), encounterId, request));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(results.getFirst().version()).isEqualTo(1);
        assertTreatmentChildren(encounterId, "Tratamiento repetido");
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void concurrentDistinctTreatmentRequestsKeepOneCompleteFormulaAndControlSet() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> treatmentOutcome(encounterId, treatment(0, "Tratamiento primero")),
                () -> treatmentOutcome(encounterId, treatment(0, "Tratamiento segundo")));
        assertThat(results).containsExactlyInAnyOrder("TREATMENT_UPDATED", "CONCURRENT_UPDATE");
        String storedName = jdbc.queryForObject("select medication_name from clinical_prescriptions where encounter_id=?", String.class, encounterId);
        assertThat(storedName).isIn("Tratamiento primero", "Tratamiento segundo");
        assertTreatmentChildren(encounterId, storedName);
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void textAndTreatmentWritesShareOneVersionAndCannotBothOverwriteTheSameDraft() throws Exception {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> updateOutcome(encounterId, update(UUID.randomUUID(), 0, "Diagnóstico concurrente")),
                () -> treatmentOutcome(encounterId, treatment(0, "Tratamiento concurrente")));
        assertThat(results).contains("CONCURRENT_UPDATE");
        assertThat(results.stream().filter(result -> result.equals("UPDATED") || result.equals("TREATMENT_UPDATED"))).hasSize(1);
        if (results.contains("UPDATED")) {
            assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId))
                    .isEqualTo("Diagnóstico concurrente");
            assertThat(jdbc.queryForObject("select count(*) from clinical_prescriptions where encounter_id=?", Integer.class, encounterId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_controls where encounter_id=?", Integer.class, encounterId)).isZero();
        } else {
            assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId)).isNull();
            assertTreatmentChildren(encounterId, "Tratamiento concurrente");
        }
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void controlInsertFailureRestoresThePreviousFormulaControlsAndAggregateVersion() {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        clinicalEncounters.update(veterinarian.getId(), encounterId, update(UUID.randomUUID(), 0, "Diagnóstico preservado"));
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento anterior"));
        UpdateClinicalTreatmentRequestDTO replacement = treatment(2, "Tratamiento que debe revertirse");
        jdbc.execute("CREATE FUNCTION reject_test_clinical_control() RETURNS trigger AS $$ BEGIN " +
                "RAISE EXCEPTION 'Controlled clinical child persistence failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_clinical_control_failure BEFORE INSERT ON clinical_controls " +
                "FOR EACH ROW EXECUTE FUNCTION reject_test_clinical_control()");
        try {
            assertThatThrownBy(() -> clinicalTreatments.update(veterinarian.getId(), encounterId, replacement)).isInstanceOf(DataAccessException.class);
            assertTreatmentChildren(encounterId, "Tratamiento anterior");
            assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("Diagnóstico preservado");
            assertDurableAudit(encounterId, 2, 3, 3);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tr_test_clinical_control_failure ON clinical_controls");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_clinical_control()");
        }
        assertThat(clinicalTreatments.update(veterinarian.getId(), encounterId, replacement).version()).isEqualTo(3);
        assertTreatmentChildren(encounterId, "Tratamiento que debe revertirse");
        assertDurableAudit(encounterId, 3, 4, 4);
    }

    @Test
    void lateTreatmentAuditFailureRestoresChildrenVersionTextAndIdempotency() {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        clinicalEncounters.update(veterinarian.getId(), encounterId, update(UUID.randomUUID(), 0, "Diagnóstico preservado"));
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento anterior"));
        UpdateClinicalTreatmentRequestDTO replacement = treatment(2, "Tratamiento siguiente");
        installLateOperationFailure("TREATMENT");
        try {
            assertThatThrownBy(() -> clinicalTreatments.update(veterinarian.getId(), encounterId, replacement)).isInstanceOf(DataAccessException.class);
            assertTreatmentChildren(encounterId, "Tratamiento anterior");
            assertThat(jdbc.queryForObject("select diagnosis from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("Diagnóstico preservado");
            assertDurableAudit(encounterId, 2, 3, 3);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where client_request_id=?", Integer.class,
                    replacement.clientRequestId())).isZero();
        } finally { removeLateOperationFailure(); }
        assertThat(clinicalTreatments.update(veterinarian.getId(), encounterId, replacement).version()).isEqualTo(3);
        assertTreatmentChildren(encounterId, "Tratamiento siguiente");
        assertDurableAudit(encounterId, 3, 4, 4);
    }

    @Test
    void finalizationCommitsTheClinicalRecordAppointmentAuditAndObsoleteNoticesTogether() {
        UUID encounterId = completeDraft();
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento final"));
        int obsoleteNotices = jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and status='PENDING'",
                Integer.class, appointmentId);
        assertThat(obsoleteNotices).isPositive();
        clock.set(appointmentSlot.getStartsAt());
        var finalized = clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalization(2));
        assertThat(finalized.status().name()).isEqualTo("FINALIZED");
        assertThat(finalized.finalizedAt()).isEqualTo(appointmentSlot.getStartsAt());
        assertThat(finalized.createdBy()).isEqualTo(veterinarian.getId());
        assertFinalizedPair(encounterId, 3);
        assertTreatmentChildren(encounterId, "Tratamiento final");
        assertDurableAudit(encounterId, 3, 4, 4);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and status='SUPERSEDED' " +
                "and last_error_code='APPOINTMENT_COMPLETED' and lease_token is null", Integer.class, appointmentId)).isEqualTo(obsoleteNotices);
        assertThat(jdbc.queryForObject("select actor_id from appointment_events where appointment_id=? and event_type='COMPLETED'",
                UUID.class, appointmentId)).isEqualTo(veterinarian.getId());
    }

    @Test
    void concurrentFinalizationReplayCommitsOneClinicalAndAppointmentTransition() throws Exception {
        UUID encounterId = completeDraft();
        clock.set(appointmentSlot.getStartsAt());
        FinalizeClinicalEncounterRequestDTO request = finalization(1);
        var results = raceWithLockedRow("users", owner.getId(),
                () -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request),
                () -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertFinalizedPair(encounterId, 2);
        assertDurableAudit(encounterId, 2, 3, 3);
    }

    @Test
    void concurrentDifferentFinalizationIntentsCannotCreateDuplicateCompletionEvents() throws Exception {
        UUID encounterId = completeDraft();
        clock.set(appointmentSlot.getStartsAt());
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> finalizationOutcome(encounterId, new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), 1L, 1L)),
                () -> finalizationOutcome(encounterId, new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), 1L, 1L)));
        assertThat(results.stream().filter("FINALIZED"::equals)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.equals("FINALIZED")))
                .allSatisfy(result -> assertThat(result).isIn("CLINICAL_APPOINTMENT_NOT_ELIGIBLE", "CLINICAL_ENCOUNTER_FINALIZED", "CONCURRENT_UPDATE"));
        assertFinalizedPair(encounterId, 2);
        assertDurableAudit(encounterId, 2, 3, 3);
    }

    @Test
    void concurrentDraftUpdateAndFinalizationCommitOnlyOneEffectiveMutation() throws Exception {
        UUID encounterId = completeDraft();
        clock.set(appointmentSlot.getStartsAt());
        List<String> results = raceWithLockedRow("users", owner.getId(),
                () -> updateOutcome(encounterId, update(UUID.randomUUID(), 1, "Diagnóstico posterior")),
                () -> finalizationOutcome(encounterId, new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), 1L, 1L)));
        assertThat(results.stream().filter(result -> result.equals("UPDATED") || result.equals("FINALIZED"))).hasSize(1);
        if (results.contains("FINALIZED")) {
            assertFinalizedPair(encounterId, 2);
            assertThat(results).anyMatch(result -> result.equals("CLINICAL_APPOINTMENT_NOT_ELIGIBLE") || result.equals("CLINICAL_ENCOUNTER_FINALIZED"));
        } else {
            assertThat(results).containsExactlyInAnyOrder("UPDATED", "CONCURRENT_UPDATE");
            assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("select status from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='COMPLETED'",
                    Integer.class, appointmentId)).isZero();
        }
        assertDurableAudit(encounterId, 2, 3, 3);
    }

    @Test
    void lateFinalizationOperationFailureRollsBackBothAggregatesAndAllAudit() {
        UUID encounterId = completeDraft();
        clock.set(appointmentSlot.getStartsAt());
        FinalizeClinicalEncounterRequestDTO request = finalization(1);
        var originalNotices = noticeSnapshot();
        installLateOperationFailure("FINALIZE");
        try {
            assertThatThrownBy(() -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request))
                    .isInstanceOf(DataAccessException.class);
            assertUnfinalizedPair(encounterId, 1);
            assertDurableAudit(encounterId, 1, 2, 2);
            assertThat(noticeSnapshot()).isEqualTo(originalNotices);
        } finally { removeLateOperationFailure(); }
        clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request);
        assertFinalizedPair(encounterId, 2);
        assertDurableAudit(encounterId, 2, 3, 3);
    }

    @Test
    void noticeSupersessionFailureRollsBackAlreadyWrittenCompletionAndClinicalEvents() {
        UUID encounterId = completeDraft();
        clock.set(appointmentSlot.getStartsAt());
        FinalizeClinicalEncounterRequestDTO request = finalization(1);
        var originalNotices = noticeSnapshot();
        assertThat(originalNotices).isNotEmpty();
        jdbc.execute("CREATE FUNCTION reject_test_notice_supersession() RETURNS trigger AS $$ BEGIN " +
                "RAISE EXCEPTION 'Controlled notice supersession failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_notice_supersession BEFORE UPDATE ON appointment_email_tasks " +
                "FOR EACH ROW WHEN (NEW.status='SUPERSEDED') EXECUTE FUNCTION reject_test_notice_supersession()");
        try {
            assertThatThrownBy(() -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request))
                    .isInstanceOf(DataAccessException.class);
            assertUnfinalizedPair(encounterId, 1);
            assertDurableAudit(encounterId, 1, 2, 2);
            assertThat(noticeSnapshot()).isEqualTo(originalNotices);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where client_request_id=?",
                    Integer.class, request.clientRequestId())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tr_test_notice_supersession ON appointment_email_tasks");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_notice_supersession()");
        }
        clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request);
        assertFinalizedPair(encounterId, 2);
    }

    @Test
    void deferredConstraintRejectsAppointmentCompletionWithoutAFinalizedEncounterAtCommit() {
        assertDeferredCompletionRejected(() -> inNewTransaction(() -> {
            jdbc.update("update appointments set status='COMPLETED',version=version+1 where id=?", appointmentId);
            // The constraint is deliberately deferred; this statement succeeds inside the transaction.
            assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("COMPLETED");
            return null;
        }));
        assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId)).isEqualTo(1L);
    }

    @Test
    void deferredConstraintRejectsClinicalFinalizationWithoutCompletingTheAppointmentAtCommit() {
        UUID encounterId = completeDraft();
        assertDeferredCompletionRejected(() -> inNewTransaction(() -> {
            jdbc.update("update clinical_encounters set status='FINALIZED',version=version+1,finalized_at=?,updated_at=? where id=?",
                    Timestamp.from(appointmentSlot.getStartsAt()), Timestamp.from(appointmentSlot.getStartsAt()), encounterId);
            assertThat(jdbc.queryForObject("select status from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("FINALIZED");
            return null;
        }));
        assertUnfinalizedPair(encounterId, 1);
        assertDurableAudit(encounterId, 1, 2, 2);
    }

    @Test
    void finalizedClinicalTextFormulaAndControlsAreImmutableEvenThroughDirectSql() {
        UUID encounterId = completeDraft();
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento inmutable"));
        clock.set(appointmentSlot.getStartsAt());
        clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalization(2));
        for (String statement : List.of(
                "update clinical_encounters set reason='Cambio prohibido',version=version+1 where id=?",
                "delete from clinical_encounters where id=?",
                "update clinical_prescriptions set dose=dose+1 where encounter_id=?",
                "delete from clinical_prescriptions where encounter_id=?",
                "update clinical_controls set reason='Cambio prohibido' where encounter_id=?",
                "delete from clinical_controls where encounter_id=?")) {
            assertDatabaseMutationRejected(() -> jdbc.update(statement, encounterId));
        }
        assertTreatmentChildren(encounterId, "Tratamiento inmutable");
        assertFinalizedPair(encounterId, 3);
        assertDurableAudit(encounterId, 3, 4, 4);
    }

    @Test
    void allSevenRealBusinessWritesAreCountedOnlyAfterTheirOuterTransactionCommits() {
        Map<BusinessOperation, Double> before = commitCounts();
        UUID encounterId = inNewTransaction(() -> {
            UUID createdEncounter = performNewOwnerClinicalJourney();
            assertThat(commitCounts()).as("emitted events must not count uncommitted writes").isEqualTo(before);
            return createdEncounter;
        });
        assertEveryCommitDelta(before, 1);
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters e join appointments a on a.id=e.appointment_id " +
                "join pets p on p.id=e.pet_id join owner_profiles o on o.user_id=e.owner_id " +
                "where e.id=? and e.status='FINALIZED' and a.status='COMPLETED' and p.owner_id=o.user_id",
                Integer.class, encounterId)).isEqualTo(1);
    }

    @Test
    void outerRollbackDiscardsAllSevenMetricEventsAsWellAsTheirDatabaseWrites() {
        Map<BusinessOperation, Double> before = commitCounts();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID encounterId = transaction.execute(status -> {
            UUID createdEncounter = performNewOwnerClinicalJourney();
            assertThat(commitCounts()).isEqualTo(before);
            status.setRollbackOnly();
            return createdEncounter;
        });
        assertThat(commitCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where id=?", Integer.class, encounterId)).isZero();
    }

    @Test
    void deferredCommitFailureDoesNotCountAnAlreadyPublishedCreationEvent() {
        Map<BusinessOperation, Double> before = commitCounts();
        assertDeferredCompletionRejected(() -> inNewTransaction(() -> {
            clinicalEncounters.create(veterinarian.getId(), creation(appointmentId));
            jdbc.update("update appointments set status='COMPLETED',version=version+1 where id=?", appointmentId);
            assertThat(commitCounts()).isEqualTo(before);
            return null;
        }));
        assertThat(commitCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
    }

    @Test
    void notificationsWithoutAnActualTransactionCannotFabricateBusinessCommits() {
        Map<BusinessOperation, Double> before = commitCounts();
        for (BusinessOperation operation : BusinessOperation.values()) {
            applicationEvents.publishEvent(new BusinessOperationCommittedEvent(operation));
        }
        assertThat(commitCounts()).isEqualTo(before);
    }

    @Test
    void concurrentClinicalReplaysIncreaseEachEffectiveMutationCounterOnlyOnce() throws Exception {
        Map<BusinessOperation, Double> before = commitCounts();
        CreateClinicalEncounterRequestDTO creation = creation(appointmentId);
        var created = raceWithLockedRow("users", owner.getId(),
                () -> clinicalEncounters.create(veterinarian.getId(), creation),
                () -> clinicalEncounters.create(veterinarian.getId(), creation));
        UUID encounterId = created.getFirst().encounter().id();
        UpdateClinicalEncounterRequestDTO save = update(UUID.randomUUID(), 0, "Diagnóstico para métricas");
        raceWithLockedRow("users", owner.getId(),
                () -> clinicalEncounters.update(veterinarian.getId(), encounterId, save),
                () -> clinicalEncounters.update(veterinarian.getId(), encounterId, save));
        UpdateClinicalTreatmentRequestDTO treatment = treatment(1, "Tratamiento para métricas");
        raceWithLockedRow("users", owner.getId(),
                () -> clinicalTreatments.update(veterinarian.getId(), encounterId, treatment),
                () -> clinicalTreatments.update(veterinarian.getId(), encounterId, treatment));
        clock.set(appointmentSlot.getStartsAt());
        FinalizeClinicalEncounterRequestDTO finalization = finalization(2);
        raceWithLockedRow("users", owner.getId(),
                () -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalization),
                () -> clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalization));
        Map<BusinessOperation, Double> after = commitCounts();
        for (BusinessOperation operation : BusinessOperation.values()) {
            double expected = operation.name().startsWith("CLINICAL_") ? 1 : 0;
            assertThat(after.get(operation) - before.get(operation)).as("committed %s", operation).isEqualTo(expected);
        }
        assertDurableAudit(encounterId, 3, 4, 4);
    }

    @Test
    void concurrentAppointmentReplaysCountOneNewPersistedAppointment() throws Exception {
        VeterinarianAvailabilitySlot nextSlot = inNewTransaction(() -> slots.saveAndFlush(new VeterinarianAvailabilitySlot(
                veterinarian.getId(), appointmentSlot.getEndsAt(), appointmentSlot.getEndsAt().plusSeconds(1800), administrator.getId(), CURRENT_TIME)));
        Map<BusinessOperation, Double> before = commitCounts();
        CreateAppointmentRequestDTO request = new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(), nextSlot.getId(),
                nextSlot.getVersion(), "Consulta para métricas");
        var results = raceWithLockedRow("users", owner.getId(),
                () -> appointments.create(owner.getId(), request),
                () -> appointments.create(owner.getId(), request));
        assertThat(results.stream().map(AppointmentService.AppointmentCreationResult::created)).containsExactlyInAnyOrder(true, false);
        assertThat(commitCounts().get(BusinessOperation.APPOINTMENT_CREATE) - before.get(BusinessOperation.APPOINTMENT_CREATE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointments where client_request_id=?", Integer.class, request.clientRequestId())).isEqualTo(1);
    }

    private UUID performNewOwnerClinicalJourney() {
        var registered = userSignup.signup(new UserSignupRequestDTO("committed-owner-" + UUID.randomUUID() + "@example.test",
                "María Gómez", LocalDate.of(1955, 5, 20), "+573001234567", "Las mascotas ficticias recorren parques tranquilos"));
        CreatePetRequestDTO petRequest = new CreatePetRequestDTO();
        petRequest.setName("Mascota de métricas");
        petRequest.setSpecies(PetSpecies.DOG);
        var createdPet = petService.create(registered.id(), petRequest);
        VeterinarianAvailabilitySlot newSlot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(),
                appointmentSlot.getStartsAt().plusSeconds(3600), appointmentSlot.getEndsAt().plusSeconds(3600), administrator.getId(), CURRENT_TIME));
        UUID newAppointmentId = appointments.create(registered.id(), new CreateAppointmentRequestDTO(UUID.randomUUID(), createdPet.id(),
                newSlot.getId(), newSlot.getVersion(), "Consulta para comprobar commits")).appointment().id();
        appointments.updateStatus(administrator.getId(), newAppointmentId,
                new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(newAppointmentId)).encounter().id();
        clinicalEncounters.update(veterinarian.getId(), encounterId, update(UUID.randomUUID(), 0, "Diagnóstico persistido"));
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento persistido"));
        clock.set(newSlot.getStartsAt());
        clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), 2L, 1L));
        return encounterId;
    }

    private Map<BusinessOperation, Double> commitCounts() {
        Map<BusinessOperation, Double> counts = new EnumMap<>(BusinessOperation.class);
        for (BusinessOperation operation : BusinessOperation.values()) {
            counts.put(operation, meterRegistry.get(CommittedBusinessOperationMetrics.METRIC_NAME).tag("operation", operation.metricTag()).counter().count());
        }
        return counts;
    }

    private void assertEveryCommitDelta(Map<BusinessOperation, Double> before, double expected) {
        Map<BusinessOperation, Double> after = commitCounts();
        for (BusinessOperation operation : BusinessOperation.values()) {
            assertThat(after.get(operation) - before.get(operation)).as("committed %s", operation).isEqualTo(expected);
        }
    }

    @Test
    void parentMutationResponsesMatchCommittedRowsIncludingDatabaseTimestampPrecision() {
        clock.set(CURRENT_TIME.plusNanos(123456789));
        var creation = creation(appointmentId);
        long beforeCreation = preparedOperationInvocations();
        var created = clinicalEncounters.create(veterinarian.getId(), creation).encounter();
        assertThat(preparedOperationInvocations() - beforeCreation).isEqualTo(10);
        assertThat(created).isEqualTo(clinicalEncounters.get(veterinarian.getId(), created.id()));
        assertThat(created.createdAt().getNano() % 1000).isZero();
        assertThat(clinicalEncounters.create(veterinarian.getId(), creation).encounter()).isEqualTo(created);

        clock.set(CURRENT_TIME.plusSeconds(1).plusNanos(987654321));
        var request = update(UUID.randomUUID(), 0, "Diagnóstico Unicode de prueba");
        long beforeUpdate = preparedOperationInvocations();
        var updated = clinicalEncounters.update(veterinarian.getId(), created.id(), request);
        assertThat(preparedOperationInvocations() - beforeUpdate).isEqualTo(10);
        assertThat(updated).isEqualTo(clinicalEncounters.get(veterinarian.getId(), created.id()));
        assertThat(updated.updatedAt().getNano() % 1000).isZero();
        assertThat(clinicalEncounters.update(veterinarian.getId(), created.id(), request)).isEqualTo(updated);

        clock.set(appointmentSlot.getStartsAt().plusNanos(123456789));
        var finalization = finalization(1);
        var finalized = clinicalFinalizations.finalizeEncounter(veterinarian.getId(), created.id(), finalization);
        assertThat(finalized).isEqualTo(clinicalEncounters.get(veterinarian.getId(), created.id()));
        assertThat(finalized.finalizedAt().getNano() % 1000).isZero();
        assertThat(clinicalFinalizations.finalizeEncounter(veterinarian.getId(), created.id(), finalization)).isEqualTo(finalized);
        assertThat(jdbc.queryForObject("select count(*) from clinical_reminder_schedules where encounter_id=?", Integer.class, created.id())).isZero();
    }

    @Test
    void mutationIdentityLookupKeepsRoleValidationBeforeInvalidRequestsOrMissingResources() {
        UUID missingEncounter = UUID.randomUUID();
        assertThatThrownBy(() -> clinicalEncounters.update(owner.getId(), missingEncounter, null))
                .isInstanceOfSatisfying(ClinicalOperationException.class, failure -> assertThat(failure.getStatus().value()).isEqualTo(403));
        assertThatThrownBy(() -> clinicalEncounters.update(veterinarian.getId(), missingEncounter, null))
                .isInstanceOfSatisfying(ClinicalOperationException.class, failure -> assertThat(failure.getStatus().value()).isEqualTo(400));
        assertThatThrownBy(() -> clinicalEncounters.update(veterinarian.getId(), missingEncounter,
                update(UUID.randomUUID(), 0, "No debe guardarse")))
                .isInstanceOfSatisfying(ClinicalOperationException.class, failure -> assertThat(failure.getStatus().value()).isEqualTo(404));
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?", Integer.class, appointmentId)).isZero();
    }

    @Test
    void zeroOneAndFiftyChildrenKeepOrderDatabaseValuesAndBoundedPreparedOperations() {
        UUID encounterId = completeDraft();
        long version = 1;
        for (int[] counts : List.of(new int[] {1, 1}, new int[] {50, 50}, new int[] {50, 0}, new int[] {0, 50}, new int[] {0, 0})) {
            var paired = treatmentWithChildren(version, Math.max(counts[0], counts[1]));
            var request = new UpdateClinicalTreatmentRequestDTO(paired.clientRequestId(), paired.expectedVersion(),
                    paired.prescriptions().subList(0, counts[0]), paired.controls().subList(0, counts[1]));
            long before = preparedOperationInvocations();
            var saved = clinicalTreatments.update(veterinarian.getId(), encounterId, request);
            long operations = preparedOperationInvocations() - before;
            assertThat(operations).isEqualTo(12 + (counts[0] > 0 ? 1 : 0) + (counts[1] > 0 ? 1 : 0));
            assertThat(saved.prescriptions()).extracting(ClinicalPrescriptionResponseDTO::id)
                    .containsExactlyElementsOf(request.prescriptions().stream().map(ClinicalPrescriptionRequestDTO::id).toList());
            assertThat(saved.controls()).extracting(ClinicalControlResponseDTO::id)
                    .containsExactlyElementsOf(request.controls().stream().map(ClinicalControlRequestDTO::id).toList());
            for (var prescription : saved.prescriptions()) {
                assertThat(prescription.dose()).isEqualTo(new BigDecimal("1.000000"));
                assertThat(prescription.dose().scale()).isEqualTo(6);
                assertThat(prescription.startsAt()).isEqualTo(request.prescriptions().getFirst().startsAt().toInstant().truncatedTo(ChronoUnit.MICROS));
            }
            long beforeRead = preparedOperationInvocations();
            assertThat(saved).isEqualTo(clinicalTreatments.get(veterinarian.getId(), encounterId));
            assertThat(preparedOperationInvocations() - beforeRead).isEqualTo(3);
            assertThat(clinicalTreatments.update(veterinarian.getId(), encounterId, request)).isEqualTo(saved);
            version++;
        }
        assertDurableAudit(encounterId, version, 7, 7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENT", "OPERATION"})
    void omittedGroupedAuditRowsRollBackTheParentAndCommitMetrics(String omittedRow) {
        CreateClinicalEncounterRequestDTO request = creation(appointmentId);
        Map<BusinessOperation, Double> before = commitCounts();
        String table = "EVENT".equals(omittedRow) ? "clinical_encounter_events" : "clinical_encounter_operations";
        jdbc.execute("CREATE FUNCTION omit_test_clinical_audit() RETURNS trigger AS $$ BEGIN RETURN NULL; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_omit_clinical_audit BEFORE INSERT ON " + table +
                " FOR EACH ROW EXECUTE FUNCTION omit_test_clinical_audit()");
        try {
            assertThatThrownBy(() -> clinicalEncounters.create(veterinarian.getId(), request))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounters where appointment_id=?",
                    Integer.class, appointmentId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where actor_id=?",
                    Integer.class, veterinarian.getId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where actor_id=? and client_request_id=?",
                    Integer.class, veterinarian.getId(), request.clientRequestId())).isZero();
            assertThat(commitCounts()).isEqualTo(before);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tr_test_omit_clinical_audit ON " + table);
            jdbc.execute("DROP FUNCTION IF EXISTS omit_test_clinical_audit()");
        }
    }

    @Test
    void secondChildGroupFailureRestoresPreviousTreatmentVersionAuditAndCommitMetrics() {
        UUID encounterId = completeDraft();
        var previous = clinicalTreatments.update(veterinarian.getId(), encounterId, treatment(1, "Tratamiento original"));
        Map<BusinessOperation, Double> before = commitCounts();
        jdbc.execute("CREATE FUNCTION reject_test_bulk_control() RETURNS trigger AS $$ BEGIN " +
                "RAISE EXCEPTION 'Controlled second child group failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_bulk_control_failure BEFORE INSERT ON clinical_controls " +
                "FOR EACH ROW EXECUTE FUNCTION reject_test_bulk_control()");
        try {
            assertThatThrownBy(() -> clinicalTreatments.update(veterinarian.getId(), encounterId, treatmentWithChildren(2, 50)))
                    .isInstanceOf(DataAccessException.class);
            assertThat(clinicalTreatments.get(veterinarian.getId(), encounterId)).isEqualTo(previous);
            assertDurableAudit(encounterId, 2, 3, 3);
            assertThat(commitCounts()).isEqualTo(before);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tr_test_bulk_control_failure ON clinical_controls");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_bulk_control()");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "OWNER_INACTIVE", "PET_INACTIVE", "REASSIGNED"})
    void sameIntentDraftReplaysCannotExposeContentAfterEligibilityOrAssignmentIsLost(String change) {
        var creation = creation(appointmentId);
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation).encounter().id();
        var save = update(UUID.randomUUID(), 0, "Diagnóstico del borrador");
        clinicalEncounters.update(veterinarian.getId(), encounterId, save);
        var treatment = treatment(1, "Tratamiento del borrador");
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment);
        String expectedCode;
        if ("CANCELLED".equals(change)) {
            appointments.updateStatus(administrator.getId(), appointmentId,
                    new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CANCELLED, 1L, "Cancelación de ensayo"));
            expectedCode = "CLINICAL_APPOINTMENT_NOT_ELIGIBLE";
        } else if ("REASSIGNED".equals(change)) {
            var replacement = inNewTransaction(() -> {
                User nextVeterinarian = veterinaryAccount();
                return slots.saveAndFlush(new VeterinarianAvailabilitySlot(nextVeterinarian.getId(), appointmentSlot.getStartsAt(),
                        appointmentSlot.getEndsAt(), administrator.getId(), CURRENT_TIME));
            });
            inNewTransaction(() -> {
                jdbc.queryForObject("select id from users where id=? for update", UUID.class, veterinarian.getId());
                jdbc.update("update users set status='DISABLED' where id=?", veterinarian.getId());
                appointments.markVeterinarianDisabled(veterinarian.getId(), administrator.getId());
                return null;
            });
            long appointmentVersion = jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId);
            appointments.reassign(administrator.getId(), appointmentId, new ReassignAppointmentRequestDTO(UUID.randomUUID(),
                    replacement.getId(), replacement.getVersion(), appointmentVersion, "Reasignación de ensayo", null, null, null));
            jdbc.update("update users set status='ACTIVE' where id=?", veterinarian.getId());
            expectedCode = "CLINICAL_ENCOUNTER_NOT_FOUND";
        } else {
            if ("OWNER_INACTIVE".equals(change)) { jdbc.update("update users set status='DISABLED' where id=?", owner.getId()); }
            else { jdbc.update("update pets set active=false where id=?", pet.getId()); }
            expectedCode = "CLINICAL_PARTICIPANT_INACTIVE";
        }
        var before = replaySnapshot(encounterId);
        var replays = List.<Supplier<?>>of(() -> clinicalEncounters.create(veterinarian.getId(), creation),
                () -> clinicalEncounters.update(veterinarian.getId(), encounterId, save),
                () -> clinicalTreatments.update(veterinarian.getId(), encounterId, treatment));
        for (var replay : replays) {
            assertThatThrownBy(replay::get).isInstanceOfSatisfying(ClinicalOperationException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(expectedCode);
                assertThat(failure.getStatus().value()).isEqualTo("REASSIGNED".equals(change) ? 404 : 409);
            });
            assertThat(replaySnapshot(encounterId)).isEqualTo(before);
        }
        if (!"REASSIGNED".equals(change)) {
            assertThatThrownBy(() -> clinicalEncounters.update(veterinarian.getId(), encounterId,
                    update(save.clientRequestId(), 0, "Payload distinto")))
                    .isInstanceOfSatisfying(ClinicalOperationException.class, failure ->
                            assertThat(failure.getErrorCode()).isEqualTo("CLINICAL_IDEMPOTENCY_CONFLICT"));
            assertThat(replaySnapshot(encounterId)).isEqualTo(before);
        }
    }

    @Test
    void originalCreationTextAndTreatmentIntentionsStillReplayAfterFinalization() {
        var creation = creation(appointmentId);
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation).encounter().id();
        var save = update(UUID.randomUUID(), 0, "Diagnóstico publicado");
        clinicalEncounters.update(veterinarian.getId(), encounterId, save);
        var treatment = treatment(1, "Tratamiento publicado");
        clinicalTreatments.update(veterinarian.getId(), encounterId, treatment);
        clock.set(appointmentSlot.getStartsAt());
        var finalized = clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalization(2));
        var before = replaySnapshot(encounterId);

        var replayedCreation = clinicalEncounters.create(veterinarian.getId(), creation);
        assertThat(replayedCreation.created()).isFalse();
        assertThat(replayedCreation.encounter()).isEqualTo(finalized);
        assertThat(clinicalEncounters.update(veterinarian.getId(), encounterId, save)).isEqualTo(finalized);
        assertThat(clinicalTreatments.update(veterinarian.getId(), encounterId, treatment))
                .isEqualTo(clinicalTreatments.get(veterinarian.getId(), encounterId));
        assertThat(replaySnapshot(encounterId)).isEqualTo(before);
        assertDurableAudit(encounterId, 3, 4, 4);
    }

    private Map<String, Object> replaySnapshot(UUID encounterId) {
        return Map.of("encounter", jdbc.queryForMap("select * from clinical_encounters where id=?", encounterId),
                "appointment", jdbc.queryForMap("select * from appointments where id=?", appointmentId),
                "prescriptions", jdbc.queryForList("select * from clinical_prescriptions where encounter_id=? order by position", encounterId),
                "controls", jdbc.queryForList("select * from clinical_controls where encounter_id=? order by position", encounterId),
                "events", jdbc.queryForList("select * from clinical_encounter_events where encounter_id=? order by encounter_version", encounterId),
                "operations", jdbc.queryForList("select * from clinical_encounter_operations where encounter_id=? order by resulting_version", encounterId),
                "metrics", commitCounts());
    }

    private long preparedOperationInvocations() {
        // A single prepared statement can pass through several JdbcTemplate overloads.
        // Count each creator once, not delegating overloads, SQL parameters or wire packets.
        return mockingDetails(jdbc).getInvocations().stream().filter(invocation ->
                List.of("query", "update", "execute").contains(invocation.getMethod().getName())
                        && invocation.getArguments().length > 0 && invocation.getArguments()[0] instanceof PreparedStatementCreator)
                .map(invocation -> invocation.getArguments()[0]).distinct().count();
    }

    private UpdateClinicalTreatmentRequestDTO treatmentWithChildren(long version, int count) {
        List<ClinicalPrescriptionRequestDTO> prescriptions = new ArrayList<>();
        List<ClinicalControlRequestDTO> controls = new ArrayList<>();
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-10-06T09:00:00.123456789-05:00");
        for (int position = 0; position < count; position++) {
            prescriptions.add(new ClinicalPrescriptionRequestDTO(UUID.randomUUID(), "Medicamento " + position, BigDecimal.ONE,
                    "mg", "Oral", 1, startsAt, startsAt.plusDays(1), position % 2 == 0 ? null : "Instrucción " + position));
            controls.add(new ClinicalControlRequestDTO(UUID.randomUUID(), startsAt.plusHours(2), "Control " + position));
        }
        return new UpdateClinicalTreatmentRequestDTO(UUID.randomUUID(), version, prescriptions, controls);
    }

    private UUID completeDraft() {
        UUID encounterId = clinicalEncounters.create(veterinarian.getId(), creation(appointmentId)).encounter().id();
        clinicalEncounters.update(veterinarian.getId(), encounterId, update(UUID.randomUUID(), 0, "Diagnóstico completo"));
        return encounterId;
    }

    private FinalizeClinicalEncounterRequestDTO finalization(long expectedVersion) {
        return new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), expectedVersion,
                jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId));
    }

    private String finalizationOutcome(UUID encounterId, FinalizeClinicalEncounterRequestDTO request) {
        try { clinicalFinalizations.finalizeEncounter(veterinarian.getId(), encounterId, request); return "FINALIZED"; }
        catch (ClinicalOperationException failure) { return failure.getErrorCode(); }
    }

    private List<java.util.Map<String, Object>> noticeSnapshot() {
        return jdbc.queryForList("select id,status,lease_token,last_error_code from appointment_email_tasks where appointment_id=? order by id", appointmentId);
    }

    private void assertFinalizedPair(UUID encounterId, long version) {
        assertThat(jdbc.queryForObject("select status from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("FINALIZED");
        assertThat(jdbc.queryForObject("select version from clinical_encounters where id=?", Long.class, encounterId)).isEqualTo(version);
        assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='COMPLETED'",
                Integer.class, appointmentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where encounter_id=? and event_type='FINALIZED'",
                Integer.class, encounterId)).isEqualTo(1);
    }

    private void assertUnfinalizedPair(UUID encounterId, long version) {
        assertThat(jdbc.queryForObject("select status from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select version from clinical_encounters where id=?", Long.class, encounterId)).isEqualTo(version);
        assertThat(jdbc.queryForObject("select finalized_at from clinical_encounters where id=?", Timestamp.class, encounterId)).isNull();
        assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='COMPLETED'",
                Integer.class, appointmentId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where encounter_id=? and event_type='FINALIZED'",
                Integer.class, encounterId)).isZero();
    }

    private void assertDeferredCompletionRejected(Runnable mutation) {
        assertPostgresFailure(catchThrowable(mutation::run));
    }

    private void assertDatabaseMutationRejected(Runnable mutation) {
        assertPostgresFailure(catchThrowable(() -> inNewTransaction(() -> { mutation.run(); return null; })));
    }

    private void assertPostgresFailure(Throwable failure) {
        assertThat(failure).as("PostgreSQL must reject the inconsistent or immutable write").isNotNull();
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) { cause = cause.getCause(); }
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getSQLState()).isEqualTo("P0001");
    }

    private void assertTreatmentChildren(UUID encounterId, String medicationName) {
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select count(*) from clinical_prescriptions where encounter_id=?", Integer.class, encounterId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from clinical_controls where encounter_id=?", Integer.class, encounterId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select medication_name from clinical_prescriptions where encounter_id=?", String.class, encounterId)).isEqualTo(medicationName);
            assertThat(jdbc.queryForObject("select reason from clinical_controls where encounter_id=?", String.class, encounterId)).isEqualTo("Control " + medicationName);
            return null;
        });
    }

    private void assertDurableAudit(UUID encounterId, long version, int events, int operations) {
        inNewTransaction(() -> {
            assertThat(jdbc.queryForObject("select version from clinical_encounters where id=?", Long.class, encounterId)).isEqualTo(version);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where encounter_id=?", Integer.class, encounterId)).isEqualTo(events);
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where encounter_id=?", Integer.class, encounterId)).isEqualTo(operations);
            return null;
        });
    }

    private void installLateOperationFailure(String operationType) {
        if (!List.of("CREATE", "UPDATE", "TREATMENT", "FINALIZE").contains(operationType)) { throw new IllegalArgumentException("Unexpected operation type."); }
        jdbc.execute("CREATE FUNCTION reject_test_clinical_operation() RETURNS trigger AS $$ BEGIN " +
                "RAISE EXCEPTION 'Controlled late clinical persistence failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_clinical_operation_failure BEFORE INSERT ON clinical_encounter_operations " +
                "FOR EACH ROW WHEN (NEW.operation_type='" + operationType + "') EXECUTE FUNCTION reject_test_clinical_operation()");
    }

    private void removeLateOperationFailure() {
        jdbc.execute("DROP TRIGGER IF EXISTS tr_test_clinical_operation_failure ON clinical_encounter_operations");
        jdbc.execute("DROP FUNCTION IF EXISTS reject_test_clinical_operation()");
    }

    private String createOutcome(CreateClinicalEncounterRequestDTO request) {
        try { return clinicalEncounters.create(veterinarian.getId(), request).created() ? "CREATED" : "REPLAYED"; }
        catch (ClinicalOperationException failure) { return failure.getErrorCode(); }
    }

    private String updateOutcome(UUID encounterId, UpdateClinicalEncounterRequestDTO request) {
        try { clinicalEncounters.update(veterinarian.getId(), encounterId, request); return "UPDATED"; }
        catch (ClinicalOperationException failure) { return failure.getErrorCode(); }
    }

    private String treatmentOutcome(UUID encounterId, UpdateClinicalTreatmentRequestDTO request) {
        try { clinicalTreatments.update(veterinarian.getId(), encounterId, request); return "TREATMENT_UPDATED"; }
        catch (ClinicalOperationException failure) { return failure.getErrorCode(); }
    }

    private <T> List<T> raceWithLockedRow(String table, UUID rowId, Callable<T> first, Callable<T> second) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<T> firstResult;
            Future<T> secondResult;
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    lockRow(blocker, table, rowId);
                    int backendId = backendId(blocker);
                    firstResult = executor.submit(first);
                    secondResult = executor.submit(second);
                    awaitBlockedRequests(backendId, 2);
                    blocker.commit();
                } finally { blocker.rollback(); }
            }
            return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
        }
    }

    private <T> T whileRowIsLocked(String table, UUID rowId, Callable<T> operation, LockedMutation mutation) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<T> result;
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    lockRow(blocker, table, rowId);
                    int backendId = backendId(blocker);
                    result = executor.submit(operation);
                    awaitBlockedRequests(backendId, 1);
                    mutation.apply(blocker);
                    blocker.commit();
                } finally { blocker.rollback(); }
            }
            return result.get(20, TimeUnit.SECONDS);
        }
    }

    private void lockRow(Connection connection, String table, UUID rowId) throws Exception {
        if (!List.of("users", "pets", "appointments").contains(table)) { throw new IllegalArgumentException("Unexpected table."); }
        try (var statement = connection.prepareStatement("select id from " + table + " where id=? for update")) {
            statement.setObject(1, rowId);
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
        }
    }

    private int backendId(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("select pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private void updateLockedRow(Connection connection, String statement, UUID rowId) throws Exception {
        try (var preparedStatement = connection.prepareStatement(statement)) {
            preparedStatement.setObject(1, rowId);
            assertThat(preparedStatement.executeUpdate()).isEqualTo(1);
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

    private UUID confirmedAppointment(User appointmentOwner, Pet appointmentPet, VeterinarianAvailabilitySlot slot) {
        UUID id = appointments.create(appointmentOwner.getId(), new CreateAppointmentRequestDTO(UUID.randomUUID(),
                appointmentPet.getId(), slot.getId(), slot.getVersion(), "Consulta clínica de prueba")).appointment().id();
        appointments.updateStatus(administrator.getId(), id, new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        return id;
    }

    private CreateClinicalEncounterRequestDTO creation(UUID id) { return new CreateClinicalEncounterRequestDTO(UUID.randomUUID(), id); }

    private UpdateClinicalEncounterRequestDTO update(UUID requestId, long version, String diagnosis) {
        return new UpdateClinicalEncounterRequestDTO(requestId, version, "Motivo", "Antecedentes", "Examen", diagnosis, "Recomendaciones");
    }

    private UpdateClinicalTreatmentRequestDTO treatment(long version, String medicationName) {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-10-06T09:00:00-05:00");
        return new UpdateClinicalTreatmentRequestDTO(UUID.randomUUID(), version,
                List.of(new ClinicalPrescriptionRequestDTO(UUID.randomUUID(), medicationName, new BigDecimal("2.500000"),
                        "mg", "Oral", 12, startsAt, startsAt.plusDays(5), "Instrucciones de prueba")),
                List.of(new ClinicalControlRequestDTO(UUID.randomUUID(), startsAt.plusDays(6), "Control " + medicationName)));
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("clinical-transaction-" + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }

    private User veterinaryAccount() {
        User account = account(UserRole.VETERINARIAN);
        veterinarians.saveAndFlush(new VeterinarianProfile(account, "Veterinaria de prueba", "+573001234567",
                "MV-" + account.getId(), "Perfil ficticio", administrator.getId(), CURRENT_TIME));
        return account;
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }

    @FunctionalInterface
    private interface LockedMutation { void apply(Connection connection) throws Exception; }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClinicalClockConfiguration {
        @Bean @Primary MutableClinicalClock clinicalTransactionClock() { return new MutableClinicalClock(); }
    }

    static final class MutableClinicalClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(CURRENT_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Instant instant() { return current.get(); }
        @Override public Clock withZone(ZoneId requestedZone) {
            return new Clock() {
                @Override public ZoneId getZone() { return requestedZone; }
                @Override public Instant instant() { return current.get(); }
                @Override public Clock withZone(ZoneId nextZone) { return MutableClinicalClock.this.withZone(nextZone); }
            };
        }
    }
}
