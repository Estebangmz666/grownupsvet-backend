package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RevokedAccessTokenId implements Serializable {
    @Column(nullable = false, length = 200)
    private String issuer;

    @Column(name = "jwt_id", nullable = false, length = 100)
    private String jwtId;

    protected RevokedAccessTokenId() { }

    public RevokedAccessTokenId(String issuer, String jwtId) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.jwtId = Objects.requireNonNull(jwtId, "jwtId must not be null");
    }

    public String getIssuer() { return issuer; }
    public String getJwtId() { return jwtId; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RevokedAccessTokenId that
                && issuer.equals(that.issuer) && jwtId.equals(that.jwtId);
    }

    @Override
    public int hashCode() { return Objects.hash(issuer, jwtId); }
}
