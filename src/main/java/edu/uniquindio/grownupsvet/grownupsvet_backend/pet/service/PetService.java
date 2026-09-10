package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.CreatePetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.PetPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.PetResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto.UpdatePetRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.exception.InvalidPetRequestException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.exception.PetNotFoundException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.Pet;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSex;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.repository.PetRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.FieldValidationErrorResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.exception.OwnerProfileNotFoundException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PetService {
    private static final Sort PET_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private final PetRepository petRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final Clock clock;

    public PetService(PetRepository petRepository, OwnerProfileRepository ownerProfileRepository, Clock clock) {
        this.petRepository = petRepository;
        this.ownerProfileRepository = ownerProfileRepository;
        this.clock = clock;
    }

    @Transactional
    public PetResponseDTO create(UUID ownerId, CreatePetRequestDTO request) {
        OwnerProfile owner = requireActiveOwner(ownerId);
        validateDetails(request.getName(), request.getSpecies(), request.getBreed(), request.getDateOfBirth(),
                request.isDateOfBirthEstimated(), true);
        Pet pet = new Pet(owner, request.getName().strip(), request.getSpecies(), normalizedBreed(request.getBreed()),
                request.getSex(), request.getDateOfBirth(), request.isDateOfBirthEstimated(), currentInstant());
        return toResponse(petRepository.saveAndFlush(pet));
    }

    @Transactional(readOnly = true)
    public PetPageResponseDTO list(UUID ownerId, int page, int size, Boolean active) {
        requireActiveOwner(ownerId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidPetRequestException(List.of(new FieldValidationErrorResponseDTO(
                    size < 1 || size > 100 ? "size" : "page", "OUT_OF_RANGE",
                    "La página debe ser no negativa, el tamaño entre 1 y 100 y el desplazamiento no mayor de 2147483647.")));
        }
        PageRequest pageRequest = PageRequest.of(page, size, PET_ORDER);
        Page<Pet> pets = active == null
                ? petRepository.findAllByOwnerUserId(ownerId, pageRequest)
                : petRepository.findAllByOwnerUserIdAndActive(ownerId, active, pageRequest);
        return new PetPageResponseDTO(pets.getContent().stream().map(this::toResponse).toList(),
                page, size, pets.getTotalElements(), pets.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PetResponseDTO get(UUID ownerId, UUID petId) {
        requireActiveOwner(ownerId);
        return toResponse(petRepository.findByIdAndOwnerUserId(petId, ownerId)
                .orElseThrow(PetNotFoundException::new));
    }

    @Transactional
    public PetResponseDTO update(UUID ownerId, UUID petId, UpdatePetRequestDTO request) {
        requireActiveOwner(ownerId);
        Pet pet = petRepository.findOwnedPetForUpdate(petId, ownerId).orElseThrow(PetNotFoundException::new);
        if (!request.hasChanges()) {
            return toResponse(pet);
        }

        String name = request.hasName() ? request.getName() : pet.getName();
        PetSpecies species = request.hasSpecies() ? request.getSpecies() : pet.getSpecies();
        String breed = request.hasBreed() ? request.getBreed() : pet.getBreed();
        PetSex sex = request.hasSex() ? request.getSex() : pet.getSex();
        LocalDate dateOfBirth = request.hasDateOfBirth() ? request.getDateOfBirth() : pet.getDateOfBirth();
        Boolean estimated = request.hasDateOfBirthEstimated()
                ? request.getDateOfBirthEstimated() : Boolean.valueOf(pet.isDateOfBirthEstimated());
        Boolean active = request.hasActive() ? request.getActive() : Boolean.valueOf(pet.isActive());

        validateDetails(name, species, breed, dateOfBirth, estimated, active);
        Instant updatedAt = currentInstant();
        if (updatedAt.isBefore(pet.getUpdatedAt())) {
            updatedAt = pet.getUpdatedAt();
        }
        pet.updateDetails(name.strip(), species, normalizedBreed(breed), sex, dateOfBirth, estimated, active, updatedAt);
        petRepository.flush();
        return toResponse(pet);
    }

    private OwnerProfile requireActiveOwner(UUID ownerId) {
        OwnerProfile owner = ownerProfileRepository.findById(ownerId).orElseThrow(OwnerProfileNotFoundException::new);
        if (owner.getUser().getRole() != UserRole.OWNER || !owner.getUser().isActive()) {
            throw new AccessDeniedException("Only active owners can manage pets");
        }
        return owner;
    }

    private void validateDetails(String name, PetSpecies species, String breed, LocalDate dateOfBirth,
                                 Boolean estimated, Boolean active) {
        List<FieldValidationErrorResponseDTO> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(error("name", "REQUIRED", "Escribe el nombre de tu mascota."));
        } else if (name.codePointCount(0, name.length()) > 100) {
            errors.add(error("name", "INVALID_LENGTH", "El nombre no puede superar 100 caracteres."));
        } else if (name.codePoints().anyMatch(Character::isISOControl)) {
            errors.add(error("name", "INVALID_VALUE", "El nombre no puede contener caracteres de control ni saltos de línea."));
        }
        if (species == null) {
            errors.add(error("species", "REQUIRED", "Selecciona perro o gato."));
        }
        if (breed != null && (breed.isBlank() || breed.codePointCount(0, breed.length()) > 100
                || breed.codePoints().anyMatch(Character::isISOControl))) {
            errors.add(error("breed", "INVALID_VALUE", "Escribe una raza de hasta 100 caracteres, sin caracteres de control ni saltos de línea, o deja el dato sin informar con null."));
        }
        if (dateOfBirth != null && (dateOfBirth.getYear() < 1 || dateOfBirth.isAfter(LocalDate.now(clock)))) {
            errors.add(error("dateOfBirth", "INVALID_DATE", "Escribe una fecha de nacimiento válida que no esté en el futuro."));
        }
        if (estimated == null) {
            errors.add(error("dateOfBirthEstimated", "REQUIRED", "Indica true o false para la fecha estimada."));
        } else if (estimated && dateOfBirth == null) {
            errors.add(error("dateOfBirthEstimated", "INVALID_VALUE", "Una fecha estimada requiere una fecha de nacimiento. Si no se conoce, usa dateOfBirth=null y dateOfBirthEstimated=false."));
        }
        if (active == null) {
            errors.add(error("active", "REQUIRED", "Indica true para una mascota activa o false para archivarla."));
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(FieldValidationErrorResponseDTO::field));
            throw new InvalidPetRequestException(errors);
        }
    }

    private FieldValidationErrorResponseDTO error(String field, String code, String message) {
        return new FieldValidationErrorResponseDTO(field, code, message);
    }

    private String normalizedBreed(String breed) { return breed == null ? null : breed.strip(); }

    private Instant currentInstant() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private PetResponseDTO toResponse(Pet pet) {
        return new PetResponseDTO(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getSex(),
                pet.getDateOfBirth(), pet.isDateOfBirthEstimated(), pet.isActive(), pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
