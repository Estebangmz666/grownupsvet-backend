package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.ApiSecurityErrorHandler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.controller.HelloController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.controller.UserPasswordRecoveryController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller.UserSignupController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id accepts the agreed Unicode/long passphrases without bcrypt's byte limit.
        return new DelegatingPasswordEncoder("argon2id", Map.of(
                "argon2id", new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
                "bcrypt", new BCryptPasswordEncoder()));
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ApiSecurityErrorHandler apiSecurityErrorHandler, UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // This increment does not authenticate with cookies, HTTP Basic or sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .authorizeHttpRequests(access -> access
                        // Opt-in monitoring transports use a narrower earlier chain; this API chain never grants metrics.
                        .requestMatchers("/actuator/prometheus", "/actuator/prometheus/**").denyAll()
                        .requestMatchers(HttpMethod.GET, HelloController.HELLO_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/account-activations",
                                UserPasswordRecoveryController.PASSWORD_RECOVERIES_PATH,
                                UserPasswordRecoveryController.PASSWORD_RECOVERY_VERIFICATIONS_PATH,
                                UserPasswordRecoveryController.PASSWORD_RESETS_PATH).permitAll()
                        .requestMatchers(UserSignupController.REGISTRATION_PATH,
                                UserSessionController.SESSIONS_PATH).permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("permissions");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }
}
