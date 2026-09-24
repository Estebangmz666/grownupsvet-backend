package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.AvailableVeterinarianSlotResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus;
import edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.VeterinarianAvailabilitySlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public interface VeterinarianAvailabilitySlotRepository extends JpaRepository<VeterinarianAvailabilitySlot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from VeterinarianAvailabilitySlot slot where slot.id = :slotId")
    Optional<VeterinarianAvailabilitySlot> findByIdForUpdate(@Param("slotId") UUID slotId);

    @Query("select slot from VeterinarianAvailabilitySlot slot " +
            "where slot.veterinarianId = :veterinarianId and slot.startsAt >= :from and slot.startsAt < :to " +
            "and (:status is null or slot.status = :status)")
    Page<VeterinarianAvailabilitySlot> findSchedule(@Param("veterinarianId") UUID veterinarianId,
            @Param("from") Instant from, @Param("to") Instant to,
            @Param("status") AvailabilitySlotStatus status, Pageable pageable);

    @Query(value = "select new edu.uniquindio.grownupsvet.grownupsvet_backend.availability.dto.AvailableVeterinarianSlotResponseDTO(" +
            "slot.id, slot.veterinarianId, profile.fullName, slot.startsAt, slot.endsAt, 'America/Bogota') " +
            "from VeterinarianAvailabilitySlot slot, VeterinarianProfile profile " +
            "where profile.userId = slot.veterinarianId and profile.user.role = edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole.VETERINARIAN " +
            "and profile.user.status = edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus.ACTIVE " +
            "and slot.status = edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus.PUBLISHED " +
            "and slot.startsAt >= :from and slot.startsAt < :to and slot.startsAt >= :minimumStart and slot.startsAt <= :maximumStart " +
            "and (:veterinarianId is null or slot.veterinarianId = :veterinarianId)",
            countQuery = "select count(slot) from VeterinarianAvailabilitySlot slot, VeterinarianProfile profile " +
            "where profile.userId = slot.veterinarianId and profile.user.role = edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole.VETERINARIAN " +
            "and profile.user.status = edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserStatus.ACTIVE " +
            "and slot.status = edu.uniquindio.grownupsvet.grownupsvet_backend.availability.model.AvailabilitySlotStatus.PUBLISHED " +
            "and slot.startsAt >= :from and slot.startsAt < :to and slot.startsAt >= :minimumStart and slot.startsAt <= :maximumStart " +
            "and (:veterinarianId is null or slot.veterinarianId = :veterinarianId)")
    Page<AvailableVeterinarianSlotResponseDTO> findAvailable(@Param("from") Instant from, @Param("to") Instant to,
            @Param("minimumStart") Instant minimumStart, @Param("maximumStart") Instant maximumStart,
            @Param("veterinarianId") UUID veterinarianId, Pageable pageable);
}
