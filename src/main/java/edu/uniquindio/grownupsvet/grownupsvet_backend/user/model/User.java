package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.Assert;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent account shared by owners, veterinarians and administrators.
 * A service must encode passwords before constructing or updating an account.
 * API responses must use dedicated DTOs rather than exposing this entity.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class    User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {
        // Required by JPA.
    }

    /** Accepts an already encoded password; never pass a raw password here. */
    public User(String email, String passwordHash, UserRole role) {
        changeEmail(email);
        changePasswordHash(passwordHash);
        changeRole(role);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    /** Login identifiers are stored without surrounding whitespace and in lowercase. */
    public void changeEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Assert.isTrue(normalizedEmail.length() <= 254, "email must not exceed 254 characters");
        this.email = normalizedEmail;
    }

    /** Applies the same canonical form for registration, login and persistence lookups. */
    public static String normalizeEmail(String email) {
        Assert.hasText(email, "email must not be blank");
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePasswordHash(String passwordHash) {
        Assert.hasText(passwordHash, "passwordHash must not be blank");
        Assert.isTrue(passwordHash.length() <= 255, "passwordHash must not exceed 255 characters");
        this.passwordHash = passwordHash;
    }

    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", role=" + role + ", active=" + active + "]";
    }
}
