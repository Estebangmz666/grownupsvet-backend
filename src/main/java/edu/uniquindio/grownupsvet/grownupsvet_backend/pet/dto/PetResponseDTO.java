package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSex;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Mascota del propietario autenticado. No expone datos de la cuenta ni relaciones internas.")
public record PetResponseDTO(
        @Schema(type = "string", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(type = "string", minLength = 1, maxLength = 100, example = "Luna", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PetSpecies species,
        @Schema(type = "string", nullable = true, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String breed,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        PetSex sex,
        @Schema(type = "string", format = "date", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "null si la fecha es desconocida; no se calcula ni inventa una fecha de nacimiento.")
        LocalDate dateOfBirth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "true indica que dateOfBirth es aproximada. Sin fecha siempre es false.")
        boolean dateOfBirthEstimated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "false significa archivada y permite reactivación; conserva identidad y relaciones existentes.")
        boolean active,
        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) { }
