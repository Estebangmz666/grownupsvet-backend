package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.configuration.StaffInvitationProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto.StaffAccountActivationRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.dto.StaffInvitationResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitationCreatedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.repository.StaffInvitationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class StaffInvitationService {
    private static final Duration INVITATION_LIFETIME = Duration.ofHours(48);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);
    private final UserRepository users;
    private final StaffInvitationRepository invitations;
    private final StaffInvitationSecrets secrets;
    private final StaffInvitationProperties properties;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public StaffInvitationService(UserRepository users, StaffInvitationRepository invitations,
            StaffInvitationSecrets secrets, StaffInvitationProperties properties, PasswordEncoder encoder,
            Clock clock, ApplicationEventPublisher events) {
        this.users = users;
        this.invitations = invitations;
        this.secrets = secrets;
        this.properties = properties;
        this.encoder = encoder;
        this.clock = clock;
        this.events = events;
    }

    /** Joins staff creation so an account cannot commit without its durable invitation. */
    @Transactional
    public void createInvitation(User user, UUID actorId) {
        requireEnabled();
        authorize(actorId, user);
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION || user.getPasswordHash() != null) {
            throw conflict();
        }
        issue(user, actorId);
    }

    @Transactional
    public StaffInvitationResponseDTO resendInvitation(UUID userId, UUID actorId) {
        requireEnabled();
        User actor = lockActor(actorId);
        User user = users.findByIdForUpdate(userId).orElseThrow(this::notFound);
        authorize(actor, user);
        if (user.getPasswordHash() != null || user.getStatus() == UserStatus.ACTIVE) {
            throw conflict();
        }
        Instant now = clock.instant();
        if (invitations.latestInvitationAt(userId).filter(last -> now.isBefore(last.plus(RESEND_COOLDOWN))).isPresent()
                || invitations.invitationsSince(userId, now.minus(QUOTA_WINDOW)) >= 5) {
            throw new StaffOperationException(HttpStatus.TOO_MANY_REQUESTS, "STAFF_INVITATION_RATE_LIMITED",
                    "Espera antes de solicitar otra invitación. Se permiten cinco invitaciones por cuenta en 24 horas, con un minuto entre solicitudes.");
        }
        invalidateInvitations(userId);
        user.setPendingActivation();
        return issue(user, actorId);
    }

    @Transactional
    public void cancelInvitation(UUID userId, UUID actorId) {
        User actor = lockActor(actorId);
        User user = users.findByIdForUpdate(userId).orElseThrow(this::notFound);
        authorize(actor, user);
        if (user.getPasswordHash() != null || user.getStatus() == UserStatus.ACTIVE) { throw conflict(); }
        invalidateInvitations(userId);
        user.deactivate();
    }

    /** The caller owns the target User write lock; cancellation follows User -> invitation -> task. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateInvitations(UUID userId) {
        invitations.cancelPendingInvitations(userId, clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateInvitations(User user) { invalidateInvitations(user.getId()); }

    @Transactional
    public void activateAccount(StaffAccountActivationRequestDTO request) {
        requireEnabled();
        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new StaffOperationException(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH",
                    "La confirmación debe coincidir exactamente con la contraseña.");
        }
        StaffInvitation observed = invitations.findByTokenHash(secrets.hash(request.token())).orElseThrow(this::invalidToken);
        User user = users.findByIdForUpdate(observed.userId()).orElseThrow(this::invalidToken);
        StaffInvitation invitation = invitations.findByIdForUpdate(observed.id()).orElseThrow(this::invalidToken);
        Instant now = clock.instant();
        if (!"PENDING".equals(invitation.status()) || !now.isBefore(invitation.expiresAt())
                || user.getStatus() != UserStatus.PENDING_ACTIVATION || user.getPasswordHash() != null
                || (user.getRole() != UserRole.ADMINISTRATOR && user.getRole() != UserRole.VETERINARIAN)) {
            throw invalidToken();
        }
        user.activateWithPasswordHash(encoder.encode(request.password()));
        invitations.consume(invitation.id(), now);
    }

    private StaffInvitationResponseDTO issue(User user, UUID actorId) {
        Instant now = clock.instant();
        UUID invitationId = UUID.randomUUID();
        String token = secrets.generateToken();
        StaffInvitation invitation = new StaffInvitation(invitationId, user.getId(), secrets.hash(token), "PENDING",
                now, now.plus(INVITATION_LIFETIME));
        invitations.create(invitation, actorId, secrets.encrypt(invitationId, token));
        events.publishEvent(new StaffInvitationCreatedEvent(invitationId));
        return new StaffInvitationResponseDTO(invitationId, user.getId(), invitation.expiresAt(), "PENDING");
    }

    private void authorize(UUID actorId, User target) { authorize(lockActor(actorId), target); }

    private User lockActor(UUID actorId) {
        User actor = users.findByIdForUpdate(actorId).orElseThrow(this::forbidden);
        if (!actor.isActive() || actor.getStatus() != UserStatus.ACTIVE
                || (actor.getRole() != UserRole.SUPER_ADMIN && actor.getRole() != UserRole.ADMINISTRATOR)) {
            throw forbidden();
        }
        return actor;
    }

    private void authorize(User actor, User target) {
        if (!((actor.getRole() == UserRole.SUPER_ADMIN && target.getRole() == UserRole.ADMINISTRATOR)
                || (actor.getRole() == UserRole.ADMINISTRATOR && target.getRole() == UserRole.VETERINARIAN))) {
            throw forbidden();
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new StaffOperationException(HttpStatus.SERVICE_UNAVAILABLE, "STAFF_INVITATIONS_UNAVAILABLE",
                    "Las invitaciones de personal no están disponibles en este momento.");
        }
    }

    private StaffOperationException forbidden() {
        return new StaffOperationException(HttpStatus.FORBIDDEN, "STAFF_OPERATION_FORBIDDEN", "No tienes permiso para administrar esta invitación.");
    }
    private StaffOperationException notFound() {
        return new StaffOperationException(HttpStatus.NOT_FOUND, "STAFF_MEMBER_NOT_FOUND", "No se encontró la cuenta de personal.");
    }
    private StaffOperationException conflict() {
        return new StaffOperationException(HttpStatus.CONFLICT, "STAFF_INVITATION_STATE_CONFLICT", "La cuenta no admite una invitación de activación.");
    }
    private StaffOperationException invalidToken() {
        return new StaffOperationException(HttpStatus.BAD_REQUEST, "STAFF_INVITATION_INVALID", "El enlace de activación es inválido, venció o ya fue utilizado.");
    }
}
