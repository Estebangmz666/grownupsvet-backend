package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class JwtCryptographyTests {
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtSecurityProperties properties;
    @Autowired private Clock clock;

    @Test
    void rejectsAnExpiredTokenBeyondTheThirtySecondClockSkew() {
        Instant now = clock.instant();
        String token = encode(now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS),
                properties.issuer(), properties.audience());

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnUnexpectedIssuerOrAudience() {
        Instant now = clock.instant();
        String wrongIssuer = encode(now, now.plus(1, ChronoUnit.HOURS),
                "untrusted-issuer", properties.audience());
        String wrongAudience = encode(now, now.plus(1, ChronoUnit.HOURS),
                properties.issuer(), "untrusted-audience");

        assertThatThrownBy(() -> jwtDecoder.decode(wrongIssuer)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtDecoder.decode(wrongAudience)).isInstanceOf(JwtException.class);
    }

    @Test
    void failsFastWithoutExternalKeyStoreConfiguration() {
        JwtSecurityProperties missingKeyStore = new JwtSecurityProperties(
                properties.issuer(), properties.audience(), properties.accessTokenTtl(), properties.clockSkew(),
                "", "", properties.keyAlias(), "");

        assertThatThrownBy(() -> JwtKeyStoreLoader.load(missingKeyStore))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_KEYSTORE_PATH must be configured.");
    }

    private String encode(Instant issuedAt, Instant expiresAt, String issuer, String audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", "cryptography-test@example.com")
                .claim("role", "OWNER")
                .claim("permissions", List.of("PROFILE_READ_SELF"))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.keyAlias()).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
