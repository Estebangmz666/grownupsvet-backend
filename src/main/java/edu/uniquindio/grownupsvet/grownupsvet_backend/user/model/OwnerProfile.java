package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Owner-only personal data, independent of professional account requirements. */
@Entity
@Table(name = "owner_profiles")
public class OwnerProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @Column(name = "privacy_notice_version", updatable = false, length = 10)
    private String privacyNoticeVersion;

    @Column(name = "privacy_authorized_at", updatable = false)
    private Instant privacyAuthorizedAt;

    protected OwnerProfile() {
        // Required by JPA.
    }

    public OwnerProfile(User user, String fullName, LocalDate dateOfBirth, String phoneNumber) {
        this(user, fullName, dateOfBirth, phoneNumber, null, null);
    }

    public OwnerProfile(User user, String fullName, LocalDate dateOfBirth, String phoneNumber,
                        String privacyNoticeVersion, Instant privacyAuthorizedAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null").strip();
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");
        this.privacyNoticeVersion = privacyNoticeVersion;
        this.privacyAuthorizedAt = privacyAuthorizedAt;
    }

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getPhoneNumber() { return phoneNumber; }

    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");
    }

    @Override
    public String toString() { return "OwnerProfile[userId=" + userId + "]"; }
}
