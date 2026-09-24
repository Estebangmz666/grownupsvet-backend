package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictOffsetDateTimeDeserializerTests {

    private final JsonMapper mapper;

    StrictOffsetDateTimeDeserializerTests() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTypeConfiguration().strictOffsetDateTimeInputCustomizer().customize(builder);
        mapper = builder.build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-05T09:00:00-05:00", "2026-10-05T14:00:00Z", "2026-10-05T16:00+02:00"})
    void retainsExplicitOffsetsWithoutChangingTheRepresentedInstant(String value) {
        DateTimeRequestDTO request = mapper.readValue("{\"startsAt\":\"" + value + "\"}", DateTimeRequestDTO.class);
        assertThat(request.startsAt()).isEqualTo(OffsetDateTime.parse(value));
        assertThat(request.startsAt().toInstant()).hasToString("2026-10-05T14:00:00Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1791208800", "1791208800.5", "true", "{}", "[]", "[2026,10,5,9,0]",
            "\"1791208800\"", "\"2026-10-05T09:00:00\"", "\"2026-10-05\"", "\"\"",
            "\"2026-02-30T09:00:00-05:00\"", "\" 2026-10-05T09:00:00-05:00\""})
    void rejectsNonTextValuesAndDateTimesWithoutAValidOffset(String value) {
        assertThatThrownBy(() -> mapper.readValue("{\"startsAt\":" + value + "}", DateTimeRequestDTO.class))
                .isInstanceOfSatisfying(MismatchedInputException.class, exception -> {
                    assertThat(exception.getTargetType()).isEqualTo(OffsetDateTime.class);
                    assertThat(exception.getPath().getFirst().getPropertyName()).isEqualTo("startsAt");
                });
    }

    @Test
    void leavesNullHandlingToBeanValidation() {
        assertThat(mapper.readValue("{\"startsAt\":null}", DateTimeRequestDTO.class).startsAt()).isNull();
    }

    record DateTimeRequestDTO(OffsetDateTime startsAt) { }
}
