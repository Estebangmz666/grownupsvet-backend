package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.UpdateAppointmentStatusRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentCutoffScheduler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository.VeterinarianAvailabilitySlotRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalControlRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalPrescriptionRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.CreateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.FinalizeClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalEncounterRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.UpdateClinicalTreatmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterFinalizedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service.ClinicalReminderEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service.ClinicalReminderMaterializer;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service.ClinicalReminderSchedulingService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalEncounterService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalFinalizationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service.ClinicalTreatmentService;
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
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real committed PostgreSQL schedules and controlled MIME transport; no external SMTP is contacted. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.workday-start-time=06:00", "grownupsvet.appointments.sender-address=no-reply@example.test",
        "grownupsvet.appointments.maximum-email-attempts=3", "grownupsvet.clinical.reminders.polling-enabled=false",
        "grownupsvet.clinical.reminders.retry-delay=PT1S", "spring.datasource.hikari.maximum-pool-size=6"})
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, ClinicalReminderIntegrationTests.ReminderTestConfiguration.class})
@MockitoBean(types = {AppointmentCutoffScheduler.class, AppointmentEmailDispatcher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicalReminderIntegrationTests {
    private static final String SCHEMA = "clinical_reminders_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant INITIAL_TIME = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant TREATMENT_START = Instant.parse("2026-10-06T14:00:00Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    @DynamicPropertySource
    static void isolateReminderState(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private PetRepository pets;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @Autowired private AppointmentService appointments;
    @Autowired private ClinicalEncounterService encounters;
    @Autowired private ClinicalTreatmentService treatments;
    @Autowired private ClinicalFinalizationService finalizations;
    @Autowired private ClinicalReminderSchedulingService scheduling;
    @Autowired private ClinicalReminderMaterializer materializer;
    @Autowired private ClinicalReminderEmailDispatcher dispatcher;
    @Autowired private AppointmentProperties appointmentProperties;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private AutowireCapableBeanFactory beanFactory;
    @Autowired private ReminderClock clock;
    @Autowired private CapturingMailSender sender;

    private User owner;
    private User veterinarian;
    private Pet pet;
    private UUID appointmentId;
    private UUID encounterId;
    private FinalizeClinicalEncounterRequestDTO finalizationRequest;

    @BeforeEach
    void commitFictionalDraftAndResetTransport() {
        encounterId = null;
        sender.reset();
        clock.set(INITIAL_TIME);
        User administrator = account(UserRole.ADMINISTRATOR);
        owner = account(UserRole.OWNER);
        OwnerProfile profile = owners.saveAndFlush(new OwnerProfile(owner, "Propietaria ficticia", LocalDate.of(1955, 5, 20), "+573001234567"));
        pet = pets.saveAndFlush(new Pet(profile, "Luna", PetSpecies.DOG, null, null, null, false, INITIAL_TIME));
        veterinarian = account(UserRole.VETERINARIAN);
        veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian, "Dra. Laura Ramírez", "+573001234567",
                "MV-" + UUID.randomUUID().toString().substring(0, 12), "Profesional ficticio", administrator.getId(), INITIAL_TIME));
        VeterinarianAvailabilitySlot slot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(), TREATMENT_START,
                TREATMENT_START.plusSeconds(1800), administrator.getId(), INITIAL_TIME));
        appointmentId = appointments.create(owner.getId(), new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(),
                slot.getId(), slot.getVersion(), "Consulta ficticia")).appointment().id();
        appointments.updateStatus(administrator.getId(), appointmentId,
                new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
        encounterId = encounters.create(veterinarian.getId(), new CreateClinicalEncounterRequestDTO(UUID.randomUUID(), appointmentId)).encounter().id();
        encounters.update(veterinarian.getId(), encounterId, new UpdateClinicalEncounterRequestDTO(UUID.randomUUID(), 0L,
                "Consulta ficticia", "Antecedentes ficticios", "Examen ficticio", "Diagnóstico privado de ensayo", "Plan ficticio"));
        finalizationRequest = new FinalizeClinicalEncounterRequestDTO(UUID.randomUUID(), 2L, 1L);
    }

    @AfterEach
    void retireOnlyThisTestsPendingWork() {
        assertThat(sender.transactionStates).doesNotContain(true);
        if (encounterId != null) {
            jdbc.update("update clinical_reminder_email_tasks set status='SUPERSEDED',lease_token=null,lease_until=null " +
                    "where schedule_id in (select id from clinical_reminder_schedules where encounter_id=?) and status in ('PENDING','SENDING','FAILED')", encounterId);
            jdbc.update("update clinical_reminder_schedules set status='SUPERSEDED',next_due_at=null where encounter_id=? and status='ACTIVE'", encounterId);
        }
        sender.reset();
    }

    @AfterAll
    void dropOnlyTheOwnedReminderSchema() {
        if (!SCHEMA.matches("clinical_reminders_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected test schema identifier."); }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void finalizationCreatesCompactSchedulesInsideItsCommitAndReplayDoesNotDuplicateThem() {
        ClinicalPrescriptionRequestDTO prescription = prescription(TREATMENT_START, TREATMENT_START.plus(Duration.ofDays(366)), 1);
        ClinicalControlRequestDTO control = control(TREATMENT_START.plus(Duration.ofDays(2)));
        finalizeTreatment(List.of(prescription), List.of(control));
        assertThat(scheduleCount()).isEqualTo(2);
        assertThat(taskCount()).isZero();
        finalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalizationRequest);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(new ClinicalEncounterFinalizedEvent(encounterId)));
        assertThat(scheduleCount()).isEqualTo(2);
        assertThat(taskCount()).isZero();
        assertThat(materializer.materializeDue()).isEqualTo(1);
        assertThat(taskCount()).isEqualTo(1);
    }

    @Test
    void lateSchedulingFailureRollsBackEncounterAppointmentEventsAndAllSchedules() {
        treatments.update(veterinarian.getId(), encounterId, new UpdateClinicalTreatmentRequestDTO(UUID.randomUUID(), 1L,
                List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)),
                List.of(control(TREATMENT_START.plus(Duration.ofDays(2))))));
        clock.set(TREATMENT_START);
        jdbc.execute("CREATE FUNCTION fail_test_control_reminder() RETURNS trigger AS $$ BEGIN " +
                "IF NEW.source_type='CONTROL' THEN RAISE EXCEPTION 'Controlled reminder scheduling failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER tr_test_control_reminder BEFORE INSERT ON clinical_reminder_schedules " +
                "FOR EACH ROW EXECUTE FUNCTION fail_test_control_reminder()");
        try {
            assertThatThrownBy(() -> finalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalizationRequest))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select status from clinical_encounters where id=?", String.class, encounterId)).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject("select version from clinical_encounters where id=?", Long.class, encounterId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId)).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, appointmentId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? and event_type='COMPLETED'", Integer.class, appointmentId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_events where encounter_id=? and event_type='FINALIZED'", Integer.class, encounterId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from clinical_encounter_operations where encounter_id=? and operation_type='FINALIZE'", Integer.class, encounterId)).isZero();
            assertThat(scheduleCount()).isZero();
            assertThat(taskCount()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tr_test_control_reminder ON clinical_reminder_schedules");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_test_control_reminder()");
        }
        finalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalizationRequest);
        assertThat(scheduleCount()).isEqualTo(2);
    }

    @Test
    void schedulingRequiresAnExistingTransaction() {
        assertThatThrownBy(() -> scheduling.finalized(new ClinicalEncounterFinalizedEvent(encounterId)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(scheduleCount()).isZero();
    }

    @Test
    void exactDoseOccurrencesAreIdempotentAndStopAtTheExclusiveEnd() {
        Instant startsAt = TREATMENT_START.plusSeconds(3600);
        Instant endsAt = TREATMENT_START.plusSeconds(10800);
        finalizeTreatment(List.of(prescription(startsAt, endsAt, 1)), List.of());
        assertThat(materializer.materializeDue()).isZero();
        clock.set(startsAt);
        assertThat(materializer.materializeDue()).isEqualTo(1);
        assertThat(materializer.materializeDue()).isZero();
        dispatcher.dispatchPending();
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(1);
        clock.set(startsAt.plusSeconds(3600));
        assertThat(materializer.materializeDue()).isEqualTo(1);
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(2);
        clock.set(endsAt);
        assertThat(materializer.materializeDue()).isZero();
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(2);
        assertThat(taskCount()).isEqualTo(2);
        assertThat(schedule("PRESCRIPTION").get("status")).isEqualTo("FINISHED");
        assertThat(sender.transactionStates).containsOnly(false);
    }

    @Test
    void aReconstructedMaterializerSkipsLongDowntimeArithmeticallyWithoutABurst() {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plus(Duration.ofDays(366)), 1)), List.of());
        clock.set(TREATMENT_START.plus(Duration.ofHours(72)).plusSeconds(360));
        ClinicalReminderMaterializer restarted = beanFactory.createBean(ClinicalReminderMaterializer.class);
        try {
            assertThat(restarted.materializeDue()).isZero();
            Map<String, Object> schedule = schedule("PRESCRIPTION");
            assertThat(((Number) schedule.get("missed_count")).longValue()).isEqualTo(73);
            assertThat(((Number) schedule.get("next_occurrence_index")).longValue()).isEqualTo(73);
            assertThat(((Timestamp) schedule.get("next_due_at")).toInstant()).isEqualTo(TREATMENT_START.plus(Duration.ofHours(73)));
            assertThat(taskCount()).isZero();
            clock.set(TREATMENT_START.plus(Duration.ofHours(73)));
            assertThat(restarted.materializeDue()).isEqualTo(1);
            dispatcher.dispatchPending();
            assertThat(sender.messages).hasSize(1);
            assertThat(taskCount()).isEqualTo(1);
        } finally { beanFactory.destroyBean(restarted); }
    }

    @Test
    void controlsUseTwentyFourHoursOrImmediateShortLeadAndNeverSendAfterTheirDate() {
        ClinicalControlRequestDTO longLead = control(TREATMENT_START.plus(Duration.ofHours(48)));
        ClinicalControlRequestDTO shortLead = control(TREATMENT_START.plus(Duration.ofHours(2)));
        ClinicalControlRequestDTO expired = control(TREATMENT_START.minusSeconds(1));
        finalizeTreatment(List.of(), List.of(longLead, shortLead, expired));
        assertThat(controlSchedule(longLead.id()).get("next_due_at")).isEqualTo(Timestamp.from(TREATMENT_START.plus(Duration.ofHours(24))));
        assertThat(controlSchedule(shortLead.id()).get("next_due_at")).isEqualTo(Timestamp.from(TREATMENT_START));
        assertThat(controlSchedule(expired.id()).get("status")).isEqualTo("FINISHED");
        assertThat(materializer.materializeDue()).isEqualTo(1);
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(1);
        clock.set(TREATMENT_START.plus(Duration.ofHours(24)));
        assertThat(materializer.materializeDue()).isEqualTo(1);
        clock.set(TREATMENT_START.plus(Duration.ofHours(48)));
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(1);
        assertThat(taskStatus(taskForControl(longLead.id()))).isEqualTo("EXPIRED");
        assertThat(taskCount()).isEqualTo(2);
    }

    @Test
    void anUnsentDoseCannotStartTransportAtOrAfterTheTreatmentEnd() {
        Instant endsAt = TREATMENT_START.plusSeconds(120);
        finalizeTreatment(List.of(prescription(TREATMENT_START, endsAt, 1)), List.of());
        assertThat(materializer.materializeDue()).isEqualTo(1);
        UUID taskId = onlyTaskId();
        clock.set(endsAt);
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId)).isEqualTo("EXPIRED");
        assertThat(sender.transportAttempts.get()).isZero();
        assertThat(sender.messages).isEmpty();
        assertThat(materializer.materializeDue()).isZero();
    }

    @Test
    void expiryWhileConstructingMimeIsRecheckedImmediatelyBeforeTransport() {
        Instant endsAt = TREATMENT_START.plusSeconds(60);
        finalizeTreatment(List.of(prescription(TREATMENT_START, endsAt, 1)), List.of());
        materializer.materializeDue();
        sender.beforeCreate = () -> clock.set(endsAt);
        dispatcher.dispatchPending();
        assertThat(taskStatus(onlyTaskId())).isEqualTo("EXPIRED");
        assertThat(sender.transportAttempts.get()).isZero();
        assertThat(sender.messages).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "PET"})
    void inactiveParticipantsSupersedePendingOccurrencesAndResumeOnlyFutureDoses(String participant) {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(10800), 1)), List.of());
        materializer.materializeDue();
        UUID previousTask = onlyTaskId();
        setParticipantActive(participant, false);
        dispatcher.dispatchPending();
        assertThat(taskStatus(previousTask)).isEqualTo("SUPERSEDED");
        assertThat(sender.messages).isEmpty();
        clock.set(TREATMENT_START.plusSeconds(120));
        setParticipantActive(participant, true);
        assertThat(materializer.materializeDue()).isZero();
        dispatcher.dispatchPending();
        assertThat(sender.messages).isEmpty();
        clock.set(TREATMENT_START.plusSeconds(3600));
        assertThat(materializer.materializeDue()).isEqualTo(1);
        dispatcher.dispatchPending();
        assertThat(sender.messages).hasSize(1);
        assertThat(taskCount()).isEqualTo(2);
        assertThat(taskStatus(previousTask)).isEqualTo("SUPERSEDED");
    }

    @Test
    void changingTheRecipientAddressDuringMimeConstructionCannotSendToTheOldSnapshot() {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        sender.beforeCreate = () -> jdbc.update("update users set email=? where id=?", "changed-" + UUID.randomUUID() + "@example.test", owner.getId());
        dispatcher.dispatchPending();
        assertThat(taskStatus(onlyTaskId())).isEqualTo("SUPERSEDED");
        assertThat(sender.transportAttempts.get()).isZero();
    }

    @Test
    void concurrentMaterializersCreateOnlyOneTaskForTheSameOccurrence() throws Exception {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return materializer.materializeDue(); });
            var second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return materializer.materializeDue(); });
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(taskCount()).isEqualTo(1);
    }

    @Test
    void concurrentDispatchersDoNotShareAnUnexpiredLease() throws Exception {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        sender.beforeSend = () -> { entered.countDown(); await(release); };
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(dispatcher::dispatchPending);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(taskStatus(onlyTaskId())).isEqualTo("SENDING");
                dispatcher.dispatchPending();
                assertThat(sender.transportAttempts.get()).isEqualTo(1);
            } finally { release.countDown(); }
            first.get(20, TimeUnit.SECONDS);
        }
        assertThat(taskStatus(onlyTaskId())).isEqualTo("SENT");
        assertThat(sender.messages).hasSize(1);
    }

    @Test
    void recoveredLeaseRejectsAStaleAcknowledgementWhileTheNewWorkerStillOwnsTheTask() throws Exception {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        UUID taskId = onlyTaskId();
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch newEntered = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch releaseNew = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        sender.beforeSend = () -> {
            if (sends.incrementAndGet() == 1) { oldEntered.countDown(); await(releaseOld); }
            else { newEntered.countDown(); await(releaseNew); }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var oldWorker = executor.submit(dispatcher::dispatchPending);
            try {
                assertThat(oldEntered.await(10, TimeUnit.SECONDS)).isTrue();
                UUID oldToken = (UUID) task(taskId).get("lease_token");
                jdbc.update("update clinical_reminder_email_tasks set lease_until=? where id=?", Timestamp.from(TREATMENT_START.minusSeconds(1)), taskId);
                var newWorker = executor.submit(dispatcher::dispatchPending);
                assertThat(newEntered.await(10, TimeUnit.SECONDS)).isTrue();
                UUID newToken = (UUID) task(taskId).get("lease_token");
                assertThat(newToken).isNotEqualTo(oldToken);
                releaseOld.countDown();
                oldWorker.get(20, TimeUnit.SECONDS);
                Map<String, Object> inFlight = task(taskId);
                assertThat(inFlight.get("status")).isEqualTo("SENDING");
                assertThat(inFlight.get("lease_token")).isEqualTo(newToken);
                assertThat(((Number) inFlight.get("attempt_count")).intValue()).isEqualTo(2);
                releaseNew.countDown();
                newWorker.get(20, TimeUnit.SECONDS);
            } finally { releaseOld.countDown(); releaseNew.countDown(); }
        }
        assertThat(taskStatus(taskId)).isEqualTo("SENT");
        // An old transport already entered SMTP; lease recovery cannot retract that uncertain send.
        assertThat(sender.messages).hasSize(2);
    }

    @Test
    void smtpFailuresUseBoundedBackoffAndStopAfterTheConfiguredAttemptLimit() {
        finalizeTreatment(List.of(), List.of(control(TREATMENT_START.plus(Duration.ofHours(24)))));
        materializer.materializeDue();
        UUID taskId = onlyTaskId();
        sender.failuresRemaining.set(10);
        for (int attempt = 1; attempt <= appointmentProperties.getMaximumEmailAttempts(); attempt++) {
            dispatcher.dispatchPending();
            Map<String, Object> state = task(taskId);
            assertThat(((Number) state.get("attempt_count")).intValue()).isEqualTo(attempt);
            assertThat((String) state.get("last_error_code")).matches("[A-Z_]+");
            if (attempt < appointmentProperties.getMaximumEmailAttempts()) {
                assertThat(state.get("status")).isEqualTo("PENDING");
                Instant nextAttempt = ((Timestamp) state.get("next_attempt_at")).toInstant();
                assertThat(nextAttempt).isAfter(clock.instant());
                dispatcher.dispatchPending();
                assertThat(sender.transportAttempts.get()).isEqualTo(attempt);
                clock.set(nextAttempt);
            }
        }
        assertThat(taskStatus(taskId)).isEqualTo("FAILED");
        assertThat(task(taskId).get("last_error_code")).isEqualTo("DELIVERY_ATTEMPTS_EXHAUSTED");
        clock.set(clock.instant().plus(Duration.ofHours(1)));
        dispatcher.dispatchPending();
        assertThat(sender.transportAttempts.get()).isEqualTo(appointmentProperties.getMaximumEmailAttempts());
        assertThat(sender.messages).isEmpty();
    }

    @Test
    void expiryStopsRetryingWithoutExtendingTheOriginalDeliveryWindow() {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        UUID taskId = onlyTaskId();
        Instant expiresAt = ((Timestamp) task(taskId).get("expires_at")).toInstant();
        sender.failuresRemaining.set(1);
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId)).isEqualTo("PENDING");
        clock.set(expiresAt);
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId)).isEqualTo("EXPIRED");
        assertThat(((Timestamp) task(taskId).get("expires_at")).toInstant()).isEqualTo(expiresAt);
        assertThat(sender.transportAttempts.get()).isEqualTo(1);
    }

    @Test
    void missingSenderConfigurationDoesNotConsumeAnAttempt() {
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        UUID taskId = onlyTaskId();
        String configured = appointmentProperties.getSenderAddress();
        try {
            appointmentProperties.setSenderAddress("");
            dispatcher.dispatchPending();
            assertThat(taskStatus(taskId)).isEqualTo("PENDING");
            assertThat(((Number) task(taskId).get("attempt_count")).intValue()).isZero();
            assertThat(sender.transportAttempts.get()).isZero();
        } finally { appointmentProperties.setSenderAddress(configured); }
    }

    @Test
    void mimeContainsCorrectEscapedTreatmentDataWithoutClinicalDiagnosisInItsSubject() throws Exception {
        String privatePetName = "Luna <Mía> & \"Familia\"";
        jdbc.update("update pets set name=? where id=?", privatePetName, pet.getId());
        finalizeTreatment(List.of(prescription(TREATMENT_START, TREATMENT_START.plusSeconds(7200), 1)), List.of());
        materializer.materializeDue();
        dispatcher.dispatchPending();
        MimeMessage message = sender.messages.getFirst();
        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collectBodies(message, plain, html);
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(owner.getEmail());
        assertThat(message.getSubject()).doesNotContain("Diagnóstico", "privado", privatePetName);
        assertThat(plain.toString()).contains(privatePetName, "1.25 mL", "America/Bogota", "09:00", "Usar <texto> & \"cuidado\"");
        assertThat(html.toString()).contains("Luna &lt;Mía&gt; &amp;", "&lt;texto&gt;", "&amp;").doesNotContain("<Mía>", "<texto>");
    }

    private void finalizeTreatment(List<ClinicalPrescriptionRequestDTO> prescriptions, List<ClinicalControlRequestDTO> controls) {
        treatments.update(veterinarian.getId(), encounterId, new UpdateClinicalTreatmentRequestDTO(UUID.randomUUID(), 1L, prescriptions, controls));
        clock.set(TREATMENT_START);
        finalizations.finalizeEncounter(veterinarian.getId(), encounterId, finalizationRequest);
    }

    private ClinicalPrescriptionRequestDTO prescription(Instant startsAt, Instant endsAt, int intervalHours) {
        return new ClinicalPrescriptionRequestDTO(UUID.randomUUID(), "Medicamento ficticio", new BigDecimal("1.25"), "mL", "Oral",
                intervalHours, OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC), OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC),
                "Usar <texto> & \"cuidado\"");
    }

    private ClinicalControlRequestDTO control(Instant when) {
        return new ClinicalControlRequestDTO(UUID.randomUUID(), OffsetDateTime.ofInstant(when, ZoneOffset.UTC), "Control ficticio");
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("reminder-" + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }

    private int scheduleCount() {
        return jdbc.queryForObject("select count(*) from clinical_reminder_schedules where encounter_id=?", Integer.class, encounterId);
    }

    private int taskCount() {
        return jdbc.queryForObject("select count(*) from clinical_reminder_email_tasks t join clinical_reminder_schedules s on s.id=t.schedule_id where s.encounter_id=?",
                Integer.class, encounterId);
    }

    private UUID onlyTaskId() {
        return jdbc.queryForObject("select t.id from clinical_reminder_email_tasks t join clinical_reminder_schedules s on s.id=t.schedule_id where s.encounter_id=?",
                UUID.class, encounterId);
    }

    private UUID taskForControl(UUID controlId) {
        return jdbc.queryForObject("select t.id from clinical_reminder_email_tasks t join clinical_reminder_schedules s on s.id=t.schedule_id " +
                "where s.encounter_id=? and s.control_id=?", UUID.class, encounterId, controlId);
    }

    private Map<String, Object> task(UUID taskId) {
        return jdbc.queryForMap("select t.* from clinical_reminder_email_tasks t join clinical_reminder_schedules s on s.id=t.schedule_id " +
                "where t.id=? and s.encounter_id=?", taskId, encounterId);
    }

    private String taskStatus(UUID taskId) { return (String) task(taskId).get("status"); }

    private Map<String, Object> schedule(String sourceType) {
        return jdbc.queryForMap("select * from clinical_reminder_schedules where encounter_id=? and source_type=?", encounterId, sourceType);
    }

    private Map<String, Object> controlSchedule(UUID controlId) {
        return jdbc.queryForMap("select * from clinical_reminder_schedules where encounter_id=? and control_id=?", encounterId, controlId);
    }

    private void setParticipantActive(String participant, boolean active) {
        if ("OWNER".equals(participant)) { jdbc.update("update users set status=? where id=?", active ? "ACTIVE" : "DISABLED", owner.getId()); }
        else { jdbc.update("update pets set active=? where id=?", active, pet.getId()); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("Controlled transport coordination timed out."); }
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("Controlled transport interrupted.", failure); }
    }

    private static void collectBodies(Part part, StringBuilder plain, StringBuilder html) throws Exception {
        if (part.isMimeType("text/plain")) { plain.append(part.getContent()); }
        else if (part.isMimeType("text/html")) { html.append(part.getContent()); }
        else if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) { collectBodies(multipart.getBodyPart(index), plain, html); }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReminderTestConfiguration {
        @Bean @Primary ReminderClock reminderClock() { return new ReminderClock(); }
        @Bean @Primary CapturingMailSender capturingMailSender() { return new CapturingMailSender(); }
    }

    static class CapturingMailSender extends JavaMailSenderImpl {
        final CopyOnWriteArrayList<MimeMessage> messages = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Boolean> transactionStates = new CopyOnWriteArrayList<>();
        final AtomicInteger transportAttempts = new AtomicInteger();
        final AtomicInteger failuresRemaining = new AtomicInteger();
        volatile Runnable beforeCreate = () -> { };
        volatile Runnable beforeSend = () -> { };

        @Override public MimeMessage createMimeMessage() {
            MimeMessage message = super.createMimeMessage();
            beforeCreate.run();
            return message;
        }

        @Override public void send(MimeMessage message) throws MailException { capture(message); }
        @Override public void send(MimeMessage... mimeMessages) throws MailException { for (MimeMessage message : mimeMessages) { capture(message); } }

        private void capture(MimeMessage message) {
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            transportAttempts.incrementAndGet();
            beforeSend.run();
            if (failuresRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) { throw new MailSendException("Controlled private transport failure"); }
            try { message.saveChanges(); messages.add(new MimeMessage(message)); }
            catch (Exception failure) { throw new MailSendException("Controlled MIME capture failure", failure); }
        }

        void reset() {
            beforeCreate = () -> { }; beforeSend = () -> { };
            messages.clear(); transactionStates.clear(); transportAttempts.set(0); failuresRemaining.set(0);
        }
    }

    static final class ReminderClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_TIME);
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Instant instant() { return current.get(); }
        @Override public Clock withZone(ZoneId zone) {
            return new Clock() {
                @Override public ZoneId getZone() { return zone; }
                @Override public Instant instant() { return current.get(); }
                @Override public Clock withZone(ZoneId nextZone) { return ReminderClock.this.withZone(nextZone); }
            };
        }
    }
}
