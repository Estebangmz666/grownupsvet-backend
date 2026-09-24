package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VeterinarianProfileRepository extends JpaRepository<VeterinarianProfile, UUID> {
    Page<VeterinarianProfile> findByUserStatus(UserStatus status, Pageable pageable);
    boolean existsByProfessionalRegistrationNumber(String registrationNumber);
}
