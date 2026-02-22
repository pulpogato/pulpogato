package io.github.pulpogato.common.jackson;

import io.github.pulpogato.common.Mode;
import io.github.pulpogato.common.jackson.FancyDeserializerSupport.SettableField;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Jackson 3 deserializer with lenient ONE_OF fallback behavior.
 *
 * @param <T> The type
 */
public class Jackson3LenientFancyDeserializer<T> extends StdDeserializer<T> {

    private static final JsonMapper om = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final JsonMapper lenientOm = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final transient LenientFancyDeserializerSupport<T> support;

    /**
     * Constructs a deserializer.
     *
     * @param vc          The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode        The mode of deserialization
     * @param fields      The fields that can be set on the class
     */
    public Jackson3LenientFancyDeserializer(
            Class<T> vc, Supplier<T> initializer, Mode mode, List<SettableField<T, ?>> fields) {
        super(vc);
        this.support = new LenientFancyDeserializerSupport<>(
                vc,
                initializer,
                mode,
                fields,
                om::writeValueAsString,
                om::readValue,
                lenientOm::readValue,
                JacksonException.class::isInstance);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) {
        return support.deserialize(type -> ctxt.readValue(p, type));
    }
}
