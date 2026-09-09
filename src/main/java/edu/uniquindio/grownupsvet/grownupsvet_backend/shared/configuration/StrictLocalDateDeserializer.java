package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Accepts exactly an ISO calendar date string; array and epoch-day coercion are not part of the contract. */
final class StrictLocalDateDeserializer extends StdScalarDeserializer<LocalDate> {

    private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final String INVALID_DATE_MESSAGE = "La fecha debe ser una cadena válida con formato YYYY-MM-DD.";

    StrictLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(LocalDate.class, INVALID_DATE_MESSAGE);
        }
        String value = parser.getString();
        if (!DATE_FORMAT.matcher(value).matches()) {
            return context.reportInputMismatch(LocalDate.class, INVALID_DATE_MESSAGE);
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            // Neither the received date nor the parsing exception enters the public error detail.
            return context.reportInputMismatch(LocalDate.class, INVALID_DATE_MESSAGE);
        }
    }
}
