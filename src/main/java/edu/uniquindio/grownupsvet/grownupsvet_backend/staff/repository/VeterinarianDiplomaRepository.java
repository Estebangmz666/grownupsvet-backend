package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianDiploma;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VeterinarianDiplomaRepository extends JpaRepository<VeterinarianDiploma, UUID> { }
