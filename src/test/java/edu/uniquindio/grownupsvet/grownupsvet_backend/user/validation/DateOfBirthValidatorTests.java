package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateOfBirthValidatorTests {

    private static final ZoneId APPLICATION_TIME_ZONE = ZoneId.of("America/Bogota");
    private final DateOfBirthValidator validator = new DateOfBirthValidator(
            Clock.fixed(Instant.parse("2026-09-08T03:00:00Z"), APPLICATION_TIME_ZONE));

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-07", "2000-02-29", "1896-09-07", "1895-09-08"})
    void acceptsZeroThrough130CompletedYearsIncludingTheDayBeforeThe131stBirthday(String dateOfBirth) {
        assertThat(validator.isValid(LocalDate.parse(dateOfBirth), null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-08", "1895-09-07", "1894-01-01"})
    void rejectsFutureDatesAndAge131OrOlderUsingTheConfiguredTimeZone(String dateOfBirth) {
        assertThat(validator.isValid(LocalDate.parse(dateOfBirth), null)).isFalse();
    }

    @Test
    void handlesALeapDayBirthAcrossTheUpperAgeBoundaryWithoutApproximateDayCounts() {
        LocalDate leapDayBirth = LocalDate.of(1896, 2, 29);
        DateOfBirthValidator beforeAnniversary = new DateOfBirthValidator(
                Clock.fixed(Instant.parse("2027-02-28T12:00:00Z"), APPLICATION_TIME_ZONE));
        DateOfBirthValidator afterAnniversary = new DateOfBirthValidator(
                Clock.fixed(Instant.parse("2027-03-01T12:00:00Z"), APPLICATION_TIME_ZONE));

        assertThat(beforeAnniversary.isValid(leapDayBirth, null)).isTrue();
        assertThat(afterAnniversary.isValid(leapDayBirth, null)).isFalse();
    }

    @Test
    void leavesNullToTheRequiredFieldConstraint() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
