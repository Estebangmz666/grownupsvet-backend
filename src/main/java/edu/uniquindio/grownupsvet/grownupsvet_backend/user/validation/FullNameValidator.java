package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Checks the agreed minimum of two name components. It cannot establish which
 * component is a legal surname and does not impose a particular country order.
 */
public final class FullNameValidator implements ConstraintValidator<ValidFullName, String> {

    private static final Pattern ALLOWED_NAME_CHARACTERS = Pattern.compile("[\\p{L}\\p{M}\\p{Zs}'’.-]+");
    private static final Pattern NAME_COMPONENT_SEPARATOR = Pattern.compile("\\p{Zs}+");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    @Override
    public boolean isValid(String fullName, ConstraintValidatorContext context) {
        if (fullName == null) {
            return true;
        }
        // Check before strip so leading/trailing control characters cannot disappear.
        if (!ALLOWED_NAME_CHARACTERS.matcher(fullName).matches()) {
            return false;
        }

        if (fullName.codePointCount(0, fullName.length()) > 150) {
            return false;
        }

        String trimmedFullName = fullName.strip();
        String[] nameComponents = NAME_COMPONENT_SEPARATOR.split(trimmedFullName);
        if (nameComponents.length < 2) {
            return false;
        }
        for (String nameComponent : nameComponents) {
            if (!LETTER.matcher(nameComponent).find()) {
                return false;
            }
        }
        return true;
    }
}
