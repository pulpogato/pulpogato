package io.github.pulpogato.common;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * A class that can be a {@link String} or a {@link Long} Integer.
 */
@tools.jackson.databind.annotation.JsonDeserialize(using = StringOrInteger.Jackson3Deserializer.class)
@tools.jackson.databind.annotation.JsonSerialize(using = StringOrInteger.Jackson3Serializer.class)
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StringOrInteger.Jackson2Deserializer.class)
@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = StringOrInteger.Jackson2Serializer.class)
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class StringOrInteger implements PulpogatoType {
    private String stringValue;
    private Long integerValue;

    /**
     * Default constructor.
     */
    public StringOrInteger() {}

    /**
     * Constructor with all fields.
     *
     * @param stringValue  the string value
     * @param integerValue the integer value
     */
    public StringOrInteger(String stringValue, Long integerValue) {
        this.stringValue = stringValue;
        this.integerValue = integerValue;
    }

    @Override
    public String toCode() {
        return new CodeBuilder(this.getClass().getName())
                .addProperty("stringValue", stringValue)
                .addProperty("integerValue", integerValue)
                .build();
    }

    static class Jackson3Deserializer extends ValueDeserializer<StringOrInteger> {

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

    static class Jackson3Serializer extends StdSerializer<StringOrInteger> {
        public Jackson3Serializer() {
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

    static class Jackson2Deserializer extends Jackson2FancyDeserializer<StringOrInteger> {
        public Jackson2Deserializer() {
            super(
                    StringOrInteger.class,
                    StringOrInteger::new,
                    Mode.ONE_OF,
                    List.of(
                            new SettableField<>(Long.class, StringOrInteger::setIntegerValue),
                            new SettableField<>(String.class, StringOrInteger::setStringValue)));
        }
    }

    static class Jackson2Serializer extends Jackson2FancySerializer<StringOrInteger> {
        public Jackson2Serializer() {
            super(
                    StringOrInteger.class,
                    Mode.ONE_OF,
                    List.of(
                            new GettableField<>(Long.class, StringOrInteger::getIntegerValue),
                            new GettableField<>(String.class, StringOrInteger::getStringValue)));
        }
    }
}
