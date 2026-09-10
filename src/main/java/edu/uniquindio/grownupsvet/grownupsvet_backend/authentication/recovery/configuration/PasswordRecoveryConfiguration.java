package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordRecoveryProperties.class)
@EnableScheduling
public class PasswordRecoveryConfiguration { }
