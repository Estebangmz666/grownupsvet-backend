package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictLocalDateDeserializerTests {

    private final JsonMapper jsonMapper;

    StrictLocalDateDeserializerTests() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTypeConfiguration().strictLocalDateInputCustomizer().customize(builder);
        jsonMapper = builder.build();
    }

    @Test
    void preservesAValidLeapDayAndSerializesItAsAnIsoDate() {
        DateRequestDTO request = jsonMapper.readValue("{\"dateOfBirth\":\"2000-02-29\"}", DateRequestDTO.class);

        assertThat(request.dateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
        assertThat(jsonMapper.writeValueAsString(request)).isEqualTo("{\"dateOfBirth\":\"2000-02-29\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[1955,5,20]", "0", "12.5", "true", "{}", "[]", "[\"1955-05-20\"]",
            "\"1955-05-20T00:00:00\"", "\"1955-05-20T00:00:00Z\"", "\"1955-5-20\"",
            "\"1955-05-20 \"", "\" 1955-05-20\"", "\"1900-02-29\"", "\"2000-02-30\"", "\"\""
    })
    void rejectsCoercionAndInvalidCalendarDatesAsMismatchedInput(String dateJson) {
        assertThatThrownBy(() -> jsonMapper.readValue("{\"dateOfBirth\":" + dateJson + "}", DateRequestDTO.class))
                .isInstanceOfSatisfying(MismatchedInputException.class, exception -> {
                    assertThat(exception.getTargetType()).isEqualTo(LocalDate.class);
                    assertThat(exception.getPath()).hasSize(1);
                    assertThat(exception.getPath().getFirst().getPropertyName()).isEqualTo("dateOfBirth");
                });
    }

    @Test
    void leavesNullHandlingToTheRequestNotNullConstraint() {
        DateRequestDTO request = jsonMapper.readValue("{\"dateOfBirth\":null}", DateRequestDTO.class);

        assertThat(request.dateOfBirth()).isNull();
    }

    record DateRequestDTO(LocalDate dateOfBirth) {
    }
}
