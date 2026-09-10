package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.RevokedAccessToken;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.RevokedAccessTokenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, RevokedAccessTokenId> {
    long deleteByExpiresAtBefore(Instant expirationThreshold);

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedAccessToken token WHERE token.user.id = :userId")
    int deleteForUser(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO revoked_access_tokens (issuer, jwt_id, user_id, revoked_at, expires_at)
            VALUES (:issuer, :jwtId, :userId, :revokedAt, :expiresAt)
            ON CONFLICT (issuer, jwt_id) DO NOTHING
            """, nativeQuery = true)
    int revoke(@Param("issuer") String issuer, @Param("jwtId") String jwtId,
               @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt,
               @Param("expiresAt") Instant expiresAt);
}
