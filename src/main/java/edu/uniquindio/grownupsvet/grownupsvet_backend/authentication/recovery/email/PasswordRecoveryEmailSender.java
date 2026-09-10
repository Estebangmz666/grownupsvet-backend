package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email;

public interface PasswordRecoveryEmailSender {
    void sendRecoveryCode(String email, String displayName, String code);

    void sendPasswordResetNotification(String email, String displayName);
}
