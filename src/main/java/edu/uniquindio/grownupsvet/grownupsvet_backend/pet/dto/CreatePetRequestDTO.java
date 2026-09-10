package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSex;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Mascota del propietario autenticado. El servidor asigna dueño y active=true; no admite propiedades adicionales.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class CreatePetRequestDTO {
    @NotBlank
    @Schema(type = "string", minLength = 1, maxLength = 100, example = "Luna",
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Nombre de hasta 100 puntos de código Unicode, sin caracteres de control. Se retiran espacios exteriores; no puede quedar vacío.")
    private String name;

    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "DOG: perro; CAT: gato.")
    private PetSpecies species;

    @Schema(type = "string", nullable = true, maxLength = 100, example = "Mestiza",
            description = "Raza opcional de hasta 100 puntos de código Unicode, sin caracteres de control. null u omisión indican que no se ha informado; el texto no puede quedar vacío.")
    private String breed;

    @Schema(nullable = true, description = "Sexo opcional. null indica que no se ha informado; UNKNOWN permite registrar que se desconoce.")
    private PetSex sex;

    @Schema(type = "string", format = "date", nullable = true, example = "2020-05-15",
            description = "Fecha exacta o estimada YYYY-MM-DD, no futura según America/Bogota. null indica desconocida; no se inventa una fecha.")
    private LocalDate dateOfBirth;

    @Schema(defaultValue = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description = "true solo si existe dateOfBirth y la fecha es aproximada. Si se omite vale false; no admite null.")
    private boolean dateOfBirthEstimated;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PetSpecies getSpecies() { return species; }
    public void setSpecies(PetSpecies species) { this.species = species; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public PetSex getSex() { return sex; }
    public void setSex(PetSex sex) { this.sex = sex; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public boolean isDateOfBirthEstimated() { return dateOfBirthEstimated; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setDateOfBirthEstimated(boolean dateOfBirthEstimated) {
        this.dateOfBirthEstimated = dateOfBirthEstimated;
    }

    @Override
    public String toString() { return "CreatePetRequestDTO[personalData=[REDACTED]]"; }
}
