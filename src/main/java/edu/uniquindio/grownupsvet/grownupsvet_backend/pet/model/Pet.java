package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Pet identity and ownership survive archiving. API callers only receive dedicated DTOs. */
@Entity
@Table(name = "pets")
public class Pet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private OwnerProfile owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PetSpecies species;

    @Column(length = 100)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private PetSex sex;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "date_of_birth_estimated", nullable = false)
    private boolean dateOfBirthEstimated;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Pet() { }

    public Pet(OwnerProfile owner, String name, PetSpecies species, String breed, PetSex sex,
               LocalDate dateOfBirth, boolean dateOfBirthEstimated, Instant createdAt) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updateDetails(name, species, breed, sex, dateOfBirth, dateOfBirthEstimated, true, createdAt);
    }

    /** The service validates the complete proposed state before changing any field. */
    public void updateDetails(String name, PetSpecies species, String breed, PetSex sex, LocalDate dateOfBirth,
                              boolean dateOfBirthEstimated, boolean active, Instant updatedAt) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.species = Objects.requireNonNull(species, "species must not be null");
        this.breed = breed;
        this.sex = sex;
        this.dateOfBirth = dateOfBirth;
        this.dateOfBirthEstimated = dateOfBirthEstimated;
        this.active = active;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return owner.getUserId(); }
    public String getName() { return name; }
    public PetSpecies getSpecies() { return species; }
    public String getBreed() { return breed; }
    public PetSex getSex() { return sex; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public boolean isDateOfBirthEstimated() { return dateOfBirthEstimated; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() { return "Pet[id=" + id + ", active=" + active + "]"; }
}
