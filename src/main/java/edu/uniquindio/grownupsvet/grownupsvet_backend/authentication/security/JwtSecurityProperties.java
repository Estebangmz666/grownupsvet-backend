package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("grownupsvet.security.jwt")
public record JwtSecurityProperties(
        @DefaultValue("grownupsvet-backend") String issuer,
        @DefaultValue("grownupsvet-clients") String audience,
        @DefaultValue("PT24H") Duration accessTokenTtl,
        @DefaultValue("PT30S") Duration clockSkew,
        String keyStorePath,
        String keyStorePassword,
        @DefaultValue("grownupsvet-jwt-signing-2026-09") String keyAlias,
        String keyPassword
) { }
