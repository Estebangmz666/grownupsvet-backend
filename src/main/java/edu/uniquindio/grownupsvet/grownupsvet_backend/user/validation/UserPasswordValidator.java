package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Counts Unicode code points, preserves spaces, and rejects whole blocklist entries. */
public final class UserPasswordValidator implements ConstraintValidator<ValidUserPassword, String> {

    private final CommonPasswordBlocklist commonPasswordBlocklist;

    public UserPasswordValidator(CommonPasswordBlocklist commonPasswordBlocklist) {
        this.commonPasswordBlocklist = commonPasswordBlocklist;
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }

        int passwordLength = password.codePointCount(0, password.length());
        if (passwordLength < 15 || passwordLength > 128) {
            return reject(context, "La contraseña debe tener entre 15 y 128 caracteres.");
        }
        if (commonPasswordBlocklist.contains(password)) {
            return reject(context, "Elige una contraseña menos común; puedes usar una frase con varias palabras.");
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
