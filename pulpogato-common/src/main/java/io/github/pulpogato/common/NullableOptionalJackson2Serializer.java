package io.github.pulpogato.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

/**
 * Custom Jackson serializer for {@link NullableOptional} that handles three-state serialization:
 * <ul>
 *   <li><b>NOT_SET</b>: Field omitted from JSON (isEmpty returns true)</li>
 *   <li><b>NULL</b>: Field serialized as "field": null</li>
 *   <li><b>VALUE</b>: Field serialized with its value</li>
 * </ul>
 */
public class NullableOptionalJackson2Serializer extends StdSerializer<NullableOptional<?>> {

    /**
     * Default constructor required by Jackson for serializer instantiation.
     */
    public NullableOptionalJackson2Serializer() {
        super(NullableOptional.class, false);
    }

    @Override
    public void serialize(NullableOptional<?> value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (value.isNull()) {
            gen.writeNull();
        } else if (!value.isNotSet()) {
            gen.writePOJO(value.getValue());
        }
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, NullableOptional<?> value) {
        // Return true for NOT_SET to skip serialization entirely
        return value == null || value.isNotSet();
    }
}
