package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.GrownupsvetBackendApplication;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Real servlet startup, Flyway and Hibernate validation; owns only UUID schemas in the test database. */
@ActiveProfiles("test")
class SchemaStartupValidationIntegrationTests {
    private static final int LATEST_REVIEWED_MIGRATION_VERSION = 23;
    private static final String TEST_DATABASE_URL = "jdbc:postgresql://localhost:5432/grownupsvet_test";
    private static final String SCHEMA_PREFIX = "startup_validation_";
    private static final Path ORIGINAL_MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
    private final List<String> ownedSchemas = new ArrayList<>();
    @TempDir private Path temporaryDirectory;
    private Map<String, String> originalMigrationDigests;
    private DriverManagerDataSource database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareAnExplicitTestDatabaseAndFingerprintOriginalMigrations() throws Exception {
        originalMigrationDigests = migrationDigests();
        assertThat(originalMigrationDigests.size()).as("the reviewed increment contains all expected migration inputs").isEqualTo(LATEST_REVIEWED_MIGRATION_VERSION);
        StandardEnvironment environment = new StandardEnvironment();
        database = new DriverManagerDataSource(TEST_DATABASE_URL,
                requiredCredential(environment, "spring.datasource.username", "DB_USERNAME"),
                requiredCredential(environment, "spring.datasource.password", "DB_PASSWORD"));
        jdbc = new JdbcTemplate(database);
        assertTestDatabase();
    }

    @AfterEach
    void removeOnlyOwnedSchemasAndVerifyOriginalMigrationInputs() throws Exception {
        try {
            if (jdbc != null) {
                assertTestDatabase();
                for (String schema : ownedSchemas) {
                    requireOwnedSchema(schema);
                    jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        } finally {
            if (originalMigrationDigests != null) {
                assertThat(migrationDigests().equals(originalMigrationDigests))
                        .as("original migration files and their SHA-256 digests must remain unchanged").isTrue();
            }
        }
    }

    @Test
    void startsWithAllReviewedMigrationsAndHibernateSchemaValidation() {
        String schema = ownSchema();
        try (ConfigurableApplicationContext context = start(schema, "classpath:db/migration")) {
            assertThat(context.isActive()).as("the coherent servlet application must start").isTrue();
            assertThat(context.getEnvironment().getProperty("local.server.port", Integer.class, 0) > 0)
                    .as("the servlet server must bind its randomly assigned test port").isTrue();
            assertThat("validate".equals(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")))
                    .as("Hibernate must validate rather than create or update the schema").isTrue();
            assertThat(context.getBean(EntityManagerFactory.class).isOpen())
                    .as("the real persistence unit must initialize successfully").isTrue();
            assertThat(context.getBean(Flyway.class).validateWithResult().validationSuccessful)
                    .as("the real Flyway history must validate").isTrue();
            assertAllReviewedMigrations(schema);
            assertThat(hasEmailColumn(schema)).as("the coherent model column must exist").isTrue();
        }
    }

    @Test
    void rejectsStartupWhenATemporaryMigrationCopyChangesAnAppliedChecksum() throws Exception {
        String schema = ownSchema();
        migrateIsolatedSchema(schema);
        List<Map<String, Object>> originalHistory = migrationHistory(schema);
        Path copiedMigrations = temporaryDirectory.resolve("changed-migrations");
        Files.createDirectory(copiedMigrations);
        for (String filename : originalMigrationDigests.keySet()) {
            Files.copy(ORIGINAL_MIGRATIONS.resolve(filename), copiedMigrations.resolve(filename));
        }
        Path copiedFirstMigration = copiedMigrations.resolve("V1__create_users.sql");
        String originalSql = Files.readString(copiedFirstMigration, StandardCharsets.UTF_8);
        String alteredSql = originalSql.replace("email VARCHAR(254) NOT NULL", "email VARCHAR(253) NOT NULL");
        assertThat(!alteredSql.equals(originalSql)).as("only the temporary SQL copy must change").isTrue();
        Files.writeString(copiedFirstMigration, alteredSql, StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext unexpected = start(schema,
                    "filesystem:" + copiedMigrations.toAbsolutePath().toString().replace('\\', '/'))) {
                // Closing an unexpectedly successful startup still leaves the failure assertion below unsatisfied.
            }
        });

        assertThat(hasDiagnostic(failure, FlywayValidateException.class, "checksum mismatch"))
                .as("Flyway checksum validation must reject this real application startup").isTrue();
        assertThat(migrationHistory(schema).equals(originalHistory))
                .as("failed startup must not repair or rewrite the recorded migration checksums").isTrue();
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema=? AND table_name='users' AND column_name='email'", Integer.class, schema))
                .as("the altered copy must not run or coerce the existing structure").isEqualTo(254);
    }

    @Test
    void rejectsStartupWhenHibernateFindsAnIncompatibleIsolatedSchema() {
        String schema = ownSchema();
        migrateIsolatedSchema(schema);
        List<Map<String, Object>> originalHistory = migrationHistory(schema);
        jdbc.execute("ALTER TABLE " + schema + ".users DROP COLUMN email");
        assertThat(!hasEmailColumn(schema)).as("the isolated incompatible column fixture must be absent").isTrue();

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext unexpected = start(schema, "classpath:db/migration")) {
                // ddl-auto=validate must fail; this context must never become a valid application.
            }
        });

