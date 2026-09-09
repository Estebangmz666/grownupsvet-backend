package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/** Evaluates completed years using the application's configured calendar time zone. */
public final class DateOfBirthValidator implements ConstraintValidator<ValidDateOfBirth, LocalDate> {

    private final Clock clock;

    public DateOfBirthValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate dateOfBirth, ConstraintValidatorContext context) {
        if (dateOfBirth == null) {
            return true;
        }

        LocalDate today = LocalDate.now(clock);
        return !dateOfBirth.isAfter(today)
                && Period.between(dateOfBirth, today).getYears() <= 130;
    }
}
