package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.RescheduleAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.RevealOwnerEmailRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.CreateAppointmentRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto.UpdateAppointmentStatusRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model.AppointmentNotificationsQueuedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentNotificationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration.AppointmentProperties;
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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real committed PostgreSQL queue operations; SMTP is a controlled fake and never contacts a server. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.appointments.email-poll-delay-millis=3600000",
        "grownupsvet.appointments.cutoff-poll-delay-millis=3600000",
        "grownupsvet.frontend.base-url=https://frontend.example.test/demo",
        "grownupsvet.appointments.sender-address=no-reply@example.test"})
@ActiveProfiles("test")
@Import({TestJwtKeyConfiguration.class, AppointmentEmailDispatcherIntegrationTests.ControlledClockConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentEmailDispatcherIntegrationTests {
    private static final String SCHEMA = "appointment_email_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2026-09-28T14:00:00Z");
    private static final Instant START = Instant.parse("2026-09-29T14:00:00Z");

    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private AppointmentEmailDispatcher dispatcher;
    @Autowired private AppointmentService appointments;
    @Autowired private AppointmentNotificationService notifications;
    @Autowired private AppointmentProperties appointmentProperties;
    @Autowired private JsonMapper mapper;
    @Autowired private MutableNotificationClock clock;
    @Autowired private UserRepository users;
    @Autowired private OwnerProfileRepository owners;
    @Autowired private PetRepository pets;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private VeterinarianAvailabilitySlotRepository slots;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean(name = "appointmentEmailExecutor") private ThreadPoolTaskExecutor executor;
    private final List<Runnable> submitted = new ArrayList<>();
    private final List<String> delivered = Collections.synchronizedList(new ArrayList<>());
    private TransactionTemplate transactions;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        transactions = new TransactionTemplate(transactionManager);
        reset(executor, mailSender);
        submitted.clear(); delivered.clear();
        doAnswer(invocation -> { submitted.add(invocation.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        successfulSmtp();
        fixture = transactions.execute(status -> {
            User administrator = account(UserRole.ADMINISTRATOR);
            User owner = account(UserRole.OWNER);
            OwnerProfile ownerProfile = owners.saveAndFlush(new OwnerProfile(owner, "María Gómez", LocalDate.of(1955, 5, 20), "+573001234567"));
            Pet pet = pets.saveAndFlush(new Pet(ownerProfile, "Luna", PetSpecies.DOG, null, null, null, false, NOW));
            User veterinarian = account(UserRole.VETERINARIAN);
            veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian, "Doctora Primera", "+573001234567",
                    "MV-" + UUID.randomUUID(), "Perfil ficticio.", administrator.getId(), NOW));
            VeterinarianAvailabilitySlot slot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(), START,
                    START.plusSeconds(1800), administrator.getId(), NOW));
            UUID appointment = UUID.randomUUID();
            jdbc.update("insert into appointments(id,owner_id,pet_id,current_slot_id,status,assignment_status,reason,starts_at,ends_at," +
                            "confirmation_cutoff_at,client_request_id,request_fingerprint,version,created_at,updated_at) " +
                            "values(?,?,?,?,'CONFIRMED','ASSIGNED','Consulta ficticia',?,?,?,?,'fixture',1,?,?)",
                    appointment, owner.getId(), pet.getId(), slot.getId(), timestamp(START), timestamp(START.plusSeconds(1800)),
                    timestamp(START.minusSeconds(3600)), UUID.randomUUID(), timestamp(NOW), timestamp(NOW));
            insertReassignmentEvent(appointment, slot.getId(), administrator.getId(), 1);
            return new Fixture(appointment, owner.getId(), owner.getEmail(), veterinarian.getId(), administrator.getId(), slot.getId());
        });
    }

    @AfterEach
    void removeOnlyMutableTasksFromThisIsolatedSchema() { jdbc.update("delete from appointment_email_tasks"); }

    @AfterAll
    void dropOnlyOwnedSchema() {
        if (!SCHEMA.matches("appointment_email_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected test schema."); }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void dispatchIsSubmittedOnlyAfterCommitAndRollbackDoesNotSend() {
        transactions.executeWithoutResult(status -> {
            queueOwner(1);
            events.publishEvent(new AppointmentNotificationsQueuedEvent(fixture.appointmentId()));
            assertThat(submitted).isEmpty();
            status.setRollbackOnly();
        });
        assertThat(submitted).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks", Integer.class)).isZero();
        UUID[] task = new UUID[1];
        transactions.executeWithoutResult(status -> {
            task[0] = queueOwner(1);
            events.publishEvent(new AppointmentNotificationsQueuedEvent(fixture.appointmentId()));
            assertThat(submitted).isEmpty();
        });
        assertThat(submitted).hasSize(1);
        verifyNoInteractions(mailSender);
        submitted.getFirst().run();
        assertThat(taskStatus(task[0])).isEqualTo("SENT");
        assertThat(delivered).hasSize(1);
    }

    @Test
    void failedOlderNoticeIsSupersededAfterNewerAssignmentEvenWhenTheSlotIsTheSame() {
        UUID older = queueOwner(1);
        doThrow(new MailSendException("Simulated failure")).when(mailSender).send(any(MimeMessage.class));
        dispatcher.dispatchPending();
        assertThat(taskStatus(older)).isEqualTo("PENDING");
        transactions.executeWithoutResult(status -> {
            jdbc.update("update appointments set version=2 where id=?", fixture.appointmentId());
            insertReassignmentEvent(fixture.appointmentId(), fixture.slotId(), fixture.administratorId(), 2);
        });
        UUID newer = queueOwner(2);
        successfulSmtp();
        dispatcher.dispatchPending();
        assertThat(taskStatus(newer)).isEqualTo("SENT");
        clock.set(NOW.plusSeconds(61));
        dispatcher.dispatchPending();
        assertThat(taskStatus(older)).isEqualTo("SUPERSEDED");
        assertThat(delivered).hasSize(1);
    }

    @Test
    void reschedulingWithTheSameVeterinarianAndReturningToTheOriginalSlotSupersedesAnOlderNotice() {
        UUID currentTask = queueOwner(1);
        UUID legacyTask = queue("OWNER_REASSIGNED", fixture.ownerId(), fixture.ownerEmail(), Map.of(
                "petName", "Luna", "veterinarianName", "Doctora Primera", "startsAt", START.toString()));
        VeterinarianAvailabilitySlot destination = transactions.execute(status -> slots.saveAndFlush(
                new VeterinarianAvailabilitySlot(fixture.veterinarianId(), START.plusSeconds(3600),
                        START.plusSeconds(5400), fixture.administratorId(), NOW)));
        appointments.reschedule(fixture.administratorId(), fixture.appointmentId(),
                rescheduling(destination.getId(), destination.getVersion(), 1L));
        appointments.reschedule(fixture.administratorId(), fixture.appointmentId(),
                rescheduling(fixture.slotId(), 0L, 2L));

        assertThat(jdbc.queryForObject("select current_slot_id from appointments where id=?", UUID.class,
                fixture.appointmentId())).isEqualTo(fixture.slotId());
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=?",
                Integer.class, fixture.appointmentId())).isEqualTo(6);
        dispatcher.dispatchPending();
        assertThat(taskStatus(currentTask)).isEqualTo("SUPERSEDED");
        assertThat(taskStatus(legacyTask)).isEqualTo("SUPERSEDED");
        assertThat(delivered).hasSize(2);
    }

    @Test
    void auditedOwnerEmailAccessDoesNotSupersedeTheCurrentAssignmentNotice() {
        UUID task = queueOwner(1);
        assertThat(appointments.revealOwnerEmail(fixture.veterinarianId(), fixture.appointmentId(),
                new RevealOwnerEmailRequestDTO("Contacto previo a la cita confirmada.")).email())
                .isEqualTo(fixture.ownerEmail());
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class,
                fixture.appointmentId())).isEqualTo(2L);

        dispatcher.dispatchPending();
        assertThat(taskStatus(task)).isEqualTo("SENT");
        assertThat(delivered).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_events where appointment_id=? " +
                "and event_type='EMAIL_ACCESSED'", Integer.class, fixture.appointmentId())).isEqualTo(1);
    }

    @Test
    void cancelledAppointmentsDoNotProduceStaleAdministratorRequests() {
        UUID task = queueAdministrator();
        jdbc.update("update appointments set assignment_status='NEEDS_REASSIGNMENT' where id=?", fixture.appointmentId());
        jdbc.update("update users set status='DISABLED' where id=?", fixture.veterinarianId());
        jdbc.update("update appointments set status='CANCELLED' where id=?", fixture.appointmentId());
        dispatcher.dispatchPending();
        assertThat(taskStatus(task)).isEqualTo("SUPERSEDED");
        verifyNoInteractions(mailSender);
    }

    @Test
    void restoredVeterinarianDoesNotProduceAStaleAdministratorRequest() {
        UUID task = queueAdministrator();
        // The original disable notice remains queued after the assignment was restored.
        dispatcher.dispatchPending();
        assertThat(taskStatus(task)).isEqualTo("SUPERSEDED");
        verifyNoInteractions(mailSender);
    }

    @Test
    void activeLeaseBlocksTheNextNoticeForTheSameAppointment() throws Exception {
        UUID task = queueOwner(1);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            delivered.add("first");
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        try (var workers = Executors.newSingleThreadExecutor()) {
            var first = workers.submit(dispatcher::dispatchPending);
            try {
                assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
                UUID next = queueOwner(1);
                dispatcher.dispatchPending();
                assertThat(taskStatus(task)).isEqualTo("SENDING");
                assertThat(taskStatus(next)).isEqualTo("PENDING");
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void expiredWorkerCannotOverwriteTheReplacementLeaseResult() throws Exception {
        UUID task = queueOwner(1);
        CountDownLatch firstSending = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch replacementSending = new CountDownLatch(1);
        CountDownLatch releaseReplacement = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                firstSending.countDown();
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                return null;
            }
            replacementSending.countDown();
            assertThat(releaseReplacement.await(10, TimeUnit.SECONDS)).isTrue();
            throw new MailSendException("Replacement worker simulated failure");
        }).when(mailSender).send(any(MimeMessage.class));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(dispatcher::dispatchPending);
            try {
                assertThat(firstSending.await(10, TimeUnit.SECONDS)).isTrue();
                clock.set(NOW.plusSeconds(121));
                var replacement = workers.submit(dispatcher::dispatchPending);
                assertThat(replacementSending.await(10, TimeUnit.SECONDS)).isTrue();
                UUID replacementToken = jdbc.queryForObject("select lease_token from appointment_email_tasks where id=?", UUID.class, task);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                assertThat(taskStatus(task)).isEqualTo("SENDING");
                assertThat(jdbc.queryForObject("select lease_token from appointment_email_tasks where id=?", UUID.class, task)).isEqualTo(replacementToken);
                assertThat(jdbc.queryForObject("select attempt_count from appointment_email_tasks where id=?", Integer.class, task)).isEqualTo(2);
                releaseReplacement.countDown();
                replacement.get(10, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
                releaseReplacement.countDown();
            }
        }
        assertThat(taskStatus(task)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select last_error_code from appointment_email_tasks where id=?", String.class, task)).isEqualTo("SMTP_DELIVERY_FAILED");
    }

    @Test
    void confirmationCreatesOneDurableNoticeAndFutureReminderAndReplayCreatesNeitherAgain() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        var confirmation = new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null);
        appointments.updateStatus(fixture.administratorId(), target.id(), confirmation);
        appointments.updateStatus(fixture.administratorId(), target.id(), confirmation);
        assertThat(taskCount(target.id(), "OWNER_CONFIRMED")).isEqualTo(1);
        assertThat(taskCount(target.id(), "OWNER_REMINDER")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks t join appointment_events e on e.id=t.event_key " +
                "where t.appointment_id=? and e.event_type='CONFIRMED'", Integer.class, target.id())).isEqualTo(2);
        dispatcher.dispatchPending();
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst()).contains("confirmada", "https://frontend.example.test/demo");
        UUID reminder = taskId(target.id(), "OWNER_REMINDER");
        assertThat(taskStatus(reminder)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempt_count from appointment_email_tasks where id=?", Integer.class, reminder)).isZero();
    }

    @Test
    void exactTwentyFourHourBoundarySchedulesReminderButLateConfirmationDoesNot() {
        clock.set(NOW.minusSeconds(60));
        NotificationAppointment before = createRequestedAt(START);
        NotificationAppointment exact = createRequestedAt(START);
        NotificationAppointment late = createRequestedAt(START);
        clock.set(NOW.minusNanos(1000));
        confirm(before.id());
        clock.set(NOW);
        confirm(exact.id());
        clock.set(NOW.plusNanos(1000));
        confirm(late.id());
        assertThat(taskCount(before.id(), "OWNER_REMINDER")).isEqualTo(1);
        assertThat(taskCount(exact.id(), "OWNER_REMINDER")).isEqualTo(1);
        assertThat(taskCount(late.id(), "OWNER_REMINDER")).isZero();
        assertThat(taskCount(late.id(), "OWNER_CONFIRMED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select scheduled_at from appointment_email_tasks where appointment_id=? and task_type='OWNER_REMINDER'",
                java.sql.Timestamp.class, exact.id()).toInstant()).isEqualTo(NOW);
    }

    @Test
    void scheduledReminderSurvivesInactivityAndSendsOnlyBeforeTheAppointmentStarts() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        dispatcher.dispatchPending();
        UUID reminder = taskId(target.id(), "OWNER_REMINDER");
        clock.set(target.startsAt().minusSeconds(3600));
        dispatcher.dispatchPending();
        assertThat(taskStatus(reminder)).isEqualTo("SENT");
        assertThat(delivered).hasSize(2);
        assertThat(delivered.getLast()).contains("Recuerda", "confirmada");
        dispatcher.dispatchPending();
        assertThat(delivered).hasSize(2);

        clock.set(NOW);
        NotificationAppointment expired = createRequestedAt(START.plusSeconds(172800));
        confirm(expired.id());
        dispatcher.dispatchPending();
        int previouslyDelivered = delivered.size();
        clock.set(expired.startsAt());
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(expired.id(), "OWNER_REMINDER"))).isEqualTo("SUPERSEDED");
        assertThat(delivered).hasSize(previouslyDelivered);
    }

    @Test
    void reminderRetryKeepsItsOriginalScheduleAndUsesANewAttemptTime() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        dispatcher.dispatchPending();
        UUID reminder = taskId(target.id(), "OWNER_REMINDER");
        Instant scheduled = target.startsAt().minusSeconds(86400);
        clock.set(scheduled);
        doThrow(new MailSendException("Controlled SMTP failure")).when(mailSender).send(any(MimeMessage.class));
        dispatcher.dispatchPending();
        assertThat(taskStatus(reminder)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select scheduled_at from appointment_email_tasks where id=?", java.sql.Timestamp.class, reminder).toInstant()).isEqualTo(scheduled);
        assertThat(jdbc.queryForObject("select next_attempt_at from appointment_email_tasks where id=?", java.sql.Timestamp.class, reminder).toInstant()).isEqualTo(scheduled.plusSeconds(60));
        clock.set(scheduled.plusSeconds(60));
        successfulSmtp();
        dispatcher.dispatchPending();
        assertThat(taskStatus(reminder)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select attempt_count from appointment_email_tasks where id=?", Integer.class, reminder)).isEqualTo(2);
    }

    @Test
    void disabledOwnerReceivesCancellationAndTheConfirmedReminder() {
        NotificationAppointment cancellation = createRequestedAt(START.plusSeconds(86400));
        NotificationAppointment reminder = createRequestedAt(START.plusSeconds(172800));
        confirm(cancellation.id());
        confirm(reminder.id());
        dispatcher.dispatchPending();
        jdbc.update("update users set status='DISABLED' where id=?", fixture.ownerId());
        appointments.updateStatus(fixture.administratorId(), cancellation.id(),
                new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CANCELLED, 1L, "Cancelación acordada."));
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(cancellation.id(), "OWNER_CANCELLED"))).isEqualTo("SENT");
        assertThat(taskStatus(taskId(cancellation.id(), "OWNER_REMINDER"))).isEqualTo("SUPERSEDED");
        clock.set(reminder.startsAt().minusSeconds(86400));
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(reminder.id(), "OWNER_REMINDER"))).isEqualTo("SENT");
    }

    @Test
    void rejectionAndDailyCutoffDeliverTerminalNoticesWithoutRevivingTheAppointment() {
        NotificationAppointment rejected = createRequestedAt(START.plusSeconds(86400));
        var rejection = new UpdateAppointmentStatusRequestDTO(AppointmentStatus.REJECTED, 0L, "No hay capacidad.");
        appointments.updateStatus(fixture.administratorId(), rejected.id(), rejection);
        appointments.updateStatus(fixture.administratorId(), rejected.id(), rejection);
        dispatcher.dispatchPending();
        assertThat(taskCount(rejected.id(), "OWNER_REJECTED")).isEqualTo(1);
        assertThat(taskStatus(taskId(rejected.id(), "OWNER_REJECTED"))).isEqualTo("SENT");
        assertThat(delivered.getLast()).contains("rechazada");

        NotificationAppointment cancelled = createRequestedAt(START.plusSeconds(172800));
        Instant cutoff = jdbc.queryForObject("select confirmation_cutoff_at from appointments where id=?",
                java.sql.Timestamp.class, cancelled.id()).toInstant();
        clock.set(cutoff);
        assertThat(appointments.expirePendingAppointments()).isGreaterThanOrEqualTo(1);
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(cancelled.id(), "OWNER_CANCELLED"))).isEqualTo("SENT");
        assertThat(delivered.getLast()).contains("cancelada");
        assertThat(taskCount(cancelled.id(), "OWNER_REMINDER")).isZero();
    }

    @Test
    void changingAConfirmedScheduleSupersedesThePreviousReminderAndRecordsOnlyTheNewIntent() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        dispatcher.dispatchPending();
        UUID older = taskId(target.id(), "OWNER_REMINDER");
        var destination = transactions.execute(status -> slots.saveAndFlush(new VeterinarianAvailabilitySlot(target.veterinarianId(),
                target.startsAt().plusSeconds(86400), target.startsAt().plusSeconds(88200), fixture.administratorId(), clock.instant())));
        RescheduleAppointmentRequestDTO change = rescheduling(destination.getId(), 0L, 1L);
        appointments.reschedule(fixture.administratorId(), target.id(), change);
        appointments.reschedule(fixture.administratorId(), target.id(), change);
        assertThat(taskStatus(older)).isEqualTo("SUPERSEDED");
        assertThat(taskCount(target.id(), "OWNER_RESCHEDULED")).isEqualTo(1);
        assertThat(taskCount(target.id(), "OWNER_REMINDER")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type='OWNER_REMINDER' and status='PENDING'",
                Integer.class, target.id())).isEqualTo(1);
        dispatcher.dispatchPending();
        assertThat(delivered).hasSize(2);
        assertThat(delivered.getLast()).contains("actualizada");
    }

    @Test
    void emailAccessAuditDoesNotSupersedeTheScheduledReminder() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        dispatcher.dispatchPending();
        appointments.revealOwnerEmail(target.veterinarianId(), target.id(), new RevealOwnerEmailRequestDTO("Contacto previo sin respuesta."));
        clock.set(target.startsAt().minusSeconds(86400));
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(target.id(), "OWNER_REMINDER"))).isEqualTo("SENT");
        assertThat(delivered).hasSize(2);
    }

    @Test
    void rolledBackBusinessTransitionCannotLeaveANoticeOrScheduledReminder() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        transactions.executeWithoutResult(status -> {
            confirm(target.id());
            assertThat(taskCount(target.id(), "OWNER_CONFIRMED")).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(taskCount(target.id(), "OWNER_CONFIRMED")).isZero();
        assertThat(taskCount(target.id(), "OWNER_REMINDER")).isZero();
        assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, target.id())).isEqualTo("REQUESTED");
        dispatcher.dispatchPending();
        verifyNoInteractions(mailSender);
    }

    @Test
    void eventRecipientTypeUniquenessSurvivesRepeatedQueueingOfTheSamePersistedEvent() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        transactions.executeWithoutResult(status -> notifications.recordEvent(target.id(), 1L, "CONFIRMED", NOW));
        transactions.executeWithoutResult(status -> notifications.recordEvent(target.id(), 1L, "CONFIRMED", NOW));
        assertThat(taskCount(target.id(), "OWNER_CONFIRMED")).isEqualTo(1);
        assertThat(taskCount(target.id(), "OWNER_REMINDER")).isEqualTo(1);
    }

    @Test
    void absentSenderConfigurationDoesNotSpendDurableDeliveryAttempts() {
        NotificationAppointment target = createRequestedAt(START.plusSeconds(86400));
        confirm(target.id());
        String configured = appointmentProperties.getSenderAddress();
        try {
            appointmentProperties.setSenderAddress("");
            dispatcher.dispatchPending();
            assertThat(jdbc.queryForObject("select sum(attempt_count) from appointment_email_tasks where appointment_id=?", Integer.class, target.id())).isZero();
            verifyNoInteractions(mailSender);
        } finally { appointmentProperties.setSenderAddress(configured); }
        dispatcher.dispatchPending();
        assertThat(taskStatus(taskId(target.id(), "OWNER_CONFIRMED"))).isEqualTo("SENT");
    }

    private NotificationAppointment createRequestedAt(Instant startsAt) {
        return transactions.execute(status -> {
            User owner = users.findById(fixture.ownerId()).orElseThrow();
            OwnerProfile profile = owners.findById(owner.getId()).orElseThrow();
            Pet pet = pets.saveAndFlush(new Pet(profile, "Mascota de aviso", PetSpecies.DOG, null, null, null, false, clock.instant()));
            User veterinarian = account(UserRole.VETERINARIAN);
            veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian, "Veterinario de avisos", "+573001234567",
                    "MV-" + UUID.randomUUID(), "Perfil ficticio", fixture.administratorId(), clock.instant()));
            var slot = slots.saveAndFlush(new VeterinarianAvailabilitySlot(veterinarian.getId(), startsAt, startsAt.plusSeconds(1800),
                    fixture.administratorId(), clock.instant()));
            UUID id = appointments.create(owner.getId(), new CreateAppointmentRequestDTO(UUID.randomUUID(), pet.getId(), slot.getId(), 0L, "Consulta general.")).appointment().id();
            return new NotificationAppointment(id, veterinarian.getId(), slot.getId(), startsAt);
        });
    }

    private void confirm(UUID id) {
        appointments.updateStatus(fixture.administratorId(), id, new UpdateAppointmentStatusRequestDTO(AppointmentStatus.CONFIRMED, 0L, null));
    }

    private int taskCount(UUID id, String type) {
        return jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id=? and task_type=?", Integer.class, id, type);
    }

    private UUID taskId(UUID id, String type) {
        return jdbc.queryForObject("select id from appointment_email_tasks where appointment_id=? and task_type=? order by created_at,id limit 1", UUID.class, id, type);
    }

    private record NotificationAppointment(UUID id, UUID veterinarianId, UUID slotId, Instant startsAt) { }

    private UUID queueOwner(long assignmentVersion) {
        return queue("OWNER_REASSIGNED", fixture.ownerId(), fixture.ownerEmail(), Map.of(
                "petName", "Luna", "veterinarianName", "Doctora Primera", "startsAt", START.toString(),
                "availabilitySlotId", fixture.slotId().toString(), "assignmentVersion", assignmentVersion));
    }

    private RescheduleAppointmentRequestDTO rescheduling(UUID destinationId, long slotVersion, long appointmentVersion) {
        return new RescheduleAppointmentRequestDTO(UUID.randomUUID(), destinationId, slotVersion, appointmentVersion,
                "Reprogramación acordada.", "PHONE", NOW, "La propietaria confirmó el nuevo horario.");
    }

    private UUID queueAdministrator() {
        return queue("ADMIN_REASSIGNMENT", fixture.administratorId(), "administrator@example.test",
                Map.of("veterinarianId", fixture.veterinarianId().toString(), "veterinarianName", "Doctora Primera", "appointmentCount", 1));
    }

    private UUID queue(String type, UUID recipientId, String email, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into appointment_email_tasks(id,appointment_id,event_key,recipient_id,recipient_email,task_type,payload," +
                        "status,attempt_count,scheduled_at,next_attempt_at,created_at) values(?,?,?,?,?,?,cast(? as jsonb),'PENDING',0,?,?,?)",
                id, fixture.appointmentId(), UUID.randomUUID(), recipientId, email, type, mapper.writeValueAsString(payload),
                timestamp(clock.instant()), timestamp(clock.instant()), timestamp(clock.instant()));
        return id;
    }

    private void insertReassignmentEvent(UUID appointment, UUID slot, UUID actor, long version) {
        jdbc.update("insert into appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status," +
                        "previous_assignment_status,new_assignment_status,previous_slot_id,new_slot_id,actor_id,actor_type,occurred_at,reason) " +
                        "values(?,?,?,'REASSIGNED','CONFIRMED','CONFIRMED','ASSIGNED','ASSIGNED',?,?,?,'HUMAN',?,'Cambio ficticio')",
                UUID.randomUUID(), appointment, version, slot, slot, actor, timestamp(clock.instant()));
    }

    private void successfulSmtp() {
        doAnswer(invocation -> {
            delivered.add((String) ((MimeMessage) invocation.getArgument(0)).getContent());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
    }
    private String taskStatus(UUID id) { return jdbc.queryForObject("select status from appointment_email_tasks where id=?", String.class, id); }
    private static java.sql.Timestamp timestamp(Instant instant) { return java.sql.Timestamp.from(instant); }
    private record Fixture(UUID appointmentId, UUID ownerId, String ownerEmail, UUID veterinarianId, UUID administratorId, UUID slotId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledClockConfiguration {
        @Bean @Primary MutableNotificationClock notificationClock() { return new MutableNotificationClock(); }
    }
    static class MutableNotificationClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(NOW);
        void set(Instant value) { current.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current.get(), zone); }
        @Override public Instant instant() { return current.get(); }
    }
}
