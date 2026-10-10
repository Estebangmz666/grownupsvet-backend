package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.configuration.PasswordRecoveryProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordRecoveryVerificationResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.dto.UserPasswordResetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.email.PasswordRecoveryEmailSender;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.exception.PasswordRecoveryException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.model.PasswordRecoveryChallenge;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryEmailTaskRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.exception.PasswordRecoveryException.Reason;

@Service
public class UserPasswordRecoveryService {
    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    public static final Duration RESET_LIFETIME = Duration.ofMinutes(5);
    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryRepository repository;
    private final UserRepository users;
    private final OwnerProfileRepository ownerProfiles;
    private final PasswordEncoder encoder;
    private final PasswordRecoverySecrets secrets;
    private final PasswordRecoveryEmailOutbox outbox;
    private final PasswordRecoveryEmailTaskRepository emailTasks;
    private final ObjectProvider<PasswordRecoveryEmailSender> senderProvider;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public UserPasswordRecoveryService(PasswordRecoveryProperties properties, PasswordRecoveryRepository repository,
            UserRepository users, OwnerProfileRepository ownerProfiles, PasswordEncoder encoder,
            PasswordRecoverySecrets secrets, PasswordRecoveryEmailOutbox outbox,
            PasswordRecoveryEmailTaskRepository emailTasks,
            ObjectProvider<PasswordRecoveryEmailSender> senderProvider, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.repository = repository;
        this.users = users;
        this.ownerProfiles = ownerProfiles;
        this.encoder = encoder;
        this.secrets = secrets;
        this.outbox = outbox;
        this.emailTasks = emailTasks;
        this.senderProvider = senderProvider;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void requestCode(String rawEmail, String clientAddress) {
        requireAvailable();
        String email = User.normalizeEmail(rawEmail);
        Outcome<Void> outcome = transactions.execute(status -> {
            Instant now = clock.instant();
            long wait = rate("request-ip", clientAddress, 30, Duration.ZERO, now);
            if (wait > 0) { return Outcome.rateLimited(wait); }
            wait = rate("request-email", email, 5, Duration.ofSeconds(60), now);
            if (wait > 0) { return Outcome.rateLimited(wait); }
            // Shared durable capacity is checked before account lookup, for every address.
            if (!outbox.hasCapacity()) { return Outcome.failure(Reason.UNAVAILABLE); }
            UUID challengeId = UUID.randomUUID();
            String code = secrets.newCode();
            byte[] digest = secrets.codeDigest(challengeId, code);
            User user = users.findByEmailForUpdate(email).orElse(null);
            if (user == null || !user.isActive()) { return Outcome.success(null); }
            Instant createdAt = clock.instant();
            emailTasks.supersedeRecoveryCodes(user.getId(), createdAt);
            repository.replaceChallenge(user.getId(), challengeId, email, user.getAuthenticationVersion(),
                    digest, createdAt.plus(CODE_LIFETIME), createdAt);
            outbox.enqueueCode(user.getId(), challengeId, user.getAuthenticationVersion(), email,
                    displayName(user), code, createdAt.plus(CODE_LIFETIME), createdAt);
            return Outcome.success(null);
        });
        throwIfFailed(outcome);
    }

    public UserPasswordRecoveryVerificationResponseDTO verifyCode(String rawEmail, String code, String clientAddress) {
        requireAvailable();
        String email = User.normalizeEmail(rawEmail);
        Outcome<UserPasswordRecoveryVerificationResponseDTO> outcome = transactions.execute(status -> {
            Instant now = clock.instant();
            long wait = rate("verify-ip", clientAddress, 100, Duration.ZERO, now);
            if (wait > 0) { return Outcome.rateLimited(wait); }
            // This independent bucket survives code resends and remains shared across client IPs.
            wait = rate("verify-email", email, 10, Duration.ZERO, now);
            if (wait > 0) { return Outcome.rateLimited(wait); }
            User user = users.findByEmailForUpdate(email).orElse(null);
            if (user == null || !user.isActive()) { return Outcome.failure(Reason.INVALID_CODE); }
            PasswordRecoveryChallenge challenge = repository.findForUser(user.getId()).orElse(null);
            Instant verifiedAt = clock.instant();
            if (!isCurrent(challenge, user) || challenge.codeDigest() == null
                    || challenge.failedAttempts() >= 5 || !verifiedAt.isBefore(challenge.codeExpiresAt())) {
                return Outcome.failure(Reason.INVALID_CODE);
            }
            if (!secrets.matchesCode(challenge.challengeId(), code, challenge.codeDigest())) {
                repository.recordFailedAttempt(user.getId(), verifiedAt);
                return Outcome.failure(Reason.INVALID_CODE);
            }
            String token = secrets.newResetToken();
            repository.grantReset(user.getId(), secrets.resetTokenHash(token), verifiedAt.plus(RESET_LIFETIME), verifiedAt);
            emailTasks.supersedeRecoveryCodes(user.getId(), verifiedAt);
            return Outcome.success(new UserPasswordRecoveryVerificationResponseDTO(token, RESET_LIFETIME.toSeconds()));
        });
        // Exceptions are raised after commit, retaining failed attempts and distributed rate counters.
        throwIfFailed(outcome);
        return outcome.value();
    }

    public void resetPassword(UserPasswordResetRequestDTO request, String clientAddress) {
        requireAvailable();
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new PasswordRecoveryException(Reason.PASSWORD_CONFIRMATION_MISMATCH);
        }
        String tokenHash = secrets.resetTokenHash(request.resetToken());
        Outcome<Void> outcome = transactions.execute(status -> {
            Instant now = clock.instant();
            long wait = rate("reset-ip", clientAddress, 100, Duration.ZERO, now);
            if (wait > 0) { return Outcome.rateLimited(wait); }
            // Keep admission before account locks, matching requests and avoiding inverted locks.
            boolean notificationAdmitted = outbox.hasCapacity();
            UUID userId = repository.findUserForResetToken(tokenHash).orElse(null);
            if (userId == null) { return Outcome.failure(Reason.INVALID_RESET_TOKEN); }
            User user = users.findByIdForUpdate(userId).orElse(null);
            PasswordRecoveryChallenge challenge = repository.findForUser(userId).orElse(null);
            Instant verifiedAt = clock.instant();
            if (user == null || !user.isActive() || !isCurrent(challenge, user)
                    || !tokenHash.equals(challenge.resetTokenHash()) || challenge.resetExpiresAt() == null
                    || !verifiedAt.isBefore(challenge.resetExpiresAt())) {
                return Outcome.failure(Reason.INVALID_RESET_TOKEN);
            }
            user.resetPasswordHash(encoder.encode(request.newPassword()));
            users.flush();
            repository.deleteForUser(userId);
            emailTasks.supersedeRecoveryCodes(userId, verifiedAt);
            // A full notification queue records a terminal failed intent without undoing the reset.
            outbox.enqueueResetNotification(userId, challenge.challengeId(), user.getAuthenticationVersion(),
                    user.getEmail(), displayName(user), verifiedAt, notificationAdmitted);
            return Outcome.success(null);
        });
        throwIfFailed(outcome);
    }

