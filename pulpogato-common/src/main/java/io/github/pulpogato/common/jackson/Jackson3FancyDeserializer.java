package io.github.pulpogato.common.jackson;

import io.github.pulpogato.common.Mode;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Jackson 3 deserializer that can handle <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 *
 * @param <T> The type
 */
public class Jackson3FancyDeserializer<T> extends StdDeserializer<T> {

    private static final JsonMapper om = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final transient FancyDeserializerSupport<T> support;

    /**
     * Constructs a deserializer
     *
     * @param vc          The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode        The mode of deserialization
     * @param fields      The fields that can be set on the class
     */
    public Jackson3FancyDeserializer(
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
