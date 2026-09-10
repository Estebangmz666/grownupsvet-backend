package edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception;

public final class InvalidProfilePhotoException extends RuntimeException {
    public enum Reason { EMPTY, TOO_LARGE, UNSUPPORTED_TYPE, INVALID_CONTENT, INVALID_DIMENSIONS }

    private final Reason reason;

    public InvalidProfilePhotoException(Reason reason) {
        this.reason = reason;
    }

    public InvalidProfilePhotoException(Reason reason, Throwable cause) {
        super(cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
