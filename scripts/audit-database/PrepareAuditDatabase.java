import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Creates only new loopback PostgreSQL audit databases. Never starts Spring or SMTP. */
class PrepareAuditDatabase {
    private static String stage = "VALIDATE_PARAMETERS";
    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required environment missing");
        return value;
    }
    private static String identifier(String value) {
        if (!value.matches("[a-z][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Unsafe SQL identifier");
        return '"' + value + '"';
    }
    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static Connection connect(String origin, String database, String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username); properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "10"); properties.setProperty("socketTimeout", "60");
        return DriverManager.getConnection(origin + database, properties);
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalStateException("Audit invariant failed"); }
    private static void expectSqlState(Connection connection, String sql, String expected) throws SQLException {
        connection.setAutoCommit(false);
        try { execute(connection, sql); throw new IllegalStateException("Expected database rejection was absent"); }
        catch (SQLException exception) {
            if (!expected.equals(exception.getSQLState())) {
                stage = stage + "_EXPECTED_" + expected + "_ACTUAL_" + exception.getSQLState();
                throw new IllegalStateException("Unexpected SQL rejection");
            }
        }
        finally { connection.rollback(); connection.setAutoCommit(true); }
    }
    public static void main(String[] arguments) {
        try { prepare(); }
        catch (Exception exception) {
            System.err.println("AUDIT_DATABASE_FAILED stage=" + stage + " type=" + exception.getClass().getSimpleName()
                + (exception instanceof SQLException sql ? " sqlState=" + sql.getSQLState() : ""));
            // Never print JDBC exception messages, SQL parameters or credentials.
            System.exit(1);
        }
    }
    private static void prepare() throws Exception {
        String host = environment("AUDIT_DATABASE_HOST");
        require(Set.of("127.0.0.1", "localhost", "::1").contains(host));
        int port = Integer.parseInt(environment("AUDIT_DATABASE_PORT")); require(port > 0 && port <= 65535);
        String runId = environment("AUDIT_DATABASE_RUN_ID"); require(runId.matches("[0-9a-f]{32}"));
        String primary = "grownupsvet_audit_" + runId, disposable = "grownupsvet_audit_write_" + runId;
        String reader = "gv_audit_reader_" + runId, writer = "gv_audit_writer_" + runId;
        String admin = environment("AUDIT_DATABASE_ADMIN_USERNAME"), adminPassword = environment("AUDIT_DATABASE_ADMIN_PASSWORD");
        String readerPassword = environment("AUDIT_DATABASE_READER_PASSWORD"), writerPassword = environment("AUDIT_DATABASE_WRITER_PASSWORD");
        String origin = "jdbc:postgresql://" + (host.equals("::1") ? "[::1]" : host) + ":" + port + "/";
        Path migrations = Path.of(environment("AUDIT_DATABASE_MIGRATIONS")).toRealPath();
        long expectedMigrations;
        try (var files = Files.list(migrations)) { expectedMigrations = files.filter(p -> p.getFileName().toString().matches("V[0-9]+__.+\\.sql")).count(); }
        require(expectedMigrations >= 22);
        stage = "PREFLIGHT_LOCAL_POSTGRES";
        try (Connection connection = connect(origin, "postgres", admin, adminPassword)) {
            require(scalar(connection, "SELECT current_setting('server_version_num')::int / 10000") == 18);
            require(scalar(connection, "SELECT (inet_server_addr() IN ('127.0.0.1'::inet,'::1'::inet))::int") == 1);
            require(scalar(connection, "SELECT ((rolsuper OR rolcreatedb) AND (rolsuper OR rolcreaterole))::int FROM pg_roles WHERE rolname=current_user") == 1);
            // Names are fresh UUIDs. Refuse existing resources instead of reusing or deleting them.
            require(scalar(connection, "SELECT count(*) FROM pg_database WHERE datname IN (" + literal(primary) + "," + literal(disposable) + ")") == 0);
            require(scalar(connection, "SELECT count(*) FROM pg_roles WHERE rolname IN (" + literal(reader) + "," + literal(writer) + ")") == 0);
            stage = "CREATE_ISOLATED_RESOURCES";
            for (String database : List.of(primary, disposable)) {
                execute(connection, "CREATE DATABASE " + identifier(database) + " TEMPLATE template0 ENCODING 'UTF8'");
                execute(connection, "COMMENT ON DATABASE " + identifier(database) + " IS " + literal("GrownupsVet isolated audit " + runId));
                execute(connection, "REVOKE ALL ON DATABASE " + identifier(database) + " FROM PUBLIC");
            }
            execute(connection, "CREATE ROLE " + identifier(reader) + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD " + literal(readerPassword));
            execute(connection, "CREATE ROLE " + identifier(writer) + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD " + literal(writerPassword));
            execute(connection, "COMMENT ON ROLE " + identifier(reader) + " IS " + literal("GrownupsVet isolated audit " + runId));
            execute(connection, "COMMENT ON ROLE " + identifier(writer) + " IS " + literal("GrownupsVet isolated audit " + runId));
            execute(connection, "GRANT CONNECT ON DATABASE " + identifier(primary) + " TO " + identifier(reader));
            execute(connection, "GRANT CONNECT ON DATABASE " + identifier(disposable) + " TO " + identifier(writer));
            execute(connection, "ALTER ROLE " + identifier(reader) + " IN DATABASE " + identifier(primary) + " SET default_transaction_read_only = on");
        }
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(environment("AUDIT_DATABASE_ACCOUNT_PASSWORD"));
        for (String database : List.of(primary, disposable)) {
            stage = "MIGRATE_" + (database.equals(primary) ? "READ" : "WRITE");
            Flyway flyway = Flyway.configure().dataSource(origin + database, admin, adminPassword)
                .locations("filesystem:" + migrations).cleanDisabled(true).load();
            flyway.migrate(); flyway.validate();
            try (Connection connection = connect(origin, database, admin, adminPassword)) {
                require(scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL") == expectedMigrations);
                require(scalar(connection, "SELECT count(*) FROM pg_tables WHERE schemaname='public'") >= 30);
                execute(connection, "REVOKE ALL ON SCHEMA public FROM PUBLIC");
                stage = "SEED_" + (database.equals(primary) ? "READ" : "WRITE");
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users(id,email,password_hash,role,status) VALUES (?::uuid,?,?,?,'ACTIVE')")) {
                    String[] roles = {"SUPER_ADMIN", "ADMINISTRATOR", "VETERINARIAN", "OWNER"};
                    for (int index = 0; index < roles.length; index++) {
                        statement.setString(1, "10000000-0000-0000-0000-" + String.format("%012d", index + 1));
                        statement.setString(2, roles[index].toLowerCase(Locale.ROOT) + "@audit.example.com");
                        statement.setString(3, hash); statement.setString(4, roles[index]); statement.addBatch();
                    }
                    statement.executeBatch();
                }
                execute(connection, Files.readString(Path.of(environment("AUDIT_DATABASE_FIXTURES"))));
                connection.commit(); connection.setAutoCommit(true);
                stage = "CHECK_FIXTURE_" + (database.equals(primary) ? "READ" : "WRITE");
                require(scalar(connection, "SELECT count(*) FROM users") == 4);
                require(scalar(connection, "SELECT count(*) FROM pets") == 1);
                require(scalar(connection, "SELECT count(*) FROM appointments") == 1);
                require(scalar(connection, "SELECT count(*) FROM clinical_encounters WHERE status='DRAFT'") == 1);
                require(scalar(connection, "SELECT (SELECT count(*) FROM appointment_email_tasks) + (SELECT count(*) FROM clinical_reminder_email_tasks)") == 0);
                if (database.equals(primary)) {
                    stage = "CREATE_REVIEW_VIEWS";
                    execute(connection, "CREATE SCHEMA audit_review");
                    execute(connection, "CREATE VIEW audit_review.accounts AS SELECT id,email,role,status,authentication_version FROM public.users");
                    for (String table : List.of("owner_profiles","administrator_profiles","veterinarian_profiles","veterinarian_qualifications","pets","veterinarian_availability_slots","veterinarian_availability_events","appointments","appointment_assignments","appointment_events","clinical_encounters","clinical_encounter_events","clinical_prescriptions","clinical_controls"))
                        execute(connection, "CREATE VIEW audit_review." + identifier(table) + " AS SELECT * FROM public." + identifier(table));
                    execute(connection, "CREATE VIEW audit_review.migrations AS SELECT installed_rank,version,description,success FROM public.flyway_schema_history");
                    execute(connection, "GRANT USAGE ON SCHEMA audit_review TO " + identifier(reader));
                    execute(connection, "GRANT SELECT ON ALL TABLES IN SCHEMA audit_review TO " + identifier(reader));
                    String schemaSql = """
                        SELECT jsonb_pretty(jsonb_build_object(
                          'migrationCount', (SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL),
                          'tables', (SELECT jsonb_agg(jsonb_build_object('name',c.relname,'columns',
                            (SELECT jsonb_agg(jsonb_build_object('name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'nullable',NOT a.attnotnull) ORDER BY a.attnum)
                             FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped)) ORDER BY c.relname)
                            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r'),
                          'constraints', (SELECT jsonb_agg(jsonb_build_object('table',c.relname,'name',p.conname,'type',p.contype,'definition',pg_get_constraintdef(p.oid)) ORDER BY c.relname,p.conname)
                            FROM pg_constraint p JOIN pg_class c ON c.oid=p.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public')
                        ))
                        """;
                    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(schemaSql)) {
                        result.next();
                        Files.writeString(Path.of(environment("AUDIT_DATABASE_REPORT")).resolveSibling("schema-sanitized.json"), result.getString(1));
                    }
                } else {
                    execute(connection, "GRANT USAGE ON SCHEMA public TO " + identifier(writer));
                    execute(connection, "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO " + identifier(writer));
                    execute(connection, "REVOKE ALL ON public.flyway_schema_history FROM " + identifier(writer));
                    execute(connection, "GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO " + identifier(writer));
                }
            }
        }
        stage = "VERIFY_READER_AND_CONSTRAINTS";
        try (Connection connection = connect(origin, primary, reader, readerPassword)) {
            require(scalar(connection, "SELECT count(*) FROM audit_review.accounts") == 4);
            require(scalar(connection, "SELECT count(*) FROM audit_review.migrations WHERE success") == expectedMigrations);
            require(scalar(connection, "SELECT (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)::int FROM pg_roles WHERE rolname=current_user") == 0);
            stage = "VERIFY_READER_WRITE_DENIAL";
            expectSqlState(connection, "UPDATE audit_review.pets SET name='forbidden'", "42501");
            execute(connection, "SET default_transaction_read_only=off");
            expectSqlState(connection, "SELECT password_hash FROM public.users", "42501");
            expectSqlState(connection, "UPDATE audit_review.pets SET name='forbidden'", "42501");
        }
        try (Connection connection = connect(origin, disposable, writer, writerPassword)) {
            stage = "VERIFY_WRITER_CONSTRAINTS";
            require(scalar(connection, "SELECT (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)::int FROM pg_roles WHERE rolname=current_user") == 0);
            expectSqlState(connection, "INSERT INTO pets(id,owner_id,name,species,created_at,updated_at) VALUES (gen_random_uuid(),gen_random_uuid(),'Missing owner','DOG',now(),now())", "23503");
            expectSqlState(connection, "INSERT INTO veterinarian_availability_slots SELECT gen_random_uuid(),veterinarian_id,starts_at,ends_at,status,version,created_at,updated_at,created_by,updated_by FROM veterinarian_availability_slots LIMIT 1", "23505");
            expectSqlState(connection, "UPDATE veterinarian_availability_events SET reason='mutated'", "P0001");
            expectSqlState(connection, "UPDATE pets SET species='BIRD'", "23514");
            connection.setAutoCommit(false); execute(connection, "UPDATE pets SET name='Rollback demonstration'"); connection.rollback(); connection.setAutoCommit(true);
            require(scalar(connection, "SELECT count(*) FROM pets WHERE name='Luna audit fixture'") == 1);
        }
        stage = "WRITE_SANITIZED_REPORT";
        String report = """
            {"status":"PASSED","checkedAtUtc":"%s","runId":"%s","host":"%s","port":%d,
             "readDatabase":"%s","writeDatabase":"%s","readerUsername":"%s","writerUsername":"%s",
             "migrationCountPerDatabase":%d,"fixtureAccountsPerDatabase":4,"petsPerDatabase":1,
             "appointmentsPerDatabase":1,"clinicalDraftsPerDatabase":1,"smtpStarted":false,"externalDatabaseAccess":false,
             "readerHasOnlySanitizedViews":true,"readerWriteDeniedEvenAfterReadOnlyOverride":true,
             "foreignKeyRejected":true,"duplicateSlotRejected":true,"immutableEventRejected":true,
             "invalidSpeciesRejected":true,"writerRollbackVerified":true,"secondPersonReproductionVerified":false}
            """.formatted(Instant.now(),runId,host,port,primary,disposable,reader,writer,expectedMigrations);
        Files.writeString(Path.of(environment("AUDIT_DATABASE_REPORT")), report);
        System.out.println("AUDIT_DATABASE_PASSED");
    }
}
