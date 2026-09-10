package edu.uniquindio.grownupsvet.grownupsvet_backend.pet.exception;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.dto.error.FieldValidationErrorResponseDTO;

import java.util.List;

public class InvalidPetRequestException extends RuntimeException {
    private final List<FieldValidationErrorResponseDTO> fieldErrors;

    public InvalidPetRequestException(List<FieldValidationErrorResponseDTO> fieldErrors) {
        super("Invalid pet request");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldValidationErrorResponseDTO> getFieldErrors() { return fieldErrors; }
}
