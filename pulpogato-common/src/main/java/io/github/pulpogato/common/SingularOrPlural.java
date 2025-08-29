package io.github.pulpogato.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * A class that can represent either a single value or a list of values of the same type.
 */
@JsonDeserialize(using = SingularOrPlural.CustomDeserializer.class)
@JsonSerialize(using = SingularOrPlural.CustomSerializer.class)
@Getter
@Setter
@Accessors(chain = true, fluent = false)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SingularOrPlural<T> {
    private T singular;
    private List<T> plural;

    public static <T> SingularOrPlural<T> singular(T singular) {
        return new SingularOrPlural<>(singular, null);
    }

    public static <T> SingularOrPlural<T> plural(List<T> plural) {
        return new SingularOrPlural<>(null, plural);
    }

    static class CustomDeserializer extends FancyDeserializer<SingularOrPlural> {
        public CustomDeserializer() {
            super(SingularOrPlural.class, SingularOrPlural::new, Mode.ONE_OF, List.of(
                    new SettableField<>(List.class, SingularOrPlural::setPlural),
                    new SettableField<>(Object.class, SingularOrPlural::setSingular)
            ));
        }
    }

    static class CustomSerializer extends FancySerializer<SingularOrPlural> {
        public CustomSerializer() {
            super(SingularOrPlural.class, Mode.ONE_OF, List.of(
                    new GettableField<>(List.class, SingularOrPlural::getPlural),
                    new GettableField<>(Object.class, SingularOrPlural::getSingular)
            ));
        }
    }
}
