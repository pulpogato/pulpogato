package io.github.pulpogato.common.jackson;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Custom deserializer for {@link OffsetDateTime} objects.
 * This deserializer attempts to parse date-time strings using multiple {@link DateTimeFormatter} patterns,
 * and also handles Unix timestamps as numeric values.
 */
public class OffsetDateTimeJackson3Deserializer extends StdDeserializer<OffsetDateTime> {
    /**
     * Default constructor for {@code OffsetDateTimeDeserializer}.
     */
    public OffsetDateTimeJackson3Deserializer() {
        super(OffsetDateTime.class);
    }

    /**
     * Deserializes a JSON string or number into an {@link OffsetDateTime} object.
     *
     * @param jsonParser the JSON parser
     * @param deserializationContext the deserialization context
     * @return the deserialized {@code OffsetDateTime} object, or {@code null} if the input is {@code null} or cannot be parsed
     */
    @Override
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        var currentToken = jsonParser.currentToken();

        // Handle numeric Unix timestamps
        if (currentToken == JsonToken.VALUE_NUMBER_INT) {
            var timestamp = jsonParser.getLongValue();
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
        }

        // Handle string date-time values
        final var text = jsonParser.getString();
        return OffsetDateTimeUtil.parseStringDateTime(text);
    }
}
