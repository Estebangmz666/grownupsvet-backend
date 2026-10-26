package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.ApiSecurityErrorHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Scraping stays within the instance network and does not consume a person's session. */
@Configuration(proxyBeanMethods = false)
@Profile("observability")
@DependsOn("observabilityConfigurationValidator")
public class ObservabilitySecurityConfiguration {

    @Bean
    @Order(0)
    public SecurityFilterChain observabilitySecurityFilterChain(HttpSecurity http, Environment environment,
            ApiSecurityErrorHandler errorHandler) throws Exception {
        return http
                .securityMatcher(request -> isManagementPort(request, environment) || isApplicationProbe(request))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(ObservabilitySecurityConfiguration::isApplicationProbe).permitAll()
                        .requestMatchers(request -> "GET".equals(request.getMethod())
                                && isManagementPort(request, environment)
                                && ("/actuator/prometheus".equals(request.getRequestURI())
                                || "/actuator/health/liveness".equals(request.getRequestURI())
                                || "/actuator/health/readiness".equals(request.getRequestURI()))).permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    static boolean isManagementPort(HttpServletRequest request, Environment environment) {
        // Boot publishes the actual bound port into the parent Environment, including random test ports.
        Integer managementPort = environment.getProperty("local.management.port", Integer.class);
        return managementPort != null && managementPort > 0 && request.getLocalPort() == managementPort;
    }

    static boolean isApplicationProbe(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && ("/livez".equals(request.getRequestURI()) || "/readyz".equals(request.getRequestURI()));
    }
}
