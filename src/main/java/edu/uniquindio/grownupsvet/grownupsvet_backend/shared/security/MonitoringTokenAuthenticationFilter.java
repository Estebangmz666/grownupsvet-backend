package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Created only inside the monitoring SecurityFilterChain, never registered as a servlet filter bean. */
public final class MonitoringTokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String PROMETHEUS_PATH = "/actuator/prometheus";
    public static final String SCRAPE_AUTHORITY = "MONITORING_SCRAPE";
    private static final Pattern MACHINE_BEARER = Pattern.compile("(?i:Bearer) ([A-Za-z0-9_-]{43})");
    private final byte[] expectedTokenHash;
    private final ApiSecurityErrorHandler errorHandler;

    public MonitoringTokenAuthenticationFilter(byte[] expectedTokenHash, ApiSecurityErrorHandler errorHandler) {
        this.expectedTokenHash = expectedTokenHash.clone();
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        List<String> authorizationHeaders = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (!"GET".equals(request.getMethod()) || !PROMETHEUS_PATH.equals(request.getRequestURI()) || authorizationHeaders.size() != 1
                || !matchesToken(authorizationHeaders.getFirst())) {
            errorHandler.commence(request, response, new BadCredentialsException("Machine monitoring authentication is required."));
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("prometheus-monitor", null,
                List.of(new SimpleGrantedAuthority(SCRAPE_AUTHORITY))));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private boolean matchesToken(String authorizationHeader) {
        Matcher matcher = MACHINE_BEARER.matcher(authorizationHeader);
        if (!matcher.matches()) { return false; }
        try {
            byte[] presentedHash = MessageDigest.getInstance("SHA-256").digest(matcher.group(1).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expectedTokenHash, presentedHash);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("The required SHA-256 algorithm is unavailable.");
        }
    }
}
