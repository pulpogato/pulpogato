package io.github.pulpogato.common.jackson;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Jackson 3 deserializer for a non-discriminated {@code oneOf} modeled as a sealed interface.
 *
 * <p>With no discriminator property to route on, each candidate subtype is tried in declaration
 * order and the first that parses without unknown properties wins. This is the same disambiguation
 * the wrapper-based {@link Jackson3FancyDeserializer} performs in {@link io.github.pulpogato.common.Mode#ONE_OF},
 * but it yields the concrete subtype directly instead of a wrapper holding one field per branch.
 *
 * @param <T> The sealed interface type
 */
@Slf4j
public class Jackson3OneOfDeserializer<T> extends StdDeserializer<T> {

    // FAIL_ON_UNKNOWN_PROPERTIES is what lets a wrong candidate be rejected: a payload shaped for one
    // branch will carry properties the other branch does not declare, so it fails and we move on.
    private static final JsonMapper om = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final transient List<Class<? extends T>> candidates;

    /**
     * Constructs a deserializer.
     *
     * @param vc         The sealed interface type
     * @param candidates The permitted subtypes, tried in order
     */
    public Jackson3OneOfDeserializer(Class<T> vc, List<Class<? extends T>> candidates) {
        super(vc);
        this.candidates = candidates;
    }

    @Override
    @Nullable
    public T deserialize(JsonParser p, DeserializationContext ctxt) {
        final var map = ctxt.readValue(p, Map.class);
        final var json = om.writeValueAsString(map);
        for (final var candidate : candidates) {
            try {
                return om.readValue(json, candidate);
            } catch (Exception e) {
                log.debug("Candidate {} did not match, trying next", candidate.getSimpleName(), e);
            }
        }
        return null;
    }
}
