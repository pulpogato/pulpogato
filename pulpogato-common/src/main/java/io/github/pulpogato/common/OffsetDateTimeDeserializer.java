package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Custom deserializer for {@link OffsetDateTime} objects.
 * This deserializer attempts to parse date-time strings using multiple {@link DateTimeFormatter} patterns.
 */
public class OffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
    private static final List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
            DateTimeFormatter.ISO_INSTANT
    );

    /**
     * Default constructor for {@code OffsetDateTimeDeserializer}.
     */
    public OffsetDateTimeDeserializer() {
        super(OffsetDateTime.class);
    }

    /**
     * Deserializes a JSON string into an {@link OffsetDateTime} object.
     *
     * @param jsonParser the JSON parser
     * @param deserializationContext the deserialization context
     * @return the deserialized {@code OffsetDateTime} object, or {@code null} if the input is {@code null} or cannot be parsed
     * @throws IOException if an I/O error occurs
     */
    @Override
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        var text = jsonParser.getText();
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