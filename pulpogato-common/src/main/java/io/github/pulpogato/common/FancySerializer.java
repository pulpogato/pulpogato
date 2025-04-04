package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A serializer that can handle <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 *
 * @param <T> The type
 */
public class FancySerializer<T> extends StdSerializer<T> {
    /**
     * A field that can be read from the object
     *
     * @param type   The class of the object
     * @param getter The method that gets the field from the object
     * @param <T>    The type of the object
     * @param <X>    The type of the field
     */
    public record GettableField<T, X>(Class<X> type, Function<T, X> getter) {
    }

    private static final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Constructs a serializer
     *
     * @param vc     The class being serialized
     * @param mode   The mode of serialization
     * @param fields The fields that can be read from the class
     */
    public FancySerializer(Class<T> vc, Mode mode, List<GettableField<T, ?>> fields) {
        super(vc);
        this.mode = mode;
        this.fields = fields;
    }

    /**
     * The mode of serialization
     */
    private final Mode mode;

    /**
     * The fields that can be read from the class
     */
    private final transient List<GettableField<T, ?>> fields;

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        final var serialized = fields.stream()
                .map(field -> field.getter().apply(value))
                .filter(Objects::nonNull)
                .toList();

        if (mode == Mode.ONE_OF) {
            final Object o = serialized.isEmpty() ? null : serialized.getFirst();
            switch (o) {
                case Integer i -> gen.writeNumber(i);
                case Long l -> gen.writeNumber(l);
                case Double d -> gen.writeNumber(d);
                case Float f -> gen.writeNumber(f);
                case BigDecimal bd -> gen.writeNumber(bd);
                case BigInteger bi -> gen.writeNumber(bi);
                case Boolean b -> gen.writeBoolean(b);
                case String s -> gen.writeString(s);
                case null, default -> gen.writeObject(o);
            }
            return;
        }

        var superMap = serialized.stream()
                .map(x -> {
                    try {
                        final String string = om.writeValueAsString(x);
                        return om.readValue(string, new TypeReference<Map<String, Object>>() {});
                    } catch (JsonProcessingException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .reduce(new LinkedHashMap<>(), (m1, m2) -> {
                    m1.putAll(m2);
                    return m1;
                });
        gen.writeObject(superMap);
    }
}
