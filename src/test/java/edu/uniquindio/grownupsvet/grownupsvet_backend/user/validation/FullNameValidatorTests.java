package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FullNameValidatorTests {

    private final FullNameValidator validator = new FullNameValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "María Pérez",
            "  María   del Carmen Pérez  ",
            "Jean-Luc Picard",
            "Shaun O'Neill",
            "Shaun O’Neill",
            "J. R. Tolkien",
            "Jose\u0301 Pe\u0301rez",
            "王 小明",
            "محمد علي",
            "Ana\u00A0Pérez"
    })
    void acceptsMultipleNameComponentsAcrossLanguagesWithoutAssumingSurnameOrder(String fullName) {
        assertThat(validator.isValid(fullName, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "María",
            "Jean-Luc",
            "",
            "   ",
            "María 123",
            "María Pérez2",
            "María .",
            "' Pérez",
            "\tMaría Pérez",
            "María\nPérez",
            "María Pérez\r",
            "María 👩"
    })
    void rejectsMissingNameComponentsDigitsControlsAndComponentsWithoutLetters(String fullName) {
        assertThat(validator.isValid(fullName, null)).isFalse();
    }

    @Test
    void counts150UnicodeCodePointsIncludingSpacesInsteadOfUtf16CodeUnits() {
        String nameAtLimit = "\uD801\uDC00".repeat(74) + " " + "\uD801\uDC01".repeat(75);

        assertThat(validator.isValid(nameAtLimit, null)).isTrue();
        assertThat(validator.isValid("  " + nameAtLimit + "  ", null)).isFalse();
        assertThat(validator.isValid(nameAtLimit + "a", null)).isFalse();
    }

    @Test
    void leavesNullToTheRequiredFieldConstraint() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
