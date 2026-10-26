package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Fail before accepting requests when production database, mail or public-link configuration is incomplete. */
@Component
@Profile({"prod", "external-postgres"})
public class ProductionConfigurationValidator {
    private static final String JDBC_POSTGRESQL_PREFIX = "jdbc:postgresql:";
    private static final String DATA_SOURCE_PROPERTIES = "spring.datasource.hikari.data-source-properties.";
    private static final Pattern EXTERNAL_DATABASE_HOST = Pattern.compile(
            "(?i)(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private final Environment environment;
    private final FrontendConfigurationProperties frontend;

    public ProductionConfigurationValidator(Environment environment, FrontendConfigurationProperties frontend) {
        this.environment = environment;
        this.frontend = frontend;
    }

    @PostConstruct
    public void validate() {
        validateDatabase();
        validateMail();
        String proxyPattern = environment.getProperty("server.tomcat.remoteip.internal-proxies", "^$");
        if (proxyPattern.isBlank() || proxyPattern.contains(".*")) {
            throw new IllegalStateException("Production cannot trust arbitrary forwarding proxies.");
        }
        try { Pattern.compile(proxyPattern); }
        catch (PatternSyntaxException failure) { throw new IllegalStateException("Production proxy pattern is invalid."); }
        validateProductionUrl(frontend.baseUrl(), false);
        frontend.allowedOrigins().forEach(value -> validateProductionUrl(value, true));
        validateProductionUrl(environment.getProperty("grownupsvet.api.base-url", ""), false);
        if (environment.getProperty("grownupsvet.staff.invitations.enabled", Boolean.class, false)) {
            validateProductionUrl(requireText("grownupsvet.staff.invitations.activation-url"), false);
        }
    }

    private void validateMail() {
        String host = requireText("spring.mail.host");
        boolean gmail = "smtp.gmail.com".equalsIgnoreCase(host);
        if (!gmail && !"sandbox.smtp.mailtrap.io".equalsIgnoreCase(host)) {
            throw new IllegalStateException("Production SMTP supports only Mailtrap Sandbox or Gmail.");
        }
        String username = requireText("spring.mail.username");
        requireText("spring.mail.password");
        Integer port = environment.getProperty("spring.mail.port", Integer.class);
        if (port == null || port < 1 || port > 65535
                || !environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, false)
                || !environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", Boolean.class, false)) {
            throw new IllegalStateException("Production SMTP requires authentication, STARTTLS and a valid port.");
        }
        if (gmail && (port != 587
                || !environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false))) {
            throw new IllegalStateException("Gmail SMTP requires port 587 and enabled STARTTLS.");
        }
        String sender = requireText("grownupsvet.appointments.sender-address");
        validateSender(sender);
        if (gmail && !sender.equalsIgnoreCase(username)) {
            throw new IllegalStateException("Gmail SMTP requires the sender address to match the authenticated account.");
        }
    }

    private void validateDatabase() {
        String databaseUrl = requireText("spring.datasource.url");
        requireText("spring.datasource.username");
        requireText("spring.datasource.password");
        if (environment.matchesProfiles("external-postgres")) {
            if (!environment.matchesProfiles("prod")) {
                throw new IllegalStateException("External PostgreSQL requires both prod and external-postgres profiles.");
            }
            validateExternalDatabase(databaseUrl);
            return;
        }
        String instance = requireText(DATA_SOURCE_PROPERTIES + "cloudSqlInstance");
        if (!instance.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]:[a-z0-9-]+:[a-z][a-z0-9-]{0,96}")) {
            throw new IllegalStateException("Production requires a valid Cloud SQL instance connection name.");
        }
    }

    private void validateExternalDatabase(String databaseUrl) {
        URI databaseUri;
        try {
            if (!databaseUrl.startsWith(JDBC_POSTGRESQL_PREFIX)) {
                throw new IllegalArgumentException();
            }
            databaseUri = URI.create(databaseUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException failure) {
            // Do not attach the exception: a rejected URL may contain credentials.
            throw new IllegalStateException("External PostgreSQL requires a valid PostgreSQL JDBC URL.");
        }
        String host = databaseUri.getHost();
        if (host == null || !EXTERNAL_DATABASE_HOST.matcher(host).matches()
                || host.toLowerCase(Locale.ROOT).endsWith(".localhost")
                || databaseUri.getRawUserInfo() != null || databaseUri.getRawQuery() != null
                || databaseUri.getRawFragment() != null || databaseUri.getPort() == 0 || databaseUri.getPort() > 65535
                || databaseUri.getRawPath() == null || !databaseUri.getRawPath().matches("/[a-zA-Z0-9_][a-zA-Z0-9_-]*")) {
            throw new IllegalStateException("External PostgreSQL requires an external DNS hostname and database, without URL credentials or parameters.");
        }
        if (!"verify-full".equals(requireText(DATA_SOURCE_PROPERTIES + "sslmode"))
                || !"org.postgresql.ssl.DefaultJavaSSLFactory".equals(requireText(DATA_SOURCE_PROPERTIES + "sslfactory"))) {
            throw new IllegalStateException("External PostgreSQL requires verified TLS using the Java truststore and hostname verification.");
        }
        for (String property : new String[]{"socketFactory", "cloudSqlInstance", "cloudSqlRefreshStrategy", "ipTypes", "sslhostnameverifier"}) {
            if (environment.containsProperty(DATA_SOURCE_PROPERTIES + property)) {
                throw new IllegalStateException("External PostgreSQL cannot configure Cloud SQL properties or a custom TLS hostname verifier.");
            }
        }
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url");
        if (hikariUrl != null && !hikariUrl.equals(databaseUrl)) {
            throw new IllegalStateException("External PostgreSQL cannot override the validated JDBC URL through Hikari.");
        }
        if (environment.containsProperty("spring.datasource.hikari.data-source-class-name")) {
            throw new IllegalStateException("External PostgreSQL requires the validated JDBC URL instead of a separate data source class.");
        }
    }

    private String requireText(String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalStateException("Production configuration is missing or invalid: " + property);
        }
        return value;
    }

    private static void validateSender(String sender) {
        try { new InternetAddress(sender, true).validate(); }
        catch (AddressException failure) { throw new IllegalStateException("Production requires a valid sender address."); }
    }

    private static void validateProductionUrl(String value, boolean originOnly) {
        if (value == null || value.isBlank()) { return; }
        URI uri = URI.create(FrontendConfigurationProperties.normalizeUrl(value, originOnly));
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || FrontendConfigurationProperties.isLoopbackHost(host)
                || host.endsWith(".invalid") || host.endsWith(".example") || host.endsWith(".test")
                || host.equals("example.com") || host.endsWith(".example.com")
                || host.equals("example.org") || host.endsWith(".example.org")
                || host.equals("example.net") || host.endsWith(".example.net")) {
            throw new IllegalStateException("Production public URLs require a real HTTPS host.");
        }
    }
}
