package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties("grownupsvet.frontend")
public record FrontendConfigurationProperties(@DefaultValue("") String baseUrl, List<String> allowedOrigins) {
    public FrontendConfigurationProperties {
        baseUrl = normalizeUrl(baseUrl, false);
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> normalizeUrl(value, true)).distinct().toList();
    }

    static String normalizeUrl(String value, boolean originOnly) {
        if (value == null || value.isBlank()) { return ""; }
        String normalized = value.strip();
        try {
            URI uri = URI.create(normalized);
            boolean localHttp = "http".equals(uri.getScheme()) && isLoopbackHost(uri.getHost());
            if ((!"https".equals(uri.getScheme()) && !localHttp) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || normalized.contains("*")
                    || (originOnly && uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw new IllegalArgumentException();
            }
            normalized = uri.getScheme() + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT)
                    + (uri.getRawPath() == null ? "" : uri.getRawPath());
            if (!originOnly) {
                while (normalized.endsWith("/")) { normalized = normalized.substring(0, normalized.length() - 1); }
            }
            return normalized;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Frontend URLs require HTTPS or local HTTP, without credentials, query or fragment; CORS requires exact origins.");
        }
    }

    static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }
}
