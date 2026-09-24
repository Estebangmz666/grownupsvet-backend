package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model;

import java.util.UUID;

/** An optimization for delivery latency; PostgreSQL remains the authoritative queue. */
public record StaffInvitationCreatedEvent(UUID invitationId) { }
