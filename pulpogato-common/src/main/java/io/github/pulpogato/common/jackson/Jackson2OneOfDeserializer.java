package io.github.pulpogato.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A Jackson 2 deserializer for a non-discriminated {@code oneOf} modeled as a sealed interface.
 *
 * <p>With no discriminator property to route on, each candidate subtype is tried in declaration
 * order and the first that parses without unknown properties wins. This is the same disambiguation
 * the wrapper-based {@link Jackson2FancyDeserializer} performs in {@link io.github.pulpogato.common.Mode#ONE_OF},
 * but it yields the concrete subtype directly instead of a wrapper holding one field per branch.
 *
 * @param <T> The sealed interface type
 */
public class Jackson2OneOfDeserializer<T> extends StdDeserializer<T> {

    // FAIL_ON_UNKNOWN_PROPERTIES is what lets a wrong candidate be rejected: a payload shaped for one
    // branch will carry properties the other branch does not declare, so it fails and we move on.
    private static final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final transient List<Class<? extends T>> candidates;

    /**
     * Constructs a deserializer.
     *
     * @param vc         The sealed interface type
     * @param candidates The permitted subtypes, tried in order
     */
    public Jackson2OneOfDeserializer(Class<T> vc, List<Class<? extends T>> candidates) {
        super(vc);
        this.candidates = candidates;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        final var map = ctxt.readValue(p, Map.class);
        final var json = om.writeValueAsString(map);
        for (final var candidate : candidates) {
            try {
                return om.readValue(json, candidate);
            } catch (Exception e) {
                // Not this branch; try the next candidate.
            }
        }
        return null;
    }
}
