package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model;

/** Internal encrypted delivery content, never an API response or diagnostic value. */
public record PasswordRecoveryEmailPayload(String email, String displayName, String code) {
    @Override public String toString() { return "PasswordRecoveryEmailPayload[contents=[REDACTED]]"; }
}
