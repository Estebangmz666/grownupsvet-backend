package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.RevokedAccessTokenId;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final UserPermissionResolver permissionResolver;

    public CurrentAccountJwtValidator(UserRepository userRepository,
                                      RevokedAccessTokenRepository revokedAccessTokenRepository,
                                      UserPermissionResolver permissionResolver) {
        this.userRepository = userRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
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

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()
                || !user.getEmail().equals(jwt.getClaimAsString("email"))
                || !user.getRole().name().equals(jwt.getClaimAsString("role"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        List<String> tokenPermissions = jwt.getClaimAsStringList("permissions");
        List<String> currentPermissions = permissionResolver.resolve(user.getRole());
        if (tokenPermissions == null || tokenPermissions.size() != currentPermissions.size()
                || !new HashSet<>(tokenPermissions).equals(new HashSet<>(currentPermissions))
                || revokedAccessTokenRepository.existsById(new RevokedAccessTokenId(issuer, jwtId))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private UUID parseUserId(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
