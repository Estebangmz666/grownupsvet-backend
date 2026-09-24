package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitation(UUID id, UUID userId, String tokenHash, String status,
                              Instant createdAt, Instant expiresAt) {
    @Override public String toString() { return "StaffInvitation[id=" + id + ", status=" + status + "]"; }
}
