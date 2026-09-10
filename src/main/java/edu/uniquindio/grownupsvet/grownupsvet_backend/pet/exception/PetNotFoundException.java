package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.exception;

/** Does not distinguish an absent pet from another owner's pet. */
public class PetNotFoundException extends RuntimeException {
    public PetNotFoundException() { super("Pet not found"); }
}
