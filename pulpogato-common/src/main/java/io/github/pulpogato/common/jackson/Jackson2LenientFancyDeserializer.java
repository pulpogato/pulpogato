package io.github.pulpogato.common.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.Mode;
import io.github.pulpogato.common.jackson.FancyDeserializerSupport.SettableField;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Jackson 2 deserializer with lenient ONE_OF fallback behavior.
 *
 * @param <T> The type
 */
public class Jackson2LenientFancyDeserializer<T> extends StdDeserializer<T> {

    private static final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final ObjectMapper lenientOm = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final transient LenientFancyDeserializerSupport<T> support;

    /**
     * Constructs a deserializer.
     *
     * @param vc          The class being deserialized
     * @param initializer The supplier that creates a new instance of the class
     * @param mode        The mode of deserialization
     * @param fields      The fields that can be set on the class
     */
    public Jackson2LenientFancyDeserializer(
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
