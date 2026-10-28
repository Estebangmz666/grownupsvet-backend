package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.model;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;

import java.util.UUID;

/** Current account and token revocation state from one database statement. */
public record CurrentAccountAuthenticationSnapshot(UUID userId, String email, UserRole role,
                                                   boolean active, long authenticationVersion,
                                                   boolean revoked) { }
