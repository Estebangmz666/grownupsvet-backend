package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ApplicationClockConfiguration {
    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
