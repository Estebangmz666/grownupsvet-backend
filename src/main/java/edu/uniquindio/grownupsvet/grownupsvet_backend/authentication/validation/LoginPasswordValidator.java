package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Uses the same Unicode code-point length definition as password creation. */
public final class LoginPasswordValidator implements ConstraintValidator<ValidLoginPassword, String> {
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return password == null
                || password.codePointCount(0, password.length()) <= MAXIMUM_PASSWORD_LENGTH;
    }
}
