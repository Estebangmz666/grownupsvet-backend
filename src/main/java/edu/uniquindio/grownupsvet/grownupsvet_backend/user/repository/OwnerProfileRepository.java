package edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, UUID> {
}
