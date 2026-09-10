package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.AuthenticatedUserResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.JwtSecurityProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {
    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtSecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public UserLoginResponseDTO issue(User user, List<String> permissions) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("permissions", List.copyOf(permissions))
                .claim("authenticationVersion", user.getAuthenticationVersion())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.keyAlias())
                .type("JWT")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        long expiresIn = properties.accessTokenTtl().toSeconds();
        AuthenticatedUserResponseDTO authenticatedUser = new AuthenticatedUserResponseDTO(
                user.getId(), user.getEmail(), user.getRole(), permissions);
        return new UserLoginResponseDTO(token, TOKEN_TYPE, expiresIn, authenticatedUser);
    }
}