        assertThat(hasDiagnostic(failure, SchemaManagementException.class, "missing column [email]"))
                .as("Hibernate schema validation must reject this real application startup").isTrue();
        assertThat(!hasEmailColumn(schema))
                .as("validate must not recreate the missing column or silently update the structure").isTrue();
        assertThat(migrationHistory(schema).equals(originalHistory))
                .as("the structural mismatch must not alter the successful Flyway history").isTrue();
    }

    private ConfigurableApplicationContext start(String schema, String migrationLocation) {
        requireOwnedSchema(schema);
        // Command arguments outrank environment URLs; no developer database or shared schema can be selected.
        // Register the fixture before component scanning so ConditionalOnMissingBean sees its RSA key pair.
        return new SpringApplicationBuilder(TestJwtKeyConfiguration.class, GrownupsvetBackendApplication.class)
                .profiles("test").web(WebApplicationType.SERVLET).run(
                        "--spring.profiles.active=test",
                        "--server.address=127.0.0.1", "--server.port=0",
                        "--spring.datasource.url=" + TEST_DATABASE_URL + "?currentSchema=" + schema,
                        "--spring.datasource.hikari.jdbc-url=" + TEST_DATABASE_URL + "?currentSchema=" + schema,
                        "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.datasource.hikari.connection-init-sql=SET search_path TO " + schema,
                        "--spring.flyway.url=" + TEST_DATABASE_URL + "?currentSchema=" + schema,
                        "--spring.flyway.user=${spring.datasource.username}",
                        "--spring.flyway.password=${spring.datasource.password}",
                        "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                        "--spring.flyway.locations=" + migrationLocation,
                        "--spring.flyway.validate-on-migrate=true", "--spring.flyway.clean-disabled=true",
                        "--spring.flyway.baseline-on-migrate=false", "--spring.flyway.create-schemas=true",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.properties.hibernate.default_schema=" + schema,
                        "--grownupsvet.staff.bootstrap.enabled=false", "--grownupsvet.staff.invitations.enabled=false",
                        "--grownupsvet.security.password-recovery.enabled=false",
                        "--grownupsvet.appointments.sender-address=",
                        "--grownupsvet.appointments.cutoff-poll-delay-millis=3600000",
                        "--grownupsvet.appointments.email-poll-delay-millis=3600000",
                        "--spring.main.banner-mode=off", "--spring.main.log-startup-info=false");
    }

    private void migrateIsolatedSchema(String schema) {
        requireOwnedSchema(schema);
        Flyway.configure().dataSource(database).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false)
                .validateOnMigrate(true).load().migrate();
        assertAllReviewedMigrations(schema);
    }

    private void assertAllReviewedMigrations(String schema) {
        List<Map<String, Object>> history = migrationHistory(schema);
        assertThat(history.size()).as("all reviewed versioned migrations must be applied").isEqualTo(LATEST_REVIEWED_MIGRATION_VERSION);
        assertThat(history.stream().allMatch(row -> Boolean.TRUE.equals(row.get("success"))))
                .as("all recorded migrations must be successful").isTrue();
        assertThat(Integer.toString(LATEST_REVIEWED_MIGRATION_VERSION).equals(history.getLast().get("version")))
                .as("the reviewed schema must reach the latest expected version").isTrue();
    }

    private List<Map<String, Object>> migrationHistory(String schema) {
        requireOwnedSchema(schema);
        return jdbc.queryForList("SELECT version, checksum, success FROM " + schema
                + ".flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
    }

    private boolean hasEmailColumn(String schema) {
        requireOwnedSchema(schema);
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema=? AND table_name='users' AND column_name='email')", Boolean.class, schema));
    }

    private String ownSchema() {
        String schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
        ownedSchemas.add(schema);
        return schema;
    }

    private void requireOwnedSchema(String schema) {
        if (!schema.matches(SCHEMA_PREFIX + "[0-9a-f]{32}") || !ownedSchemas.contains(schema)) {
            throw new IllegalStateException("Only schemas created by this test instance may be used.");
        }
    }

    private void assertTestDatabase() {
        if (!"grownupsvet_test".equals(jdbc.queryForObject("SELECT current_database()", String.class))) {
            throw new IllegalStateException("Startup validation requires the exclusive grownupsvet_test database.");
        }
    }

    private String requiredCredential(StandardEnvironment environment, String property, String variable) {
        String value = environment.getProperty(property);
        if (value == null) value = environment.getProperty(variable);
        if (value == null) throw new IllegalStateException("Test datasource credentials must be configured.");
        return value;
    }

    private Map<String, String> migrationDigests() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (var files = Files.list(ORIGINAL_MIGRATIONS)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("V[0-9]+__.*\\.sql")).sorted().toList()) {
                result.put(file.getFileName().toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
        }
        return result;
    }

    private boolean hasDiagnostic(Throwable failure, Class<? extends Throwable> expectedType, String diagnostic) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (expectedType.isInstance(cause) && cause.getMessage() != null
                    && cause.getMessage().toLowerCase(Locale.ROOT).contains(diagnostic.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
