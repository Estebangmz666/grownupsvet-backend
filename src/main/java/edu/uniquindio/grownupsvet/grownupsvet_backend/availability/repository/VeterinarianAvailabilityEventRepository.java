package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VeterinarianAvailabilityEventRepository extends JpaRepository<VeterinarianAvailabilityEvent, UUID> {
    Page<VeterinarianAvailabilityEvent> findBySlotId(UUID slotId, Pageable pageable);
}
