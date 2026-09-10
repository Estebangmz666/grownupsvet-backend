package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.dto;

import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSex;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.model.PetSpecies;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.EnumSet;

/** Setters record presence: an omitted value is different from an explicit JSON null. */
@Schema(description = "Cambio parcial: omitir conserva el valor; null solo elimina breed, sex o dateOfBirth. No admite ownerId ni propiedades adicionales. {} conserva el estado.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class UpdatePetRequestDTO {
    private enum Field { NAME, SPECIES, BREED, SEX, DATE_OF_BIRTH, DATE_OF_BIRTH_ESTIMATED, ACTIVE }

    private final EnumSet<Field> suppliedFields = EnumSet.noneOf(Field.class);

    @Schema(type = "string", minLength = 1, maxLength = 100, example = "Luna",
            description = "Nombre de hasta 100 puntos de código Unicode, sin caracteres de control y no vacío después de retirar espacios exteriores; no admite null.")
    private String name;

    @Schema(description = "DOG: perro; CAT: gato. No admite null.")
    private PetSpecies species;

    @Schema(type = "string", nullable = true, maxLength = 100, example = "Mestiza",
            description = "Texto no vacío de hasta 100 puntos de código Unicode, sin caracteres de control, o null para quitar la raza.")
    private String breed;

    @Schema(nullable = true, description = "Sexo de la mascota; null quita el dato. UNKNOWN indica que se desconoce.")
    private PetSex sex;

    @Schema(type = "string", format = "date", nullable = true, example = "2020-05-15",
            description = "Fecha YYYY-MM-DD no futura según America/Bogota, o null para quitarla. Si la fecha anterior era estimada, también debe enviarse dateOfBirthEstimated=false al quitarla.")
    private LocalDate dateOfBirth;

    @Schema(description = "No admite null. true requiere una fecha en el estado resultante. Omitir conserva el valor anterior.")
    private Boolean dateOfBirthEstimated;

    @Schema(description = "No admite null. false archiva sin borrar la mascota ni su historial; true la reactiva. Una mascota archivada no debe admitir nuevas reservas.")
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; suppliedFields.add(Field.NAME); }
    public boolean hasName() { return suppliedFields.contains(Field.NAME); }
    public PetSpecies getSpecies() { return species; }
    public void setSpecies(PetSpecies species) { this.species = species; suppliedFields.add(Field.SPECIES); }
    public boolean hasSpecies() { return suppliedFields.contains(Field.SPECIES); }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; suppliedFields.add(Field.BREED); }
    public boolean hasBreed() { return suppliedFields.contains(Field.BREED); }
    public PetSex getSex() { return sex; }
    public void setSex(PetSex sex) { this.sex = sex; suppliedFields.add(Field.SEX); }
    public boolean hasSex() { return suppliedFields.contains(Field.SEX); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; suppliedFields.add(Field.DATE_OF_BIRTH); }
    public boolean hasDateOfBirth() { return suppliedFields.contains(Field.DATE_OF_BIRTH); }
    public Boolean getDateOfBirthEstimated() { return dateOfBirthEstimated; }
    public void setDateOfBirthEstimated(Boolean estimated) {
        this.dateOfBirthEstimated = estimated;
        suppliedFields.add(Field.DATE_OF_BIRTH_ESTIMATED);
    }
    public boolean hasDateOfBirthEstimated() { return suppliedFields.contains(Field.DATE_OF_BIRTH_ESTIMATED); }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; suppliedFields.add(Field.ACTIVE); }
    public boolean hasActive() { return suppliedFields.contains(Field.ACTIVE); }
    public boolean hasChanges() { return !suppliedFields.isEmpty(); }

    @Override
    public String toString() { return "UpdatePetRequestDTO[personalData=[REDACTED]]"; }
}
