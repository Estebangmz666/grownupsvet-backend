package edu.uniquindio.grownupsvet.grownupsvet_backend.user.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserSignupResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.EmailAlreadyRegisteredException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperation;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability.BusinessOperationCommittedEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

@Service
public class UserSignupService {
    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean privacyAuthorizationRequired;

    public UserSignupService(UserRepository userRepository, OwnerProfileRepository ownerProfileRepository,
                             PasswordEncoder passwordEncoder, ApplicationEventPublisher events, Clock clock,
                             @Value("${grownupsvet.registration.privacy-authorization-required:false}")
                             boolean privacyAuthorizationRequired) {
        this.userRepository = userRepository;
        this.ownerProfileRepository = ownerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.privacyAuthorizationRequired = privacyAuthorizationRequired;
    }

    /** Saves the account and owner profile atomically, without granting a session. */
    @Transactional
    public UserSignupResponseDTO signup(UserSignupRequestDTO request) {
        String privacyNoticeVersion = request.privacyNoticeVersion();
        if (privacyAuthorizationRequired && privacyNoticeVersion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere autorizar el tratamiento de datos.");
        }
        String normalizedEmail = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), UserRole.OWNER);
        try {
            userRepository.saveAndFlush(user);
            ownerProfileRepository.saveAndFlush(new OwnerProfile(user, request.fullName(),
                    request.dateOfBirth(), request.phoneNumber(), privacyNoticeVersion,
                    privacyNoticeVersion == null ? null : clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                // Handles the race after existsByEmail. The transaction will roll back.
                // Do not include the SQL exception, email or hash in the public exception.
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
        events.publishEvent(new BusinessOperationCommittedEvent(BusinessOperation.OWNER_SIGNUP));
        return new UserSignupResponseDTO(user.getId(), user.getEmail());
    }

    private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && "23505".equals(constraintViolation.getSQLState())
                    && "uk_users_email".equals(constraintViolation.getConstraintName())) {
                return true;
            }
            // Hibernate may not extract a name from a localized PostgreSQL diagnostic.
            // Use the driver's structured metadata, never match or expose the SQL message.
            if (cause instanceof PSQLException postgresException
                    && "23505".equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null
                    && "uk_users_email".equals(postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}
