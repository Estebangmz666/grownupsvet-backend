package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.AdministratorProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AdministratorProfileRepository extends JpaRepository<AdministratorProfile, UUID> {
    Page<AdministratorProfile> findByUserStatus(UserStatus status, Pageable pageable);
}
