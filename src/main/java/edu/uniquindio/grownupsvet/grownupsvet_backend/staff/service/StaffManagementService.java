package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.EmailAlreadyRegisteredException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StaffManagementService {
    private static final int MAX_QUALIFICATIONS = 20;
    private final UserRepository users;
    private final AdministratorProfileRepository administrators;
    private final VeterinarianProfileRepository veterinarians;
    private final VeterinarianQualificationRepository qualifications;
    private final VeterinarianDiplomaRepository diplomas;
    private final UserProfilePhotoRepository photos;
    private final StaffInvitationService invitations;
    private final DiplomaPdfValidator diplomaValidator;
    private final Clock clock;

    public StaffManagementService(UserRepository users, AdministratorProfileRepository administrators,
            VeterinarianProfileRepository veterinarians, VeterinarianQualificationRepository qualifications,
            VeterinarianDiplomaRepository diplomas, UserProfilePhotoRepository photos,
            StaffInvitationService invitations, DiplomaPdfValidator diplomaValidator, Clock clock) {
        this.users = users; this.administrators = administrators; this.veterinarians = veterinarians;
        this.qualifications = qualifications; this.diplomas = diplomas; this.photos = photos;
        this.invitations = invitations; this.diplomaValidator = diplomaValidator; this.clock = clock;
    }

    public AdministratorResponseDTO createAdministrator(UUID actorId, CreateAdministratorRequestDTO request) {
        requireActor(actorId, UserRole.SUPER_ADMIN, true);
        User user = createPendingAccount(request.email(), UserRole.ADMINISTRATOR);
        AdministratorProfile profile = administrators.saveAndFlush(
                new AdministratorProfile(user, request.fullName(), actorId, clock.instant()));
        invitations.createInvitation(user, actorId);
        return administratorResponse(profile);
    }

    public VeterinarianResponseDTO createVeterinarian(UUID actorId, CreateVeterinarianRequestDTO request) {
        requireActor(actorId, UserRole.ADMINISTRATOR, true);
        String registration = VeterinarianProfile.normalizeRegistration(request.professionalRegistrationNumber());
        if (veterinarians.existsByProfessionalRegistrationNumber(registration)) { throw registrationConflict(); }
        User user = createPendingAccount(request.email(), UserRole.VETERINARIAN);
        VeterinarianProfile profile = new VeterinarianProfile(user, request.fullName(),
                request.professionalPhoneNumber(), registration, request.biography(), actorId, clock.instant());
        saveVeterinarian(profile);
        qualifications.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                request.baseDegreeTitle(), request.baseDegreeInstitution(), null, true, clock.instant()));
        invitations.createInvitation(user, actorId);
        return veterinarianResponse(profile);
    }

    @Transactional(readOnly = true)
    public AdministratorPageResponseDTO listAdministrators(UUID actorId, int page, int size, UserStatus status) {
        requireActor(actorId, UserRole.SUPER_ADMIN, false);
        Pageable pageable = pageable(page, size);
        Page<AdministratorProfile> result = status == null ? administrators.findAll(pageable)
                : administrators.findByUserStatus(status, pageable);
        return new AdministratorPageResponseDTO(result.map(this::administratorResponse).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public VeterinarianPageResponseDTO listVeterinarians(UUID actorId, int page, int size, UserStatus status) {
        requireActor(actorId, UserRole.ADMINISTRATOR, false);
        Pageable pageable = pageable(page, size);
        Page<VeterinarianProfile> result = status == null ? veterinarians.findAll(pageable)
                : veterinarians.findByUserStatus(status, pageable);
        return new VeterinarianPageResponseDTO(result.map(this::veterinarianResponse).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdministratorResponseDTO getAdministrator(UUID actorId, UUID id) {
        requireActor(actorId, UserRole.SUPER_ADMIN, false);
        return administratorResponse(administrator(id));
    }

    @Transactional(readOnly = true)
    public VeterinarianResponseDTO getVeterinarian(UUID actorId, UUID id) {
        requireActor(actorId, UserRole.ADMINISTRATOR, false);
        return veterinarianResponse(veterinarian(id));
    }

    public AdministratorResponseDTO updateAdministrator(UUID actorId, UUID id, UpdateAdministratorRequestDTO request) {
        requireActor(actorId, UserRole.SUPER_ADMIN, true);
        lockTarget(id, UserRole.ADMINISTRATOR);
        AdministratorProfile profile = administrator(id);
        profile.update(request.fullName(), actorId, clock.instant());
        return administratorResponse(profile);
    }

    public VeterinarianResponseDTO updateVeterinarian(UUID actorId, UUID id, UpdateVeterinarianRequestDTO request) {
        requireActor(actorId, UserRole.ADMINISTRATOR, true);
        lockTarget(id, UserRole.VETERINARIAN);
        VeterinarianProfile profile = veterinarian(id);
        profile.update(request.fullName(), request.professionalPhoneNumber(), request.professionalRegistrationNumber(),
                request.biography(), actorId, clock.instant());
        saveVeterinarian(profile);
        VeterinarianQualification baseDegree = baseDegree(id);
        boolean academicDetailsChanged = !baseDegree.getTitle().equals(request.baseDegreeTitle().strip())
                || !baseDegree.getInstitution().equals(request.baseDegreeInstitution().strip());
        baseDegree.update(QualificationType.UNDERGRADUATE, request.baseDegreeTitle(), request.baseDegreeInstitution(),
                baseDegree.getGraduationYear(), clock.instant());
        if (academicDetailsChanged) { baseDegree.publishDiploma(false, clock.instant()); }
        return veterinarianResponse(profile);
    }

    public AdministratorResponseDTO updateAdministratorStatus(UUID actorId, UUID id, UpdateStaffStatusRequestDTO request) {
        requireActor(actorId, UserRole.SUPER_ADMIN, true);
        User user = lockTarget(id, UserRole.ADMINISTRATOR);
        updateStatus(user, request.status());
        AdministratorProfile profile = administrator(id);
        profile.update(profile.getFullName(), actorId, clock.instant());
        return administratorResponse(profile);
    }

    public VeterinarianResponseDTO updateVeterinarianStatus(UUID actorId, UUID id, UpdateStaffStatusRequestDTO request) {
        requireActor(actorId, UserRole.ADMINISTRATOR, true);
        User user = lockTarget(id, UserRole.VETERINARIAN);
        updateStatus(user, request.status());
        VeterinarianProfile profile = veterinarian(id);
        profile.touch(actorId, clock.instant());
        return veterinarianResponse(profile);
    }

    public VeterinarianQualificationResponseDTO createQualification(UUID actorId, UUID veterinarianId,
            VeterinarianQualificationRequestDTO request) {
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        validateGraduationYear(request.graduationYear());
        if (qualifications.countByVeterinarianUserId(veterinarianId) >= MAX_QUALIFICATIONS) {
            throw failure(HttpStatus.CONFLICT, "QUALIFICATION_LIMIT_REACHED", "Se permiten hasta 20 títulos por veterinario, incluido el título base.");
        }
        VeterinarianQualification qualification = qualifications.saveAndFlush(new VeterinarianQualification(profile,
                request.type(), request.title(), request.institution(), request.graduationYear(), false, clock.instant()));
        profile.touch(actorId, clock.instant());
        return qualificationResponse(qualification, false);
    }

    @Transactional(readOnly = true)
    public List<VeterinarianQualificationResponseDTO> listQualifications(UUID actorId, UUID veterinarianId) {
        requireActor(actorId, UserRole.ADMINISTRATOR, false);
        veterinarian(veterinarianId);
        return qualificationResponses(veterinarianId, false);
    }

    public VeterinarianQualificationResponseDTO updateQualification(UUID actorId, UUID veterinarianId, UUID qualificationId,
            VeterinarianQualificationRequestDTO request) {
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        validateGraduationYear(request.graduationYear());
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        if (qualification.isBaseDegree() && request.type() != QualificationType.UNDERGRADUATE) {
            throw failure(HttpStatus.CONFLICT, "BASE_DEGREE_REQUIRED", "El título base debe conservar el tipo de pregrado.");
        }
        boolean changed = qualification.getType() != request.type()
                || !qualification.getTitle().equals(request.title().strip())
                || !qualification.getInstitution().equals(request.institution().strip())
                || !java.util.Objects.equals(qualification.getGraduationYear(), request.graduationYear());
        qualification.update(request.type(), request.title(), request.institution(), request.graduationYear(), clock.instant());
        if (changed) { qualification.publishDiploma(false, clock.instant()); }
        profile.touch(actorId, clock.instant());
        return qualificationResponse(qualification, false);
    }

    @Transactional(readOnly = true)
    public VeterinarianQualificationResponseDTO getQualification(UUID actorId, UUID veterinarianId, UUID qualificationId) {
        requireActor(actorId, UserRole.ADMINISTRATOR, false);
        veterinarian(veterinarianId);
        return qualificationResponse(qualification(veterinarianId, qualificationId), false);
    }

    public VeterinarianQualificationResponseDTO updateDiplomaPublication(UUID actorId, UUID veterinarianId,
            UUID qualificationId, UpdateDiplomaPublicationRequestDTO request) {
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        if (!diplomas.existsById(qualificationId)) { throw notFound(); }
        qualification.publishDiploma(request.published(), clock.instant());
        profile.touch(actorId, clock.instant());
        return qualificationResponse(qualification, false);
    }

    public void deleteQualification(UUID actorId, UUID veterinarianId, UUID qualificationId) {
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        if (qualification.isBaseDegree()) {
            throw failure(HttpStatus.CONFLICT, "BASE_DEGREE_REQUIRED", "El título base es obligatorio y no se puede eliminar.");
        }
        diplomas.findById(qualificationId).ifPresent(diplomas::delete);
        diplomas.flush();
        qualifications.delete(qualification);
        profile.touch(actorId, clock.instant());
    }

    public VeterinarianQualificationResponseDTO uploadDiploma(UUID actorId, UUID veterinarianId, UUID qualificationId,
            MultipartFile file, boolean published) {
        // Authorization and resource checks precede parsing untrusted bytes.
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        byte[] content = diplomaValidator.validate(file);
        VeterinarianDiploma diploma = diplomas.findById(qualificationId)
                .orElseGet(() -> new VeterinarianDiploma(qualification, content, clock.instant()));
        diploma.replace(content, clock.instant());
        diplomas.saveAndFlush(diploma);
        qualification.publishDiploma(published, clock.instant());
        profile.touch(actorId, clock.instant());
        return qualificationResponse(qualification, false);
    }

    public void deleteDiploma(UUID actorId, UUID veterinarianId, UUID qualificationId) {
        VeterinarianProfile profile = editableVeterinarian(actorId, veterinarianId);
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        diplomas.findById(qualificationId).ifPresent(diplomas::delete);
        qualification.publishDiploma(false, clock.instant());
        profile.touch(actorId, clock.instant());
    }

    public StaffBinaryContent getAdministrativeDiploma(UUID actorId, UUID veterinarianId, UUID qualificationId) {
        editableVeterinarian(actorId, veterinarianId);
        qualification(veterinarianId, qualificationId);
        return diplomaContent(qualificationId);
    }

    @Transactional(readOnly = true)
    public VeterinarianPublicProfilePageResponseDTO listPublicProfiles(UUID actorId, int page, int size) {
        requireActor(actorId, UserRole.OWNER, false);
        Page<VeterinarianProfile> result = veterinarians.findByUserStatus(UserStatus.ACTIVE, pageable(page, size));
        return new VeterinarianPublicProfilePageResponseDTO(result.map(this::publicResponse).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public VeterinarianPublicProfileResponseDTO getPublicProfile(UUID actorId, UUID veterinarianId) {
        return publicResponse(activeVeterinarianForOwner(actorId, veterinarianId));
    }

    public StaffBinaryContent getPublicDiploma(UUID actorId, UUID veterinarianId, UUID qualificationId) {
        activeVeterinarianForOwner(actorId, veterinarianId);
        VeterinarianQualification qualification = qualification(veterinarianId, qualificationId);
        if (!qualification.isDiplomaPublished()) { throw notFound(); }
        return diplomaContent(qualificationId);
    }

    public StaffBinaryContent getPublicPhoto(UUID actorId, UUID veterinarianId) {
        activeVeterinarianForOwner(actorId, veterinarianId);
        UserProfilePhoto photo = photos.findById(veterinarianId).orElseThrow(StaffManagementService::notFound);
        return new StaffBinaryContent(photo.getContent(), photo.getContentType());
    }

    private User createPendingAccount(String email, UserRole role) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) { throw new EmailAlreadyRegisteredException(); }
        try {
            return users.saveAndFlush(User.pendingActivation(normalized, role));
        } catch (DataIntegrityViolationException exception) {
            if (constraintMatches(exception, "uk_users_email")) { throw new EmailAlreadyRegisteredException(); }
            throw exception;
        }
    }

    private void updateStatus(User user, UserStatus status) {
        if (status == null || status == UserStatus.PENDING_ACTIVATION) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_STAFF_STATUS", "Solo se admite ACTIVE o DISABLED.");
        }
        if (status == UserStatus.ACTIVE) {
            if (user.getStatus() == UserStatus.PENDING_ACTIVATION || user.getPasswordHash() == null) {
                throw failure(HttpStatus.CONFLICT, "STAFF_ACTIVATION_REQUIRED", "La persona debe establecer su contraseña mediante una invitación.");
            }
            user.activate();
        } else {
            invitations.invalidateInvitations(user);
            user.deactivate();
        }
    }

    private User requireActor(UUID actorId, UserRole role, boolean lock) {
        User actor = (lock ? users.findByIdForUpdate(actorId) : users.findById(actorId))
                .orElseThrow(() -> failure(HttpStatus.FORBIDDEN, "STAFF_ACCESS_DENIED", "La cuenta no tiene permiso para esta operación."));
        if (!actor.isActive() || actor.getRole() != role) {
            throw failure(HttpStatus.FORBIDDEN, "STAFF_ACCESS_DENIED", "La cuenta no tiene permiso para esta operación.");
        }
        return actor;
    }

    private User lockTarget(UUID userId, UserRole role) {
        User user = users.findByIdForUpdate(userId).orElseThrow(StaffManagementService::notFound);
        if (user.getRole() != role) { throw notFound(); }
        return user;
    }

    private VeterinarianProfile editableVeterinarian(UUID actorId, UUID veterinarianId) {
        requireActor(actorId, UserRole.ADMINISTRATOR, true);
        lockTarget(veterinarianId, UserRole.VETERINARIAN);
        return veterinarian(veterinarianId);
    }

    private VeterinarianProfile activeVeterinarianForOwner(UUID actorId, UUID veterinarianId) {
        requireActor(actorId, UserRole.OWNER, false);
        if (!lockTarget(veterinarianId, UserRole.VETERINARIAN).isActive()) { throw notFound(); }
        return veterinarian(veterinarianId);
    }

    private AdministratorProfile administrator(UUID id) { return administrators.findById(id).orElseThrow(StaffManagementService::notFound); }
    private VeterinarianProfile veterinarian(UUID id) { return veterinarians.findById(id).orElseThrow(StaffManagementService::notFound); }
    private VeterinarianQualification qualification(UUID veterinarianId, UUID qualificationId) {
        return qualifications.findByIdAndVeterinarianUserId(qualificationId, veterinarianId).orElseThrow(StaffManagementService::notFound);
    }
    private VeterinarianQualification baseDegree(UUID veterinarianId) {
        return qualifications.findByVeterinarianUserIdAndBaseDegreeTrue(veterinarianId)
                .orElseThrow(() -> failure(HttpStatus.INTERNAL_SERVER_ERROR, "STAFF_PROFILE_INCOMPLETE", "No se pudo consultar el perfil profesional."));
    }
    private StaffBinaryContent diplomaContent(UUID qualificationId) {
        return new StaffBinaryContent(diplomas.findById(qualificationId).orElseThrow(StaffManagementService::notFound).getContent(), "application/pdf");
    }
    private void saveVeterinarian(VeterinarianProfile profile) {
        try { veterinarians.saveAndFlush(profile); }
        catch (DataIntegrityViolationException exception) {
            if (constraintMatches(exception, "uk_veterinarian_registration")) { throw registrationConflict(); }
            throw exception;
        }
    }
    private void validateGraduationYear(Integer graduationYear) {
        int currentYear = Year.now(clock.withZone(ZoneId.of("America/Bogota"))).getValue();
        if (graduationYear != null && (graduationYear < 1900 || graduationYear > currentYear)) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_GRADUATION_YEAR", "El año de graduación debe estar entre 1900 y el año actual.");
        }
    }
    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "La paginación solicitada no es válida.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("userId")));
    }
    private AdministratorResponseDTO administratorResponse(AdministratorProfile profile) {
        return new AdministratorResponseDTO(profile.getUserId(), profile.getUser().getEmail(), profile.getFullName(),
                profile.getUser().getStatus(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
    private VeterinarianResponseDTO veterinarianResponse(VeterinarianProfile profile) {
        VeterinarianQualification base = baseDegree(profile.getUserId());
        return new VeterinarianResponseDTO(profile.getUserId(), profile.getFullName(),
                profile.getProfessionalPhoneNumber(), profile.getProfessionalRegistrationNumber(), base.getTitle(), base.getInstitution(),
                profile.getBiography(), profile.getUser().getEmail(), profile.getUser().getStatus(), qualificationResponses(profile.getUserId(), false),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
    private VeterinarianPublicProfileResponseDTO publicResponse(VeterinarianProfile profile) {
        VeterinarianQualification base = baseDegree(profile.getUserId());
        return new VeterinarianPublicProfileResponseDTO(profile.getUserId(), profile.getFullName(), profile.getProfessionalPhoneNumber(),
                profile.getProfessionalRegistrationNumber(), base.getTitle(), base.getInstitution(), profile.getBiography(),
                photos.existsById(profile.getUserId()) ? "/api/v1/veterinarian-profiles/" + profile.getUserId() + "/photo" : null,
                qualificationResponses(profile.getUserId(), true));
    }
    private List<VeterinarianQualificationResponseDTO> qualificationResponses(UUID veterinarianId, boolean publicView) {
        return qualifications.findByVeterinarianUserIdOrderByBaseDegreeDescCreatedAtAscIdAsc(veterinarianId).stream()
                .map(qualification -> qualificationResponse(qualification, publicView)).toList();
    }
    private VeterinarianQualificationResponseDTO qualificationResponse(VeterinarianQualification qualification, boolean publicView) {
        boolean available = (!publicView || qualification.isDiplomaPublished()) && diplomas.existsById(qualification.getId());
        return new VeterinarianQualificationResponseDTO(qualification.getId(), qualification.getType(), qualification.getTitle(),
                qualification.getInstitution(), qualification.getGraduationYear(), qualification.isBaseDegree(),
                available, available && qualification.isDiplomaPublished());
    }
    private static boolean constraintMatches(Throwable cause, String name) {
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException exception && "23505".equals(exception.getSQLState())
                    && name.equals(exception.getConstraintName())) { return true; }
            if (cause instanceof PSQLException exception && "23505".equals(exception.getSQLState())
                    && exception.getServerErrorMessage() != null && name.equals(exception.getServerErrorMessage().getConstraint())) { return true; }
        }
        return false;
    }
    private static StaffOperationException registrationConflict() {
        return failure(HttpStatus.CONFLICT, "PROFESSIONAL_REGISTRATION_ALREADY_EXISTS", "La matrícula profesional ya está registrada.");
    }
    private static StaffOperationException notFound() {
        return failure(HttpStatus.NOT_FOUND, "STAFF_RESOURCE_NOT_FOUND", "No se encontró el recurso solicitado.");
    }
    private static StaffOperationException failure(HttpStatus status, String code, String detail) { return new StaffOperationException(status, code, detail); }
}
