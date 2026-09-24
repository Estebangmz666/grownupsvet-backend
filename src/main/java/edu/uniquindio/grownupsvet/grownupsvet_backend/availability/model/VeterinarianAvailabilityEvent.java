package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "veterinarian_availability_events")
public class VeterinarianAvailabilityEvent {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "slot_id", nullable = false, updatable = false) private UUID slotId;
    @Column(name = "slot_version", nullable = false, updatable = false) private long slotVersion;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, updatable = false, length = 16) private AvailabilityEventType eventType;
    @Column(name = "previous_starts_at", updatable = false) private Instant previousStartsAt;
    @Column(name = "previous_ends_at", updatable = false) private Instant previousEndsAt;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", updatable = false, length = 16) private AvailabilitySlotStatus previousStatus;
    @Column(name = "new_starts_at", nullable = false, updatable = false) private Instant newStartsAt;
    @Column(name = "new_ends_at", nullable = false, updatable = false) private Instant newEndsAt;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, updatable = false, length = 16) private AvailabilitySlotStatus newStatus;
    @Column(length = 500, updatable = false) private String reason;

    protected VeterinarianAvailabilityEvent() { }

    public VeterinarianAvailabilityEvent(VeterinarianAvailabilitySlot slot, UUID actorId, Instant now,
            AvailabilityEventType eventType, Instant previousStartsAt, Instant previousEndsAt,
            AvailabilitySlotStatus previousStatus, String reason) {
        id = UUID.randomUUID(); slotId = slot.getId(); slotVersion = slot.getVersion(); this.actorId = actorId;
        occurredAt = now; this.eventType = eventType; this.previousStartsAt = previousStartsAt;
        this.previousEndsAt = previousEndsAt; this.previousStatus = previousStatus;
        newStartsAt = slot.getStartsAt(); newEndsAt = slot.getEndsAt(); newStatus = slot.getStatus(); this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getSlotId() { return slotId; }
    public long getSlotVersion() { return slotVersion; }
    public UUID getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public AvailabilityEventType getEventType() { return eventType; }
    public Instant getPreviousStartsAt() { return previousStartsAt; }
    public Instant getPreviousEndsAt() { return previousEndsAt; }
    public AvailabilitySlotStatus getPreviousStatus() { return previousStatus; }
    public Instant getNewStartsAt() { return newStartsAt; }
    public Instant getNewEndsAt() { return newEndsAt; }
    public AvailabilitySlotStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
}
