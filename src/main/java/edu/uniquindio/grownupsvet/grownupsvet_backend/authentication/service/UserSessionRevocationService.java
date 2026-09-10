package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.JwtSecurityProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserSessionRevocationService {
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final UserRepository userRepository;
    private final JwtSecurityProperties jwtSecurityProperties;
    private final Clock clock;

    public UserSessionRevocationService(RevokedAccessTokenRepository revokedAccessTokenRepository,
                                        UserRepository userRepository,
                                        JwtSecurityProperties jwtSecurityProperties, Clock clock) {
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.userRepository = userRepository;
        this.jwtSecurityProperties = jwtSecurityProperties;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Jwt jwt) {
        Instant now = clock.instant();
        revokedAccessTokenRepository.deleteByExpiresAtBefore(now.minus(jwtSecurityProperties.clockSkew()));
        User user = userRepository.findById(parseUserId(jwt.getSubject()))
                .orElseThrow(() -> new BadCredentialsException("Authenticated account no longer exists"));
        Instant revocationExpiration = jwt.getExpiresAt().isAfter(now)
                ? jwt.getExpiresAt()
                : now.plus(jwtSecurityProperties.clockSkew());
        revokedAccessTokenRepository.revoke(jwt.getClaimAsString("iss"), jwt.getId(), user.getId(), now,
                revocationExpiration);
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Invalid authenticated subject");
        }
    }
}
