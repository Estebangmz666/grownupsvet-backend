package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VeterinarianQualificationRepository extends JpaRepository<VeterinarianQualification, UUID> {
    List<VeterinarianQualification> findByVeterinarianUserIdOrderByBaseDegreeDescCreatedAtAscIdAsc(UUID veterinarianId);
    Optional<VeterinarianQualification> findByIdAndVeterinarianUserId(UUID id, UUID veterinarianId);
    Optional<VeterinarianQualification> findByVeterinarianUserIdAndBaseDegreeTrue(UUID veterinarianId);
    long countByVeterinarianUserId(UUID veterinarianId);
}
