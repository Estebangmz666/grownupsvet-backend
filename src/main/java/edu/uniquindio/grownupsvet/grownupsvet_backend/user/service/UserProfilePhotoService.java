package edu.uniquindio.grownupsvet.grownupsvet_backend.user.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.dto.UserProfilePhotoContent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.InvalidProfilePhotoException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.ProfilePhotoNotFoundException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserProfilePhoto;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserProfilePhotoRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

@Service
public class UserProfilePhotoService {
    private final UserProfilePhotoRepository userProfilePhotoRepository;
    private final UserRepository userRepository;
    private final ProfilePhotoProcessor profilePhotoProcessor;
    private final Clock clock;

    public UserProfilePhotoService(UserProfilePhotoRepository userProfilePhotoRepository,
                                   UserRepository userRepository, ProfilePhotoProcessor profilePhotoProcessor,
                                   Clock clock) {
        this.userProfilePhotoRepository = userProfilePhotoRepository;
        this.userRepository = userRepository;
        this.profilePhotoProcessor = profilePhotoProcessor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfilePhotoContent get(UUID userId) {
        UserProfilePhoto photo = userProfilePhotoRepository.findById(userId)
                .orElseThrow(ProfilePhotoNotFoundException::new);
        return new UserProfilePhotoContent(photo.getContent(), photo.getContentType());
    }

    @Transactional
    public void put(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProfilePhotoException(InvalidProfilePhotoException.Reason.EMPTY);
        }
        if (file.getSize() > ProfilePhotoProcessor.MAX_INPUT_BYTES) {
            throw new InvalidProfilePhotoException(InvalidProfilePhotoException.Reason.TOO_LARGE);
        }
        byte[] input;
        try {
            input = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidProfilePhotoException(InvalidProfilePhotoException.Reason.INVALID_CONTENT, exception);
        }
        ProfilePhotoProcessor.ProcessedProfilePhoto processed = profilePhotoProcessor.process(input);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Authenticated account no longer exists"));
        UserProfilePhoto photo = userProfilePhotoRepository.findById(userId).orElse(null);
        if (photo == null) {
            photo = new UserProfilePhoto(user, processed.content(), processed.contentType(),
                    processed.width(), processed.height(), clock.instant());
        } else {
            photo.replace(processed.content(), processed.contentType(), processed.width(), processed.height(),
                    clock.instant());
        }
        userProfilePhotoRepository.saveAndFlush(photo);
    }

    @Transactional
    public void delete(UUID userId) {
        userProfilePhotoRepository.deleteById(userId);
        userProfilePhotoRepository.flush();
    }
}
