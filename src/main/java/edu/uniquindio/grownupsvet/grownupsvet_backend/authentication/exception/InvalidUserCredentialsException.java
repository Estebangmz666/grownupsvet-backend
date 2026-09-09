package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.exception;

import org.springframework.security.core.AuthenticationException;

/** Public authentication failure that deliberately does not identify which credential failed. */
public class InvalidUserCredentialsException extends AuthenticationException {
    public InvalidUserCredentialsException() {
        super("Invalid user credentials.");
    }
}
