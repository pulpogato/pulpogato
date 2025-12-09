package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Custom Jackson deserializer for {@link NullableOptional} that handles three-state deserialization:
 * <ul>
 *   <li><b>Field absent</b>: Lombok initializes to notSet() via @Builder.Default</li>
 *   <li><b>Field present with null</b>: Deserializes to ofNull()</li>
 *   <li><b>Field present with value</b>: Deserializes to of(value)</li>
 * </ul>
 */
public class NullableOptionalJackson2Deserializer extends StdDeserializer<NullableOptional<?>>
        implements ContextualDeserializer {

    private final JavaType valueType;

    public NullableOptionalJackson2Deserializer() {
        super(NullableOptional.class);
        this.valueType = null;
    }

    private NullableOptionalJackson2Deserializer(JavaType valueType) {
        super(NullableOptional.class);
        this.valueType = valueType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = property != null ? property.getType().containedType(0) : null;
        return new NullableOptionalJackson2Deserializer(type);
    }

    @Override
    public NullableOptional<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return NullableOptional.ofNull();
        }

        // Deserialize to the actual type parameter
        Object value;
        if (valueType != null) {
            value = ctxt.readValue(p, valueType);
        } else {
            value = ctxt.readValue(p, Object.class);
        }
        return NullableOptional.of(value);
    }

    @Override
    public NullableOptional<?> getNullValue(DeserializationContext ctxt) {
        // Called when field is present with explicit null value
        return NullableOptional.ofNull();
    }
}
