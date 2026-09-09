package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InternationalPhoneNumberValidatorTests {

    private final InternationalPhoneNumberValidator validator = new InternationalPhoneNumberValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "+573001234567",
            "+14155552671",
            "+442083661177",
            "+34612345678",
            "+41446681800",
            "+81312345678",
            "+61293744000"
    })
    void acceptsMobileAndFixedLineNumbersFromMultipleCountries(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3001234567",
            "00573001234567",
            "+57 3001234567",
            " +573001234567",
            "+573001234567 ",
            "+1-800-FLOWERS",
            "+14155552671x123",
            "+0123456789",
            "+999123456789",
            "+1415555",
            "+11111111111",
            "+1234567890123456",
            "+５７３００１２３４５６７",
            "+573001234567\n",
            ""
    })
    void rejectsNoncanonicalAndInvalidNumbersInsteadOfGuessingACountry(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, null)).isFalse();
    }

    @Test
    void leavesNullToTheRequiredFieldConstraint() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
