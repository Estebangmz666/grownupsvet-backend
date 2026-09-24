package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Rejects timestamp coercion and requires an explicit offset in the supplied ISO date-time. */
final class StrictOffsetDateTimeDeserializer extends StdScalarDeserializer<OffsetDateTime> {

    private static final String INVALID_DATE_TIME_MESSAGE =
            "La fecha y hora deben enviarse como texto ISO 8601 con una zona horaria explícita.";

    StrictOffsetDateTimeDeserializer() {
        super(OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(OffsetDateTime.class, INVALID_DATE_TIME_MESSAGE);
        }
        try {
            return OffsetDateTime.parse(parser.getString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException exception) {
            // Do not put the rejected value or parser exception in an application error message.
            return context.reportInputMismatch(OffsetDateTime.class, INVALID_DATE_TIME_MESSAGE);
        }
    }
}
