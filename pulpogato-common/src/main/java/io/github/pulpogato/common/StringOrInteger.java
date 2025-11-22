package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.List;
import lombok.*;

/**
 * A class that can be a {@link String} or a {@link Long} Integer.
 */
@JsonDeserialize(using = StringOrInteger.CustomDeserializer.class)
@JsonSerialize(using = StringOrInteger.CustomSerializer.class)
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class StringOrInteger implements PulpogatoType {
    private String stringValue;
    private Long integerValue;

    @Override
    public String toCode() {
        return new CodeBuilder(this.getClass().getName())
                .addProperty("stringValue", stringValue)
                .addProperty("integerValue", integerValue)
                .build();
    }

    static class CustomDeserializer extends JsonDeserializer<StringOrInteger> {
        @Override
        public StringOrInteger deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            StringOrInteger result = new StringOrInteger();

            // Check the current token type to determine how to deserialize
            switch (p.getCurrentToken()) {
                case VALUE_NUMBER_INT -> result.setIntegerValue(p.getLongValue());
                case VALUE_STRING -> result.setStringValue(p.getValueAsString());
                default -> parseByContent(p, result);
            }

            return result;
        }

        private void parseByContent(JsonParser p, StringOrInteger result) throws IOException {
            String value = p.getValueAsString();
            if (value != null) {
                try {
                    Long longValue = Long.parseLong(value);
                    result.setIntegerValue(longValue);
                } catch (NumberFormatException e) {
                    result.setStringValue(value);
                }
            }
        }
    }

    static class CustomSerializer extends FancySerializer<StringOrInteger> {
        public CustomSerializer() {
            super(
                    StringOrInteger.class,
                    Mode.ONE_OF,
                    List.of(
                            new GettableField<>(Long.class, StringOrInteger::getIntegerValue),
                            new GettableField<>(String.class, StringOrInteger::getStringValue)));
        }
    }
}
