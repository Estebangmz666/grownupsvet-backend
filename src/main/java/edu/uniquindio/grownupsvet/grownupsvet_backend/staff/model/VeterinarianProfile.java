package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "veterinarian_profiles")
public class VeterinarianProfile {
    @Id @Column(name = "user_id") private UUID userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false) private User user;
    @Column(name = "full_name", nullable = false, length = 150) private String fullName;
    @Column(name = "professional_phone_number", nullable = false, length = 16) private String professionalPhoneNumber;
    @Column(name = "professional_registration_number", nullable = false, length = 50) private String professionalRegistrationNumber;
    @Column(length = 2000) private String biography;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;

    protected VeterinarianProfile() { }
    public VeterinarianProfile(User user, String fullName, String phone, String registration, String biography, UUID actorId, Instant now) {
        this.user = user; this.createdAt = now; this.createdBy = actorId;
        update(fullName, phone, registration, biography, actorId, now);
    }
    public void update(String fullName, String phone, String registration, String biography, UUID actorId, Instant now) {
        this.fullName = fullName.strip(); this.professionalPhoneNumber = phone;
        this.professionalRegistrationNumber = normalizeRegistration(registration);
        this.biography = biography == null || biography.isBlank() ? null : biography.strip();
        touch(actorId, now);
    }
    public void touch(UUID actorId, Instant now) { this.updatedBy = actorId; this.updatedAt = now; }
    public static String normalizeRegistration(String registration) { return registration.strip().toUpperCase(Locale.ROOT); }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getFullName() { return fullName; }
    public String getProfessionalPhoneNumber() { return professionalPhoneNumber; }
    public String getProfessionalRegistrationNumber() { return professionalRegistrationNumber; }
    public String getBiography() { return biography; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
