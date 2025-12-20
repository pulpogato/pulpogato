package io.github.pulpogato.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Custom deserializer for {@link OffsetDateTime} objects.
 * This deserializer attempts to parse date-time strings using multiple {@link DateTimeFormatter} patterns,
 * and also handles Unix timestamps as numeric values.
 */
public class OffsetDateTimeJackson2Deserializer extends StdDeserializer<OffsetDateTime> {
    private static final List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ISO_INSTANT);

    /**
     * Default constructor for {@code OffsetDateTimeDeserializer}.
     */
    public OffsetDateTimeJackson2Deserializer() {
        super(OffsetDateTime.class);
    }

    /**
     * Deserializes a JSON string or number into an {@link OffsetDateTime} object.
     *
     * @param jsonParser the JSON parser
     * @param deserializationContext the deserialization context
     * @return the deserialized {@code OffsetDateTime} object, or {@code null} if the input is {@code null} or cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    @Override
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {
        var currentToken = jsonParser.getCurrentToken();

        // Handle numeric Unix timestamps
        if (currentToken == JsonToken.VALUE_NUMBER_INT) {
            var timestamp = jsonParser.getLongValue();
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
        }

        // Handle string date-time values
        final var text = jsonParser.getText();
        if (text == null) {
            return null;
        }
        return formatters.stream()
                .map(formatter -> {
                    try {
                        return OffsetDateTime.parse(text, formatter);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
