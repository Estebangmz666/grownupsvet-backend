package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model;

import java.util.UUID;

/** Wakes delivery after commit; sensitive content remains encrypted in PostgreSQL. */
public record PasswordRecoveryEmailsQueuedEvent(UUID taskId) { }
