package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AppointmentProperties.class)
public class AppointmentConfiguration { }
