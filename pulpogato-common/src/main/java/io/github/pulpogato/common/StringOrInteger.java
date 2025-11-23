package io.github.pulpogato.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.StdSerializer;

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

    static class CustomDeserializer extends ValueDeserializer<StringOrInteger> {

        @Override
        public StringOrInteger deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            StringOrInteger result = new StringOrInteger();

            // Check the current token type to determine how to deserialize
            switch (p.currentToken()) {
                case VALUE_NUMBER_INT -> result.setIntegerValue(p.getLongValue());
                case VALUE_STRING -> result.setStringValue(p.getValueAsString());
                default -> parseByContent(p, result);
            }

            return result;
        }

        private void parseByContent(JsonParser p, StringOrInteger result) {
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

    static class CustomSerializer extends StdSerializer<StringOrInteger> {
        public CustomSerializer() {
            super(StringOrInteger.class);
        }

        @Override
        public void serialize(StringOrInteger value, JsonGenerator gen, SerializationContext provider) {
            if (value.getStringValue() != null) {
                gen.writeString(value.getStringValue());
            } else if (value.getIntegerValue() != null) {
                gen.writeNumber(value.getIntegerValue());
            } else {
                gen.writeNull();
            }
        }
    }
}
