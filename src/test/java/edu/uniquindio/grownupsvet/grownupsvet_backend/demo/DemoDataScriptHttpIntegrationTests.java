package edu.uniquindio.grownupsvet.grownupsvet_backend.demo;

import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentCutoffScheduler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service.AppointmentEmailDispatcher;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.AdministratorProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.AdministratorProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianQualificationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual PowerShell helper, HTTP security and committed PostgreSQL data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false",
        "grownupsvet.security.password-recovery.enabled=false", "grownupsvet.appointments.sender-address=",
        "grownupsvet.appointments.workday-start-time=06:00"})
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
@MockitoBean(types = {AppointmentEmailDispatcher.class, AppointmentCutoffScheduler.class})
@EnabledIf(value = "powerShellAvailable", disabledReason = "PowerShell 7 is absent from PATH/POWERSHELL_EXECUTABLE; only the demo script integration tests are skipped.")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoDataScriptHttpIntegrationTests {
    private static final String SCHEMA = "demo_script_" + UUID.randomUUID().toString().replace("-", "");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    private static final String OWNER_PASSWORD = "las mascotas ficticias caminan por el parque";
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DemoDataScriptHttpIntegrationTests::testDatabaseUrl);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private AdministratorProfileRepository administrators;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private VeterinarianQualificationRepository qualifications;
    @Autowired private JwtTokenService tokenService;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JsonMapper mapper;
    @Autowired private Clock clock;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private User administrator;
    private User veterinarian;
    private OffsetDateTime firstSlotStartsAt;

    @BeforeEach
    void commitOnlyAdministrativePreconditions() {
        firstSlotStartsAt = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(2).atTime(14, 0)
                .atZone(BUSINESS_ZONE).toOffsetDateTime();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            administrator = users.saveAndFlush(new User("demo-admin-" + UUID.randomUUID() + "@example.test",
                    "{argon2id}fixture-only", UserRole.ADMINISTRATOR));
            administrators.saveAndFlush(new AdministratorProfile(administrator, "Administradora de Prueba",
                    administrator.getId(), clock.instant()));
            veterinarian = users.saveAndFlush(new User("demo-veterinarian-" + UUID.randomUUID() + "@example.test",
                    "{argon2id}fixture-only", UserRole.VETERINARIAN));
            VeterinarianProfile profile = veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian,
                    "Veterinaria de Prueba", "+573001234567", "MV-" + UUID.randomUUID(), "Datos ficticios.",
                    administrator.getId(), clock.instant()));
            qualifications.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                    "Medicina Veterinaria", "Universidad Ficticia", 2020, true, clock.instant()));
        });
    }

    @AfterAll
    void removeOnlyTheOwnedSchema() {
        http.close();
        if (!SCHEMA.matches("demo_script_[0-9a-f]{32}")) { throw new IllegalStateException("Unexpected demo test schema."); }
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void realScriptCreatesOwnersPetsAndAppointmentsWithoutLeakingSecretsOrSendingMail() throws Exception {
        String administratorToken = tokenService.issue(administrator, permissions.resolve(administrator.getRole())).accessToken();
        ScriptExecutionResult execution = runDemoScript(administratorToken);
        assertPrivateOutput(execution, administratorToken);
        assertThat(execution.exitCode()).as("The demo helper must complete successfully over real HTTP").isZero();
        JsonNode report = parsePublicReport(execution.stdout());
        assertThat(report.path("Success").asBoolean()).isTrue();
        assertThat(report.has("MailtrapCaptureVerified")).isTrue();
        assertThat(report.path("MailtrapCaptureVerified").asBoolean()).isFalse();
        assertThat(report.path("RunId").asText()).isNotBlank();
        assertThat(report.path("Owners").size()).isEqualTo(2);
        assertThat(report.path("Slots").size()).isEqualTo(2);
        assertThat(report.path("Appointments").size()).isEqualTo(2);
        assertThat(report.path("Checks").isEmpty()).isFalse();
        for (JsonNode check : report.path("Checks")) {
            assertThat(check.has("passed") && check.has("actualStatus") && check.has("expectedStatus")).isTrue();
            assertThat(check.path("passed").asBoolean()).as("Every reported HTTP expectation must pass").isTrue();
            assertThat(check.path("actualStatus").asInt()).isEqualTo(check.path("expectedStatus").asInt());
        }
        assertThat(report.path("Checks")).anySatisfy(check -> {
            assertThat(check.path("name").asText()).isEqualTo("UnauthenticatedProfileDenied");
            assertThat(check.path("expectedStatus").asInt()).isEqualTo(401);
            assertThat(check.path("actualStatus").asInt()).isEqualTo(401);
            assertThat(check.path("passed").asBoolean()).isTrue();
        });
        assertThat(report.path("Checks").findValues("actualStatus")).anySatisfy(value -> assertThat(value.asInt()).isEqualTo(409));

        Map<UUID, String> ownerTokens = new HashMap<>();
        for (JsonNode owner : report.path("Owners")) {
            UUID ownerId = UUID.fromString(owner.path("id").asText());
            UUID petId = UUID.fromString(owner.path("petId").asText());
            assertThat(jdbc.queryForObject("select role from users where id=?", String.class, ownerId)).isEqualTo("OWNER");
            assertThat(jdbc.queryForObject("select email from users where id=?", String.class, ownerId)).isEqualTo(owner.path("email").asText());
            assertThat(jdbc.queryForObject("select owner_id from pets where id=? and active=true", UUID.class, petId)).isEqualTo(ownerId);
            ownerTokens.put(ownerId, loginOwner(owner.path("email").asText()));
        }
        assertThat(ownerTokens).hasSize(2);
        for (JsonNode slot : report.path("Slots")) {
            UUID slotId = UUID.fromString(slot.path("id").asText());
            assertThat(jdbc.queryForObject("select veterinarian_id from veterinarian_availability_slots where id=?",
                    UUID.class, slotId)).isEqualTo(veterinarian.getId());
            assertThat(jdbc.queryForObject("select ends_at-starts_at=interval '30 minutes' from veterinarian_availability_slots where id=?",
                    Boolean.class, slotId)).isTrue();
        }

        Map<String, UUID> appointmentIds = new HashMap<>();
        for (JsonNode appointment : report.path("Appointments")) {
            UUID id = UUID.fromString(appointment.path("id").asText());
            UUID ownerId = UUID.fromString(appointment.path("ownerId").asText());
            String status = appointment.path("status").asText();
            appointmentIds.put(status, id);
            assertThat(jdbc.queryForObject("select owner_id from appointments where id=?", UUID.class, id)).isEqualTo(ownerId);
            assertThat(jdbc.queryForObject("select status from appointments where id=?", String.class, id)).isEqualTo(status);
            assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, id))
                    .isEqualTo(appointment.path("version").asLong());
            JsonNode history = readAuthenticated("/api/v1/appointments/" + id + "/events", ownerTokens.get(ownerId));
            assertThat(history.path("totalElements").asLong()).isEqualTo(jdbc.queryForObject(
                    "select count(*) from appointment_events where appointment_id=?", Long.class, id));
            JsonNode notifications = readAuthenticated("/api/v1/appointments/" + id + "/notifications", ownerTokens.get(ownerId));
            assertThat(notifications.path("totalElements").asLong()).isEqualTo(jdbc.queryForObject(
                    "select count(*) from appointment_email_tasks where appointment_id=? and recipient_id=?", Long.class, id, ownerId));
            assertThat(notifications.path("items").isEmpty()).isFalse();
            for (JsonNode notification : notifications.path("items")) {
                assertThat(notification.path("attemptCount").asInt()).isZero();
                assertThat(notification.path("sentAt").isNull()).isTrue();
                assertThat(notification.path("status").asText()).isIn("PENDING", "SUPERSEDED");
                assertThat(notification.has("recipientEmail") || notification.has("payload")).isFalse();
            }
        }
        assertThat(appointmentIds.keySet()).containsExactlyInAnyOrder("CANCELLED", "CONFIRMED");
        UUID cancelled = appointmentIds.get("CANCELLED");
        UUID confirmed = appointmentIds.get("CONFIRMED");
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, cancelled)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("select version from appointments where id=?", Long.class, confirmed)).isEqualTo(1L);
        assertThat(jdbc.queryForList("select event_type from appointment_events where appointment_id=? order by appointment_version",
                String.class, cancelled)).containsExactly("REQUESTED", "CONFIRMED", "RESCHEDULED", "CANCELLED");
        assertThat(jdbc.queryForList("select event_type from appointment_events where appointment_id=? order by appointment_version",
                String.class, confirmed)).containsExactly("REQUESTED", "CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from appointment_assignments where appointment_id=?", Integer.class, cancelled)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from appointment_operation_idempotency where appointment_id=? " +
                "and operation_type='RESCHEDULING'", Integer.class, cancelled)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select s.starts_at from appointments a join veterinarian_availability_slots s " +
                "on s.id=a.current_slot_id where a.id=?", Timestamp.class, confirmed).toInstant()).isEqualTo(firstSlotStartsAt.toInstant());
        assertThat(jdbc.queryForObject("select count(*) from appointments where id in (?,?) and status in ('REQUESTED','CONFIRMED')",
                Integer.class, cancelled, confirmed)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_email_tasks where appointment_id in (?,?) " +
                "and (attempt_count<>0 or sent_at is not null)", Integer.class, cancelled, confirmed)).isZero();
        assertSavedPublicReport(report, execution, administratorToken);
    }

    @Test
    void authenticVeterinarianTokenCannotCreateDemoDataThroughAdministrativeRoutes() throws Exception {
        long ownersBefore = jdbc.queryForObject("select count(*) from users where role='OWNER'", Long.class);
        long slotsBefore = jdbc.queryForObject("select count(*) from veterinarian_availability_slots", Long.class);
        long appointmentsBefore = jdbc.queryForObject("select count(*) from appointments", Long.class);
        String veterinarianToken = tokenService.issue(veterinarian, permissions.resolve(veterinarian.getRole())).accessToken();
        ScriptExecutionResult execution = runDemoScript(veterinarianToken);
        assertPrivateOutput(execution, veterinarianToken);
        assertThat(execution.exitCode()).as("A veterinarian cannot impersonate the administrator").isNotZero();
        JsonNode report = parsePublicReport(execution.stdout());
        assertThat(report.has("Success") && report.has("MailtrapCaptureVerified")).isTrue();
        assertThat(report.path("Success").asBoolean()).isFalse();
        assertThat(report.path("MailtrapCaptureVerified").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from users where role='OWNER'", Long.class)).isEqualTo(ownersBefore);
        assertThat(jdbc.queryForObject("select count(*) from veterinarian_availability_slots", Long.class)).isEqualTo(slotsBefore);
        assertThat(jdbc.queryForObject("select count(*) from appointments", Long.class)).isEqualTo(appointmentsBefore);
        assertSavedPublicReport(report, execution, veterinarianToken);
    }

    private ScriptExecutionResult runDemoScript(String token) throws Exception {
        Path script = PROJECT_ROOT.resolve("scripts/create-demo-data.ps1");
        assertThat(Files.isRegularFile(script)).as("The real demo helper must exist").isTrue();
        String command = """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
                $administratorToken = ConvertTo-SecureString $env:GROWNUPSVET_DEMO_TEST_TOKEN -AsPlainText -Force
                $ownerPassword = ConvertTo-SecureString $env:GROWNUPSVET_DEMO_TEST_PASSWORD -AsPlainText -Force
                $firstStart = [DateTimeOffset]::Parse($env:GROWNUPSVET_DEMO_TEST_FIRST_START, [Globalization.CultureInfo]::InvariantCulture)
                & $env:GROWNUPSVET_DEMO_TEST_SCRIPT -ApiBaseUrl $env:GROWNUPSVET_DEMO_TEST_API -AdministratorAccessToken $administratorToken -VeterinarianId ([Guid]$env:GROWNUPSVET_DEMO_TEST_VETERINARIAN) -FirstSlotStartsAt $firstStart -OwnerPassword $ownerPassword
                """;
        ProcessBuilder builder = new ProcessBuilder(findPowerShell().toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command);
        builder.directory(PROJECT_ROOT.toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        for (String name : List.of("PATH", "SystemRoot", "WINDIR", "TEMP", "TMP", "HOME", "USERPROFILE")) {
            String value = System.getenv(name);
            if (value != null) { environment.put(name, value); }
        }
        environment.put("GROWNUPSVET_DEMO_TEST_TOKEN", token);
        environment.put("GROWNUPSVET_DEMO_TEST_PASSWORD", OWNER_PASSWORD);
        environment.put("GROWNUPSVET_DEMO_TEST_FIRST_START", firstSlotStartsAt.toString());
        environment.put("GROWNUPSVET_DEMO_TEST_SCRIPT", script.toString());
        environment.put("GROWNUPSVET_DEMO_TEST_API", "http://127.0.0.1:" + port);
        environment.put("GROWNUPSVET_DEMO_TEST_VETERINARIAN", veterinarian.getId().toString());
        assertThat(builder.command().stream().anyMatch(value -> value.contains(token) || value.contains(OWNER_PASSWORD)))
                .as("Sensitive values must not appear in child command arguments").isFalse();
        Process process = builder.start();
        process.getOutputStream().close();
        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var stdout = readers.submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            var stderr = readers.submit(() -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("The demo helper exceeded its bounded execution time.");
            }
            return new ScriptExecutionResult(process.exitValue(), stdout.get(10, TimeUnit.SECONDS), stderr.get(10, TimeUnit.SECONDS));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
    }

    private void assertPrivateOutput(ScriptExecutionResult result, String token) {
        boolean leaks = result.stdout().contains(token) || result.stderr().contains(token)
                || result.stdout().contains(OWNER_PASSWORD) || result.stderr().contains(OWNER_PASSWORD)
                || JWT_PATTERN.matcher(result.stdout()).find() || JWT_PATTERN.matcher(result.stderr()).find();
        assertThat(leaks).as("stdout/stderr must never contain passwords or Bearer tokens").isFalse();
    }

    private JsonNode parsePublicReport(String json) {
        try (var parser = mapper.createParser(json)) {
            JsonNode report = mapper.readTree(parser);
            if (report == null || parser.nextToken() != null) { throw new AssertionError("The helper must emit only one public JSON object."); }
            return report;
        }
        catch (JacksonException failure) { throw new AssertionError("The helper must emit a single valid public JSON report."); }
    }

    private void assertSavedPublicReport(JsonNode report, ScriptExecutionResult result, String token) throws IOException {
        Path reportPath = Path.of(report.path("ReportPath").asText()).toAbsolutePath().normalize();
        assertThat(reportPath.startsWith(PROJECT_ROOT.resolve("target/demo-data")))
                .as("The generated report must stay within its owned target directory").isTrue();
        assertThat(Files.isRegularFile(reportPath)).isTrue();
        String savedReport = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertPrivateOutput(new ScriptExecutionResult(result.exitCode(), savedReport, ""), token);
        assertThat(parsePublicReport(savedReport).path("RunId").asText()).isEqualTo(report.path("RunId").asText());
    }

    private String loginOwner(String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/sessions"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("email", email, "password", OWNER_PASSWORD))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("The generated owner must be able to log in through real HTTP").isEqualTo(200);
        String token = parsePublicReport(response.body()).path("accessToken").asText();
        assertThat(token.isBlank()).isFalse();
        return token;
    }

    private JsonNode readAuthenticated(String path, String token) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("Own history and notifications must remain accessible").isEqualTo(200);
        return parsePublicReport(response.body());
    }

    static boolean powerShellAvailable() { return findPowerShell() != null; }

    private static String testDatabaseUrl() {
        String url = System.getProperty("demo.test.database.url", System.getenv("SPRING_DATASOURCE_URL"));
        if (url == null || url.isBlank()) { return "jdbc:postgresql://localhost:5432/grownupsvet_test"; }
        try {
            if (!url.startsWith("jdbc:postgresql://") || !"/grownupsvet_test".equals(URI.create(url.substring("jdbc:".length())).getPath())) {
                throw new IllegalArgumentException();
            }
            return url;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Demo HTTP tests require grownupsvet_test; development and production database destinations are rejected.");
        }
    }

    private static Path findPowerShell() {
        String configured = System.getProperty("demo.powershell.executable", System.getenv("POWERSHELL_EXECUTABLE"));
        if (configured != null && !configured.isBlank()) {
            Path executable = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(executable) && Files.isExecutable(executable) ? executable : null;
        }
        String path = System.getenv("PATH");
        if (path == null) { return null; }
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "pwsh.exe" : "pwsh";
        for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
            if (!directory.isBlank()) {
                Path executable = Path.of(directory).resolve(name).toAbsolutePath().normalize();
                if (Files.isRegularFile(executable) && Files.isExecutable(executable)) { return executable; }
            }
        }
        return null;
    }

    private record ScriptExecutionResult(int exitCode, String stdout, String stderr) { }
}
