package edu.uniquindio.grownupsvet.grownupsvet_backend.user.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.OwnerProfileResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UpdateOwnerProfileRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.OwnerProfileNotFoundException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserProfilePhotoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OwnerProfileService {
    public static final String PROFILE_PHOTO_PATH = "/api/v1/users/me/profile/photo";

    private final OwnerProfileRepository ownerProfileRepository;
    private final UserProfilePhotoRepository userProfilePhotoRepository;

    public OwnerProfileService(OwnerProfileRepository ownerProfileRepository,
                               UserProfilePhotoRepository userProfilePhotoRepository) {
        this.ownerProfileRepository = ownerProfileRepository;
        this.userProfilePhotoRepository = userProfilePhotoRepository;
    }

    @Transactional(readOnly = true)
    public OwnerProfileResponseDTO get(UUID userId) {
        return toResponse(requireOwnerProfile(userId));
    }

    @Transactional
    public OwnerProfileResponseDTO update(UUID userId, UpdateOwnerProfileRequestDTO request) {
        OwnerProfile profile = requireOwnerProfile(userId);
        profile.changePhoneNumber(request.phoneNumber());
        ownerProfileRepository.flush();
        return toResponse(profile);
    }

    @Transactional
    public void deactivate(UUID userId) {
        OwnerProfile profile = requireOwnerProfile(userId);
        profile.getUser().deactivate();
        ownerProfileRepository.flush();
    }

    private OwnerProfile requireOwnerProfile(UUID userId) {
        OwnerProfile profile = ownerProfileRepository.findById(userId)
                .orElseThrow(OwnerProfileNotFoundException::new);
        if (profile.getUser().getRole() != UserRole.OWNER) {
            throw new AccessDeniedException("Only owners have an owner profile");
        }
        return profile;
    }

    private OwnerProfileResponseDTO toResponse(OwnerProfile profile) {
        User user = profile.getUser();
        String photoUrl = userProfilePhotoRepository.existsById(user.getId()) ? PROFILE_PHOTO_PATH : null;
        return new OwnerProfileResponseDTO(user.getId(), user.getEmail(), user.getRole(), user.isActive(),
                profile.getFullName(), profile.getDateOfBirth(), profile.getPhoneNumber(), photoUrl);
    }
}
