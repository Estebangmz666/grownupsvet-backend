package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "revoked_access_tokens")
public class RevokedAccessToken {
    @EmbeddedId
    private RevokedAccessTokenId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected RevokedAccessToken() { }

    public RevokedAccessToken(RevokedAccessTokenId id, User user, Instant revokedAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public RevokedAccessTokenId getId() { return id; }
    public User getUser() { return user; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
