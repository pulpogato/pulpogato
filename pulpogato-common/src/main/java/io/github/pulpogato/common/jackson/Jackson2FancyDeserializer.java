package io.github.pulpogato.common.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.Mode;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Jackson 2 deserializer that can handle <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 *
 * @param <T> The type
 */
public class Jackson2FancyDeserializer<T> extends StdDeserializer<T> {

    private static final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    private final transient FancyDeserializerSupport<T> support;

    /**
     * Constructs a deserializer
     *
     * @param vc          The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode        The mode of deserialization
     * @param fields      The fields that can be set on the class
     */
    public Jackson2FancyDeserializer(
            Class<T> vc,
            Supplier<T> initializer,
            Mode mode,
            List<FancyDeserializerSupport.SettableField<T, ?>> fields) {
        super(vc);
        this.support = new FancyDeserializerSupport<>(
                vc,
                initializer,
                mode,
                fields,
                om::writeValueAsString,
                om::readValue,
                JacksonException.class::isInstance);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) {
        return support.deserialize(type -> ctxt.readValue(p, type));
    }
}
