package io.github.pulpogato.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A class that can be a String or an Integer.
 */
@JsonDeserialize(using = StringOrInteger.CustomDeserializer.class)
@JsonSerialize(using = StringOrInteger.CustomSerializer.class)
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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

    static class CustomDeserializer extends FancyDeserializer<StringOrInteger> {
        public CustomDeserializer() {
            super(
                    StringOrInteger.class,
                    StringOrInteger::new,
                    Mode.ONE_OF,
                    List.of(
                            new SettableField<>(Long.class, StringOrInteger::setIntegerValue),
                            new SettableField<>(String.class, StringOrInteger::setStringValue)));
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
