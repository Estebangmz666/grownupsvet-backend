package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.RevokedAccessToken;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.RevokedAccessTokenId;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises fresh committed account/denylist reads, rather than a mocked repository. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class CurrentAccountJwtValidatorIntegrationTests {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String JWT_ID = "current-account-fixture-token";

    @Autowired private CurrentAccountJwtValidator validator;
    @Autowired private UserPermissionResolver permissionResolver;
    @Autowired private UserRepository users;
    @Autowired private RevokedAccessTokenRepository revokedTokens;

    private User user;

    @BeforeEach
    void createAccount() {
        user = users.saveAndFlush(new User("security-snapshot+" + UUID.randomUUID() + "@example.com",
                "unused-fixture-credential-hash", UserRole.ADMINISTRATOR));
    }

    @AfterEach
    void removeAccount() {
        revokedTokens.deleteForUser(user.getId());
        users.deleteById(user.getId());
        users.flush();
    }

    @Test
    void validCurrentAccountIsAccepted() {
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void committedRevocationIsVisibleOnTheNextValidation() {
        Jwt jwt = token().build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
        revoke(ISSUER, JWT_ID);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void revocationMatchesTheExactIssuerAndTokenId() {
        revoke(ISSUER + "/another", JWT_ID);
        revoke(ISSUER, JWT_ID + "-another");
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void committedDeactivationIsVisibleOnTheNextValidation() {
        Jwt jwt = token().build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
        user.deactivate();
        users.saveAndFlush(user);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void committedEmailChangeRejectsThePreviousSnapshot() {
        Jwt jwt = token().build();
        user.changeEmail("changed+" + UUID.randomUUID() + "@example.com");
        user = users.saveAndFlush(user);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void committedRoleChangeRejectsThePreviousSnapshot() {
        Jwt jwt = token().build();
        user.changeRole(UserRole.VETERINARIAN);
        user = users.saveAndFlush(user);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void committedCredentialVersionChangeRejectsThePreviousSnapshot() {
        Jwt jwt = token().build();
        user.resetPasswordHash("unused-replacement-fixture-hash");
        user = users.saveAndFlush(user);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        assertThat(validator.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void missingAccountIsRejected() {
        assertThat(validator.validate(token().subject(UUID.randomUUID().toString()).build()).hasErrors()).isTrue();
    }

    @Test
    void fractionalOrAbsentAuthenticationVersionIsRejected() {
        assertThat(validator.validate(token().claim("authenticationVersion", 0.5).build()).hasErrors()).isTrue();
        assertThat(validator.validate(token().claims(claims -> claims.remove("authenticationVersion"))
                .build()).hasErrors()).isTrue();
    }

    @Test
    void missingExtraAndDuplicatedPermissionsAreRejected() {
        List<String> current = permissionResolver.resolve(user.getRole());
        assertThat(validator.validate(token().claim("permissions", current.subList(1, current.size()))
                .build()).hasErrors()).isTrue();
        List<String> extra = new ArrayList<>(current);
        extra.add("UNKNOWN_PERMISSION");
        assertThat(validator.validate(token().claim("permissions", extra).build()).hasErrors()).isTrue();
        List<String> duplicated = new ArrayList<>(current);
        duplicated.set(0, duplicated.get(1));
        assertThat(validator.validate(token().claim("permissions", duplicated).build()).hasErrors()).isTrue();
    }

    @Test
    void malformedIdentityAndMissingIssuerOrTokenIdAreRejected() {
        assertThat(validator.validate(token().subject("invalid-user-id").build()).hasErrors()).isTrue();
        assertThat(validator.validate(token().claims(claims -> claims.remove("iss")).build()).hasErrors()).isTrue();
        assertThat(validator.validate(token().claims(claims -> claims.remove("jti")).build()).hasErrors()).isTrue();
    }

    private void revoke(String issuer, String jwtId) {
        Instant now = Instant.now();
        revokedTokens.saveAndFlush(new RevokedAccessToken(new RevokedAccessTokenId(issuer, jwtId),
                user, now, now.plusSeconds(300)));
    }

    private Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("unsigned-fixture-for-account-validator-only")
                .header("alg", "RS256").subject(user.getId().toString()).issuer(ISSUER).jti(JWT_ID)
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("email", user.getEmail()).claim("role", user.getRole().name())
                .claim("authenticationVersion", user.getAuthenticationVersion())
                .claim("permissions", permissionResolver.resolve(user.getRole()));
    }
}
