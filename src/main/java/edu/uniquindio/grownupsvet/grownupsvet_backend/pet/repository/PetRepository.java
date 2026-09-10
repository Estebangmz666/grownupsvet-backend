package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository;

import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PetRepository extends JpaRepository<Pet, UUID> {
    Page<Pet> findAllByOwnerUserId(UUID ownerId, Pageable pageable);

    Page<Pet> findAllByOwnerUserIdAndActive(UUID ownerId, boolean active, Pageable pageable);

    Optional<Pet> findByIdAndOwnerUserId(UUID petId, UUID ownerId);

    /** Serialize partial changes so concurrent patches preserve fields they omitted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pet from Pet pet where pet.id = :petId and pet.owner.userId = :ownerId")
    Optional<Pet> findOwnedPetForUpdate(@Param("petId") UUID petId, @Param("ownerId") UUID ownerId);
}
