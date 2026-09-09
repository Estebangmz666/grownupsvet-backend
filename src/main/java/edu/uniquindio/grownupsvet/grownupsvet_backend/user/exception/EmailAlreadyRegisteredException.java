package edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception;

/** A public registration conflicts with an existing normalized email. */
public final class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("An account is already registered with this email.");
    }
}
