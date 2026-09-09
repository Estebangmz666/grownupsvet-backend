package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserPasswordValidatorTests {

    private final UserPasswordValidator validator = new UserPasswordValidator(new CommonPasswordBlocklist());
    private ConstraintValidatorContext context;

    @BeforeEach
    void initializeValidationContext() {
        context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 128})
    void acceptsTheInclusiveLengthBoundsMeasuredInUnicodeCodePoints(int codePoints) {
        assertThat(validator.isValid("\uD83D\uDC36".repeat(codePoints), context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {14, 129})
    void rejectsLengthsOutsideTheBoundsEvenForSupplementaryUnicodeCharacters(int codePoints) {
        assertThat(validator.isValid("\uD83D\uDC36".repeat(codePoints), context)).isFalse();
        verify(context).buildConstraintViolationWithTemplate(
                "La contraseña debe tener entre 15 y 128 caracteres.");
    }

    @Test
    void acceptsPassphrasesWithoutRequiringUppercaseDigitsOrSymbols() {
        assertThat(validator.isValid("mis mascotas caminan por el jardín", context)).isTrue();
    }

    @Test
    void preservesLeadingTrailingAndRepeatedSpacesWhenMeasuringThePassword() {
        assertThat(validator.isValid("  paseo  suave  ", context)).isTrue();
    }

    @Test
    void rejectsAnActualLongBlocklistedPasswordIgnoringCase() {
        assertThat(validator.isValid("FILMS+PIC+GALERIES", context)).isFalse();
        verify(context).buildConstraintViolationWithTemplate(
                "Elige una contraseña menos común; puedes usar una frase con varias palabras.");
    }

    @Test
    void comparesWholePasswordsWithoutTrimmingOrRejectingContainedWords() {
        assertThat(validator.isValid(" films+pic+galeries ", context)).isTrue();
        assertThat(validator.isValid("mi password protege a mis mascotas", context)).isTrue();
    }

    @Test
    void leavesNullToTheRequiredFieldConstraint() {
        assertThat(validator.isValid(null, context)).isTrue();
    }
}
