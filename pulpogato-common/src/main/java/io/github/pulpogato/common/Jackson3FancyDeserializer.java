package io.github.pulpogato.common;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * A deserializer that can handle <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 *
 * @param <T> The type
 */
@Slf4j
public class Jackson3FancyDeserializer<T> extends StdDeserializer<T> {

    /**
     * A field that can be set on the field
     *
     * @param type   The class of the object
     * @param setter The method that sets the field on the object
     * @param <T>    The type of the object
     * @param <X>    The type of the field
     */
    public record SettableField<T, X>(Class<X> type, BiConsumer<T, X> setter) {}

    private static final ObjectMapper om = new ObjectMapper();

    /**
     * Constructs a deserializer
     *
     * @param vc          The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode        The mode of deserialization
     * @param fields      The fields that can be set on the class
     */
    public Jackson3FancyDeserializer(
            Class<T> vc, Supplier<T> initializer, Mode mode, List<SettableField<T, ?>> fields) {
        super(vc);
        this.initializer = initializer;
        this.mode = mode;
        this.fields = fields;
    }

    /**
     * The supplier that creates a new instance of the class
     */
    private final transient Supplier<T> initializer;

    /**
     * The mode of deserialization
     */
    private final Mode mode;

    /**
     * The fields that can be set on the class
     */
    private final transient List<SettableField<T, ?>> fields;

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) {
        final var returnValue = initializer.get();

        try {
            final var map = ctxt.readValue(p, Map.class);
            final var mapAsString = om.writeValueAsString(map);
            setAllFields(mapAsString, returnValue);
        } catch (JacksonException e) {
            try {
                final var list = ctxt.readValue(p, List.class);
                final var listAsString = om.writeValueAsString(list);
                setAllFields(listAsString, returnValue);
            } catch (JacksonException e1) {
                try {
                    final var map = ctxt.readValue(p, String.class);
                    final var mapAsString = om.writeValueAsString(map);
                    setAllFields(mapAsString, returnValue);
                } catch (JacksonException e2) {
                    try {
                        final var map = ctxt.readValue(p, Number.class);
                        final var mapAsString = om.writeValueAsString(map);
                        setAllFields(mapAsString, returnValue);
                    } catch (JacksonException e3) {
                        log.debug("Failed to parse", e3);
                        return null;
                    }
                }
            }
        }
        return returnValue;
    }

    private void setAllFields(String mapAsString, T returnValue) {
        for (var pair : fields) {
            final boolean successful = setField(pair, mapAsString, returnValue);
            if (mode == Mode.ONE_OF && successful) {
                return;
            }
        }
    }

    private <X> boolean setField(SettableField<T, X> field, String string, T retval) {
        final var clazz = field.type();
        final var consumer = field.setter();

        try {
            final X x = om.readValue(string, clazz);
            consumer.accept(retval, x);
            return true;
        } catch (JacksonException e) {
            log.debug("Failed to parse {} as {}", string, clazz, e);
            return false;
        }
    }
}
