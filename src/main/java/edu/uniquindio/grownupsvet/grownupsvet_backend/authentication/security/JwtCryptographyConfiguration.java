package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtCryptographyConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtRsaKeyPair jwtRsaKeyPair(JwtSecurityProperties properties) {
        return JwtKeyStoreLoader.load(properties);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtRsaKeyPair keyPair, JwtSecurityProperties properties) {
        RSAKey rsaKey = new RSAKey.Builder(keyPair.publicKey())
                .privateKey(keyPair.privateKey())
                .algorithm(JWSAlgorithm.RS256)
                .keyID(properties.keyAlias())
                .build();
        JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(keySource);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtRsaKeyPair keyPair, JwtSecurityProperties properties, Clock clock,
                          CurrentAccountJwtValidator currentAccountJwtValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyPair.publicKey()).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.clockSkew());
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>("aud",
                audiences -> audiences != null && audiences.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator,
                new JwtIssuerValidator(properties.issuer()), audienceValidator, currentAccountJwtValidator));
        return decoder;
    }
}
