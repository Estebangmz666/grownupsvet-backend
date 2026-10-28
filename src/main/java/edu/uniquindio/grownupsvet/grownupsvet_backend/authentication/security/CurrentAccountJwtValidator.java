package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.CurrentAccountAuthenticationSnapshot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.CurrentAccountAuthenticationRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Rejects stale account snapshots and revoked tokens on every protected request. */
@Component
public class CurrentAccountJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "The access token is no longer valid.", null);

    private final CurrentAccountAuthenticationRepository currentAccountAuthenticationRepository;
    private final UserPermissionResolver permissionResolver;

    public CurrentAccountJwtValidator(CurrentAccountAuthenticationRepository currentAccountAuthenticationRepository,
                                      UserPermissionResolver permissionResolver) {
        this.currentAccountAuthenticationRepository = currentAccountAuthenticationRepository;
        this.permissionResolver = permissionResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        String issuer = jwt.getClaimAsString("iss");
        String jwtId = jwt.getId();
        if (userId == null || issuer == null || issuer.isBlank() || jwtId == null || jwtId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        CurrentAccountAuthenticationSnapshot user = currentAccountAuthenticationRepository
                .findCurrentAccount(userId, issuer, jwtId).orElse(null);
        if (user == null || !user.active() || user.revoked()
                || !hasCurrentAuthenticationVersion(jwt, user)
                || !user.email().equals(jwt.getClaimAsString("email"))
                || !user.role().name().equals(jwt.getClaimAsString("role"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        List<String> tokenPermissions = jwt.getClaimAsStringList("permissions");
        List<String> currentPermissions = permissionResolver.resolve(user.role());
        if (tokenPermissions == null || tokenPermissions.size() != currentPermissions.size()
                || !new HashSet<>(tokenPermissions).equals(new HashSet<>(currentPermissions))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasCurrentAuthenticationVersion(Jwt jwt, CurrentAccountAuthenticationSnapshot user) {
        Object value = jwt.getClaims().get("authenticationVersion");
        return value instanceof Number number
                && number.longValue() == user.authenticationVersion()
                && number.doubleValue() == number.longValue();
    }

    private UUID parseUserId(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
