package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "administrator_profiles")
public class AdministratorProfile {
    @Id @Column(name = "user_id") private UUID userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false) private User user;
    @Column(name = "full_name", nullable = false, length = 150) private String fullName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;

    protected AdministratorProfile() { }
    public AdministratorProfile(User user, String fullName, UUID actorId, Instant now) {
        this.user = user; this.createdAt = now; this.createdBy = actorId;
        update(fullName, actorId, now);
    }
    public void update(String fullName, UUID actorId, Instant now) {
        this.fullName = fullName.strip(); this.updatedBy = actorId; this.updatedAt = now;
    }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getFullName() { return fullName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