    private boolean isCurrent(PasswordRecoveryChallenge challenge, User user) {
        return challenge != null && challenge.authenticationVersion() == user.getAuthenticationVersion()
                && challenge.emailAtRequest().equals(user.getEmail());
    }

    private long rate(String purpose, String identifier, int maximum, Duration interval, Instant now) {
        return repository.consumeRateLimit(secrets.rateLimitKey(purpose, identifier), maximum, interval, now);
    }

    private String displayName(User user) {
        return ownerProfiles.findById(user.getId()).map(OwnerProfile::getFullName).orElse(null);
    }

    private void requireAvailable() {
        if (!properties.enabled() || senderProvider.getIfAvailable() == null) {
            throw new PasswordRecoveryException(Reason.UNAVAILABLE);
        }
    }

    private void throwIfFailed(Outcome<?> outcome) {
        if (outcome.failure() != null) {
            throw new PasswordRecoveryException(outcome.failure(), outcome.retryAfterSeconds());
        }
    }

    private record Outcome<T>(T value, Reason failure, long retryAfterSeconds) {
        static <T> Outcome<T> success(T value) { return new Outcome<>(value, null, 0); }
        static <T> Outcome<T> failure(Reason failure) { return new Outcome<>(null, failure, 0); }
        static <T> Outcome<T> rateLimited(long wait) { return new Outcome<>(null, Reason.RATE_LIMITED, wait); }
        @Override public String toString() { return "RecoveryOutcome[contents=[REDACTED]]"; }
    }
}
