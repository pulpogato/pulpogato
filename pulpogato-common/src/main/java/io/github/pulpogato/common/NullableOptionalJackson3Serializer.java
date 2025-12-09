package io.github.pulpogato.common;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Custom Jackson serializer for {@link NullableOptional} that handles three-state serialization:
 * <ul>
 *   <li><b>NOT_SET</b>: Field omitted from JSON (isEmpty returns true)</li>
 *   <li><b>NULL</b>: Field serialized as "field": null</li>
 *   <li><b>VALUE</b>: Field serialized with its value</li>
 * </ul>
 */
public class NullableOptionalJackson3Serializer extends StdSerializer<NullableOptional<?>> {

    /**
     * Default constructor required by Jackson for serializer instantiation.
     */
    public NullableOptionalJackson3Serializer() {
        super(NullableOptional.class);
    }

    @Override
    public void serialize(NullableOptional<?> value, JsonGenerator gen, SerializationContext provider) {
        if (value.isNull()) {
            gen.writeNull();
        } else if (!value.isNotSet()) {
            gen.writePOJO(value.getValue());
        }
    }

    @Override
    public boolean isEmpty(SerializationContext provider, NullableOptional<?> value) {
        // Return true for NOT_SET to skip serialization entirely
        return value == null || value.isNotSet();
    }
}
