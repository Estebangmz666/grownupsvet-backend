package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

/** Credential activation and administrative availability are account states, not roles. */
public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED
}
