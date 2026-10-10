package com.featureflag.flag_service.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Deserializer for internal entities and event snapshots that safely reads both modern ISO-8601
 * UTC instant strings (e.g. ending in 'Z' or with offset) and legacy timezone-less representations
 * (which are interpreted as UTC).
 */
public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException instantException) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException offsetException) {
                try {
                    return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException localException) {
                    throw context.instantiationException(Instant.class, localException);
                }
            }
        }
    }
}
