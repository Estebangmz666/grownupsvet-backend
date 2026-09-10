package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.exception;

public final class PasswordRecoveryException extends RuntimeException {
    public enum Reason {
        UNAVAILABLE, RATE_LIMITED, INVALID_CODE, INVALID_RESET_TOKEN, PASSWORD_CONFIRMATION_MISMATCH
    }

    private final Reason reason;
    private final long retryAfterSeconds;

    public PasswordRecoveryException(Reason reason) {
        this(reason, 0);
    }

    public PasswordRecoveryException(Reason reason, long retryAfterSeconds) {
        super("Password recovery request could not be completed: " + reason.name());
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Reason getReason() { return reason; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
