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

/** Fail before accepting requests when the production mail or public-link configuration is incomplete. */
@Component
@Profile("prod")
public class ProductionConfigurationValidator {
    private final Environment environment;
    private final FrontendConfigurationProperties frontend;

    public ProductionConfigurationValidator(Environment environment, FrontendConfigurationProperties frontend) {
        this.environment = environment;
        this.frontend = frontend;
    }

    @PostConstruct
    public void validate() {
        requireText("spring.datasource.url");
        requireText("spring.datasource.username");
        requireText("spring.datasource.password");
        String instance = requireText("spring.datasource.hikari.data-source-properties.cloudSqlInstance");
        if (!instance.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]:[a-z0-9-]+:[a-z][a-z0-9-]{0,96}")) {
            throw new IllegalStateException("Production requires a valid Cloud SQL instance connection name.");
        }
        String host = requireText("spring.mail.host");
        if (!"sandbox.smtp.mailtrap.io".equalsIgnoreCase(host)) {
            throw new IllegalStateException("This delivery requires the configured Mailtrap Sandbox SMTP host.");
        }
        requireText("spring.mail.username");
        requireText("spring.mail.password");
        Integer port = environment.getProperty("spring.mail.port", Integer.class);
        if (port == null || port < 1 || port > 65535
                || !environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, false)
                || !environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", Boolean.class, false)) {
            throw new IllegalStateException("Production SMTP requires authentication, STARTTLS and a valid port.");
        }
        validateSender(requireText("grownupsvet.appointments.sender-address"));
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
