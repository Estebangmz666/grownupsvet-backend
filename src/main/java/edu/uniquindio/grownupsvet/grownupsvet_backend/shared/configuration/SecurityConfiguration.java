package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.ApiSecurityErrorHandler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller.UserSignupController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id accepts the agreed Unicode/long passphrases without bcrypt's byte limit.
        return new DelegatingPasswordEncoder("argon2id", Map.of(
                "argon2id", new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
                "bcrypt", new BCryptPasswordEncoder()));
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // Prevent Boot's generated development account. Login/JWT is a later increment.
        return authentication -> {
            throw new AuthenticationServiceException("Authentication is not implemented in this increment.");
        };
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      ApiSecurityErrorHandler apiSecurityErrorHandler) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // This increment does not authenticate with cookies, HTTP Basic or sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(UserSignupController.REGISTRATION_PATH).permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
