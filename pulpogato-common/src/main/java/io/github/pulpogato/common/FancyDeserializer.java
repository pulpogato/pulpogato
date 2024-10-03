package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A deserializer that can handle <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 *
 * @param <T> The type
 */
@Slf4j
public class FancyDeserializer<T> extends StdDeserializer<T>  {

    /**
     * A field that can be set on the field
     *
     * @param type The class of the object
     * @param setter The method that sets the field on the object
     * @param <T> The type of the object
     * @param <X> The type of the field
     */
    public record SettableField<T, X>(Class<X> type, BiConsumer<T, X> setter) {}

    private static final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Constructs a deserializer
     *
     * @param vc The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode The mode of deserialization
     * @param fields The fields that can be set on the class
     */
    public FancyDeserializer(Class<T> vc, Supplier<T> initializer, Mode mode, List<SettableField<T, ?>> fields) {
        super(vc);
        this.initializer = initializer;
        this.mode = mode;
        this.fields = fields;
    }

    /**
     * The supplier that creates a new instance of the class
     */
    private final Supplier<T> initializer;

    /**
     * The mode of deserialization
     */
    private final Mode mode;

    /**
     * The fields that can be set on the class
     */
    private final List<SettableField<T, ?>> fields;

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var map = ctxt.readValue(p, Map.class);
        var string = om.writeValueAsString(map);
        var retval = initializer.get();
        fields.forEach(pair -> setField(pair, string, retval));
        return retval;
    }

    private <X> void setField(SettableField<T, X> field, String string, T retval) {
        var clazz = field.type();
        var consumer = field.setter();

        try {
            var x = om.readValue(string, clazz);
            consumer.accept(retval, x);
        } catch (JacksonException e) {
            log.debug("Failed to parse {} as {}", string, clazz, e);
        }
    }

}