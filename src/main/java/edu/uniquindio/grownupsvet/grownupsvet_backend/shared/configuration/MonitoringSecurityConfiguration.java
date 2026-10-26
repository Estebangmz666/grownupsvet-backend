package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.ApiSecurityErrorHandler;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.MonitoringTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile("monitoring")
@DependsOn("monitoringConfigurationValidator")
public class MonitoringSecurityConfiguration {
    @Bean
    @Order(-1)
    public SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http, MonitoringConfigurationValidator configuration,
            ApiSecurityErrorHandler errorHandler) throws Exception {
        return http
                .securityMatcher(request -> MonitoringTokenAuthenticationFilter.PROMETHEUS_PATH.equals(request.getRequestURI())
                        || request.getRequestURI().startsWith(MonitoringTokenAuthenticationFilter.PROMETHEUS_PATH + "/"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .addFilterBefore(new MonitoringTokenAuthenticationFilter(configuration.expectedTokenHash(), errorHandler), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(access -> access.anyRequest().hasAuthority(MonitoringTokenAuthenticationFilter.SCRAPE_AUTHORITY))
                .build();
    }
}
