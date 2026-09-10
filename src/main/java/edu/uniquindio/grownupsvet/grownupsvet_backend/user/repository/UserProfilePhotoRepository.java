package edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfilePhotoRepository extends JpaRepository<UserProfilePhoto, UUID> {
}
