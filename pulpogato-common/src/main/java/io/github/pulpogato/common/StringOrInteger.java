package io.github.pulpogato.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * A class that can be a String or an Integer.
 */
@JsonDeserialize(using = StringOrInteger.CustomDeserializer.class)
@JsonSerialize(using = StringOrInteger.CustomSerializer.class)
@Getter
@Setter
@Accessors(chain = true, fluent = false)
public class StringOrInteger {
    private String stringValue;
    private Long integerValue;

    /**
     * Creates an instance that can be a String, or an Integer.
     */
    public StringOrInteger() {
    }

    static class CustomDeserializer extends FancyDeserializer<StringOrInteger> {
        public CustomDeserializer() {
            super(StringOrInteger.class, StringOrInteger::new, Mode.oneOf, List.of(
                    new SettableField<>(Long.class, StringOrInteger::setIntegerValue),
                    new SettableField<>(String.class, StringOrInteger::setStringValue)
            ));
        }
    }

    static class CustomSerializer extends FancySerializer<StringOrInteger> {
        public CustomSerializer() {
            super(StringOrInteger.class, Mode.oneOf, List.of(
                    new GettableField<>(Long.class, StringOrInteger::getIntegerValue),
                    new GettableField<>(String.class, StringOrInteger::getStringValue)
            ));
        }
    }
}
