package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model.CurrentAccountAuthenticationSnapshot;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads a fresh security snapshot without loading credential hashes or caching accounts. */
@Repository
public class CurrentAccountAuthenticationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CurrentAccountAuthenticationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CurrentAccountAuthenticationSnapshot> findCurrentAccount(UUID userId, String issuer,
                                                                           String jwtId) {
        return jdbc.query("""
                SELECT account.id, account.email, account.role, account.status, account.authentication_version,
                       EXISTS (SELECT 1 FROM revoked_access_tokens token
                               WHERE token.issuer = :issuer AND token.jwt_id = :jwtId) AS revoked
                FROM users account
                WHERE account.id = :userId
                """, Map.of("userId", userId, "issuer", issuer, "jwtId", jwtId),
                (result, rowNumber) -> new CurrentAccountAuthenticationSnapshot(
                        result.getObject("id", UUID.class), result.getString("email"),
                        UserRole.valueOf(result.getString("role")), "ACTIVE".equals(result.getString("status")),
                        result.getLong("authentication_version"), result.getBoolean("revoked")))
                .stream().findFirst();
    }
}
