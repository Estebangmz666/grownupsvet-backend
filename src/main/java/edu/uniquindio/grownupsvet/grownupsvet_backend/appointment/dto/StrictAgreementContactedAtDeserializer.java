package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Keeps the contact instant consistent with the API's textual date-time contract. */
public final class StrictAgreementContactedAtDeserializer extends StdScalarDeserializer<Instant> {
    private static final String INVALID_DATE_TIME_MESSAGE =
            "La fecha y hora deben enviarse como texto ISO 8601 con una zona horaria explícita.";

    public StrictAgreementContactedAtDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(Instant.class, INVALID_DATE_TIME_MESSAGE);
        }
        try {
            return OffsetDateTime.parse(parser.getString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(Instant.class, INVALID_DATE_TIME_MESSAGE);
        }
    }
}
