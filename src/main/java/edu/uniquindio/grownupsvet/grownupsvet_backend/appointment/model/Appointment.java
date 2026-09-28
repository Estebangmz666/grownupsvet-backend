package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "pet_id", nullable = false) private UUID petId;
    @Column(name = "current_slot_id", nullable = false) private UUID currentSlotId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AppointmentStatus status;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;

    protected Appointment() { }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getPetId() { return petId; }
    public UUID getCurrentSlotId() { return currentSlotId; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
