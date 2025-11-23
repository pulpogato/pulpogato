package io.github.pulpogato.common;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * A class that can represent either a single value or a list of values of the same type.
 *
 * @param <T> the type of the value(s) held by this container
 */
@JsonDeserialize(using = SingularOrPlural.CustomDeserializer.class)
@JsonSerialize(using = SingularOrPlural.CustomSerializer.class)
@Getter
@Setter
@Accessors(chain = true, fluent = false)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SingularOrPlural<T> implements PulpogatoType {
    @Nullable
    private T singular;

    @Nullable
    private List<T> plural;

    /**
     * Default constructor for creating an empty SingularOrPlural instance.
     */
    public SingularOrPlural() {
        // Empty Default Constructor
    }

    /**
     * Creates a SingularOrPlural instance containing a single value.
     *
     * @param <T> the type of the value
     * @param singular the single value to wrap
     * @return a new SingularOrPlural instance containing the single value
     */
    public static <T> SingularOrPlural<T> singular(@NonNull T singular) {
        return new SingularOrPlural<>(singular, null);
    }

    /**
     * Creates a SingularOrPlural instance containing a list of values.
     *
     * @param <T> the type of the values in the list
     * @param plural the list of values to wrap
     * @return a new SingularOrPlural instance containing the list of values
     */
    public static <T> SingularOrPlural<T> plural(@NonNull List<T> plural) {
        return new SingularOrPlural<>(null, plural);
    }

    @Override
    public String toCode() {
        return "io.github.pulpogato.common.SingularOrPlural."
                + ((singular == null)
                        ? ("plural(" + CodeBuilder.render(plural) + ")")
                        : ("singular(" + CodeBuilder.render(singular) + ")"));
    }

    static class CustomDeserializer extends FancyDeserializer<SingularOrPlural> {
        public CustomDeserializer() {
            super(
                    SingularOrPlural.class,
                    SingularOrPlural::new,
                    Mode.ONE_OF,
                    List.of(
                            new SettableField<>(List.class, SingularOrPlural::setPlural),
                            new SettableField<>(Object.class, SingularOrPlural::setSingular)));
        }
    }

    static class CustomSerializer extends FancySerializer<SingularOrPlural> {
        public CustomSerializer() {
            super(
                    SingularOrPlural.class,
                    Mode.ONE_OF,
                    List.of(
                            new GettableField<>(List.class, SingularOrPlural::getPlural),
                            new GettableField<>(Object.class, SingularOrPlural::getSingular)));
        }
    }
}
