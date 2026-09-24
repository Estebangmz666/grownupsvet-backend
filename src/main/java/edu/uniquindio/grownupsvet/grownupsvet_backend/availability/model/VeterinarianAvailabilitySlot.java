package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "veterinarian_availability_slots")
public class VeterinarianAvailabilitySlot {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "veterinarian_id", nullable = false, updatable = false) private UUID veterinarianId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AvailabilitySlotStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;

    protected VeterinarianAvailabilitySlot() { }

    public VeterinarianAvailabilitySlot(UUID veterinarianId, Instant startsAt, Instant endsAt, UUID actorId, Instant now) {
        this.id = UUID.randomUUID();
        this.veterinarianId = veterinarianId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = AvailabilitySlotStatus.PUBLISHED;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void reschedule(Instant newStartsAt, Instant newEndsAt, UUID actorId, Instant now) {
        startsAt = newStartsAt; endsAt = newEndsAt; updatedBy = actorId; updatedAt = now;
    }

    public void changeStatus(AvailabilitySlotStatus newStatus, UUID actorId, Instant now) {
        status = newStatus; updatedBy = actorId; updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getVeterinarianId() { return veterinarianId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public AvailabilitySlotStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
