package io.github.pulpogato.common.jackson;

import io.github.pulpogato.common.NullableOptional;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Custom Jackson deserializer for {@link NullableOptional} that handles three-state deserialization:
 * <ul>
 *   <li><b>Field absent</b>: Lombok initializes to notSet() via @Builder.Default</li>
 *   <li><b>Field present with null</b>: Deserializes to ofNull()</li>
 *   <li><b>Field present with value</b>: Deserializes to of(value)</li>
 * </ul>
 */
public class NullableOptionalJackson3Deserializer extends StdDeserializer<NullableOptional<?>> {

    /**
     * The type of the value contained in the NullableOptional.
     */
    private final JavaType valueType;

    /**
     * Default constructor required by Jackson for deserializer instantiation.
     */
    public NullableOptionalJackson3Deserializer() {
        super(NullableOptional.class);
        this.valueType = null;
    }

    private NullableOptionalJackson3Deserializer(JavaType valueType) {
        super(NullableOptional.class);
        this.valueType = valueType;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = property != null ? property.getType().containedType(0) : null;
        return new NullableOptionalJackson3Deserializer(type);
    }

    @Override
    public NullableOptional<?> deserialize(JsonParser p, DeserializationContext ctxt) {
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
