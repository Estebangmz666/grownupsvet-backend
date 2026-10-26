package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.exception;

public class LoginAttemptRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginAttemptRateLimitException(long retryAfterSeconds) {
        super("Login admission limit reached.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
