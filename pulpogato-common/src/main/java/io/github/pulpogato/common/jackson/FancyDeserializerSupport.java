package io.github.pulpogato.common.jackson;

import io.github.pulpogato.common.Mode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Shared deserialization logic for <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 * Used by both Jackson 2 and Jackson 3 deserializers via composition.
 *
 * @param <T> The type being deserialized
 */
@Slf4j
@RequiredArgsConstructor
public class FancyDeserializerSupport<T> {

    /**
     * A field that can be set on the deserialized object.
     *
     * @param type   The class of the field value
     * @param setter The method that sets the field on the object
     * @param <T>    The type of the object
     * @param <X>    The type of the field
     */
    public record SettableField<T, X>(Class<X> type, BiConsumer<T, X> setter) {}

    /**
     * Reads a value from the JSON input (wraps parser + context).
     *
     * <p>Declares {@code throws Exception} because implementations bridge Jackson 2 (checked
     * {@code IOException}) and Jackson 3 (unchecked {@code JacksonException}); those hierarchies
     * share no narrower common checked type.
     */
    @SuppressWarnings("java:S112")
    @FunctionalInterface
    public interface ContextReader {
        /**
         * Reads a value of the specified type from the JSON input.
         *
         * @param type the type to read
         * @return the deserialized value
         * @throws Exception if reading fails
         */
        Object readValue(Class<?> type) throws Exception;
    }

    /**
     * Serializes a value to a JSON string.
     *
     * <p>See {@link ContextReader} for why this declares {@code throws Exception}.
     */
    @SuppressWarnings("java:S112")
    @FunctionalInterface
    public interface JsonWriter {
        /**
         * Writes an object as a JSON string.
         *
         * @param value the value to serialize
         * @return the JSON string representation
         * @throws Exception if serialization fails
         */
        String writeValueAsString(Object value) throws Exception;
    }

    /**
     * Deserializes a JSON string to a typed value.
     *
     * <p>See {@link ContextReader} for why this declares {@code throws Exception}.
     */
    @SuppressWarnings("java:S112")
    @FunctionalInterface
    public interface JsonReader {
        /**
         * Reads a JSON string and deserializes it to the specified type.
         *
         * @param json the JSON string to deserialize
         * @param type the target type
         * @return the deserialized value
         * @throws Exception if deserialization fails
         */
        Object readValue(String json, Class<?> type) throws Exception;
    }

    /**
     * Tracks types currently being deserialized on this thread to prevent infinite recursion
     * from self-referential union types (e.g., Permissions containing a Permissions field).
     */
    private static final ThreadLocal<Set<Class<?>>> IN_PROGRESS = ThreadLocal.withInitial(HashSet::new);

    /**
     * The class being deserialized.
     */
    private final Class<T> type;

    /**
     * Supplier that creates a new instance.
     */
    private final Supplier<T> initializer;

    /**
     * The deserialization mode (anyOf, oneOf, allOf).
     */
    @Getter
    private final Mode mode;

    /**
     * The fields that can be set.
     */
    @Getter
    private final List<SettableField<T, ?>> fields;

    /**
     * Jackson-version-specific JSON writer.
     */
    @Getter
    private final JsonWriter writer;

    /**
     * Jackson-version-specific JSON reader.
     */
    private final JsonReader reader;

    /**
     * Predicate that identifies Jackson parsing exceptions.
     */
    private final Predicate<Exception> isParsingException;

    /**
     * Hint about the current JSON token type, supplied by the calling deserializer when it can read
     * the token type from the parser. Used by YAML-aware deserializers to preserve the original
     * scalar type (boolean/number) instead of letting Jackson coerce it to String first.
     */
    public enum TokenHint {
        /** Input token is a boolean literal (true/false). */
        BOOLEAN,
        /** Input token is a numeric literal. */
        NUMBER
    }

    /**
     * Performs the deserialization by trying to read the input as Map, List, String, or Number.
     * Uses String-before-Number order to preserve Jackson's default coercion behavior for
     * JSON input.
     *
     * @param contextReader Reads from the Jackson parser/context
     * @return The deserialized value, or null if all attempts fail
     */
    public T deserialize(ContextReader contextReader) {
        return deserialize(contextReader, null);
    }

    /**
     * Performs the deserialization with an optional token type hint.
     *
     * <p>When a hint is present (set by YAML-aware deserializers that can inspect the raw token),
     * the scalar-read order is adjusted so that the native type is tried first, preventing Jackson
     * from coercing e.g. a YAML boolean into a String before the Boolean branch gets a chance.
     *
     * @param contextReader Reads from the Jackson parser/context
     * @param hint          The token type hint, or {@code null} for default String-first ordering
     * @return The deserialized value, or null if all attempts fail
     */
    public T deserialize(ContextReader contextReader, @Nullable TokenHint hint) {
        final var returnValue = initializer.get();
        var inProgress = IN_PROGRESS.get();
        inProgress.add(type);

        try {
            try {
                final var map = contextReader.readValue(Map.class);
                final var mapAsString = writer.writeValueAsString(map);
                handleMapValue(map, mapAsString, returnValue);
            } catch (Exception e) {
                ensureParsingException(e);
                try {
                    final var list = contextReader.readValue(List.class);
                    final var listAsString = writer.writeValueAsString(list);
                    setAllFields(listAsString, returnValue);
                } catch (Exception e1) {
                    ensureParsingException(e1);
                    deserializeScalar(contextReader, hint, returnValue);
                }
            }
            return returnValue;
        } finally {
            inProgress.remove(type);
            // Recursive calls share the same Set, so only clear the ThreadLocal binding once
            // the outermost call finishes; otherwise pooled threads never release the HashSet.
            if (inProgress.isEmpty()) {
                IN_PROGRESS.remove();
            }
        }
    }

    private void deserializeScalar(ContextReader contextReader, @Nullable TokenHint hint, T returnValue) {
        switch (hint) {
            case TokenHint.BOOLEAN -> deserializeBoolThenNumberThenString(contextReader, returnValue);
            case TokenHint.NUMBER -> deserializeNumberThenString(contextReader, returnValue);
            case null, default -> deserializeStringThenNumber(contextReader, returnValue);
        }
    }

    private void deserializeBoolThenNumberThenString(ContextReader contextReader, T returnValue) {
        try {
            final var bool = contextReader.readValue(Boolean.class);
            final var boolAsString = writer.writeValueAsString(bool);
            setAllFields(boolAsString, returnValue);
        } catch (Exception e2) {
            ensureParsingException(e2);
            deserializeNumberThenString(contextReader, returnValue);
        }
    }

    private void deserializeNumberThenString(ContextReader contextReader, T returnValue) {
        try {
            final var num = contextReader.readValue(Number.class);
            final var numAsString = writer.writeValueAsString(num);
            setAllFields(numAsString, returnValue);
        } catch (Exception e3) {
            ensureParsingException(e3);
            deserializeString(contextReader, returnValue);
        }
    }

    private void deserializeStringThenNumber(ContextReader contextReader, T returnValue) {
        try {
            final var str = contextReader.readValue(String.class);
            final var strAsString = writer.writeValueAsString(str);
            setAllFields(strAsString, returnValue);
        } catch (Exception e4) {
            ensureParsingException(e4);
            try {
                final var num = contextReader.readValue(Number.class);
                final var numAsString = writer.writeValueAsString(num);
                setAllFields(numAsString, returnValue);
            } catch (Exception e5) {
                ensureParsingException(e5);
                log.debug("Failed to parse", e5);
            }
        }
    }

    private void deserializeString(ContextReader contextReader, T returnValue) {
        try {
            final var str = contextReader.readValue(String.class);
            final var strAsString = writer.writeValueAsString(str);
            setAllFields(strAsString, returnValue);
        } catch (Exception e) {
            ensureParsingException(e);
            log.debug("Failed to parse", e);
        }
    }

    /**
     * Handles a deserialized map value by converting it to a string and setting all fields.
     *
     * @param mapValue    the deserialized map value
     * @param mapAsString the map serialized as a JSON string
     * @param returnValue the object being populated
     */
    protected void handleMapValue(Object mapValue, String mapAsString, T returnValue) {
        setAllFields(mapAsString, returnValue);
    }

    /**
     * Ensures that the given exception is a parsing exception. If not, wraps it in a runtime exception.
     *
     * @param e the exception to check
     * @throws RuntimeException if the exception is not a parsing exception
     */
    protected final void ensureParsingException(Exception e) {
        if (!isParsingException.test(e)) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets fields on the return value according to the mode's semantics:
     * <ul>
     *   <li>{@link Mode#ONE_OF}: exactly one field must match; throws if multiple match.</li>
     *   <li>{@link Mode#ALL_OF}: every field must match; throws if any field fails.</li>
     *   <li>{@link Mode#ANY_OF}: sets every field that successfully deserializes.</li>
     * </ul>
     *
     * @param mapAsString the JSON string to deserialize
     * @param returnValue the object being populated
     */
    protected final void setAllFields(String mapAsString, T returnValue) {
        if (mode == Mode.ONE_OF) {
            setOneOfField(mapAsString, returnValue);
        } else {
            for (var pair : fields) {
                setField(pair, mapAsString, returnValue);
            }
        }
    }

    /**
     * Finds the single field whose type matches the JSON input and sets it on {@code returnValue}.
     * {@link Object} fields are treated as a catch-all wildcard: skipped during the strict-match
     * pass and used only when no more specific field matched. If more than one specific field
     * matches, the first match wins and the ambiguity is logged rather than thrown.
     *
     * @param mapAsString the JSON string to deserialize
     * @param returnValue the object being populated
     */
    private void setOneOfField(String mapAsString, T returnValue) {
        var match = findOneOfMatch(mapAsString);
        if (match != null) {
            setField(match, mapAsString, returnValue);
        } else {
            fields.stream()
                    .filter(pair -> pair.type() == Object.class)
                    .findFirst()
                    .ifPresent(objectFallback -> setField(objectFallback, mapAsString, returnValue));
        }
    }

    /**
     * Finds the single non-wildcard field whose type matches the JSON input.
     *
     * @param mapAsString the JSON string to deserialize
     * @return the matching field, or {@code null} if none match
     */
    private @Nullable SettableField<T, ?> findOneOfMatch(String mapAsString) {
        SettableField<T, ?> match = null;
        for (var pair : fields) {
            if (pair.type() == Object.class || !isOneOfCandidate(pair, mapAsString)) {
                continue;
            }
            if (match == null) {
                match = pair;
            } else {
                log.debug(
                        "Ambiguous oneOf for {}: both {} and {} matched; using first match",
                        type.getSimpleName(),
                        match.type().getSimpleName(),
                        pair.type().getSimpleName());
            }
        }
        return match;
    }

    /**
     * Checks whether a non-wildcard field is a viable oneOf match: its scalar type (if any) must
     * be compatible with the JSON token, and the JSON must actually deserialize into it.
     *
     * @param pair        the field to probe
     * @param mapAsString the JSON string to deserialize
     * @return true if the field successfully deserializes and is a candidate match
     */
    private boolean isOneOfCandidate(SettableField<T, ?> pair, String mapAsString) {
        // Scalar types (String, Number subclasses, Boolean) must match the JSON token type.
        // Without this guard, Jackson's default coercion lets a JSON number satisfy String
        // (and vice versa), producing spurious ambiguity in oneOf unions like String|BigDecimal.
        if (isScalarType(pair.type()) && !isJsonTokenCompatible(mapAsString, pair.type())) {
            return false;
        }
        var probe = initializer.get();
        return setField(pair, mapAsString, probe);
    }

    /**
     * Attempts to set a single field by deserializing the JSON string.
     *
     * @param field the field to set
     * @param string the JSON string to deserialize
     * @param retval the object being populated
     * @param <X> the type of the field
     * @return true if the field was successfully set, false otherwise
     */
    protected final <X> boolean setField(SettableField<T, X> field, String string, T retval) {
        return setFieldWithReader(field, string, retval, reader);
    }

    /**
     * Attempts to set a single field using the specified JSON reader.
     *
     * @param field the field to set
     * @param string the JSON string to deserialize
     * @param retval the object being populated
     * @param activeReader the JSON reader to use for deserialization
     * @param <X> the type of the field
     * @return true if the field was successfully set, false otherwise
     */
    protected final <X> boolean setFieldWithReader(
            SettableField<T, X> field, String string, T retval, JsonReader activeReader) {
        final var clazz = field.type();
        final var consumer = field.setter();

        if (IN_PROGRESS.get().contains(clazz)) {
            return false;
        }

        try {
            final var raw = activeReader.readValue(string, clazz);
            @SuppressWarnings("unchecked")
            final var x = (X) coerceListValuesIfNeeded(clazz, raw);
            consumer.accept(retval, x);
            return true;
        } catch (Exception e) {
            ensureParsingException(e);
            log.debug("Failed to parse {} as {}", string, clazz, e);
            return false;
        }
    }

    private static boolean isScalarType(Class<?> clazz) {
        return clazz == String.class
                || clazz == Boolean.class
                || clazz == boolean.class
                || Number.class.isAssignableFrom(clazz)
                || clazz.isPrimitive();
    }

    private static boolean isJsonTokenCompatible(@Nullable String json, Class<?> clazz) {
        if (json == null || json.isEmpty()) return true;
        int i = 0;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i == json.length()) return true;
        char first = json.charAt(i);
        if (clazz == String.class) return first == '"' || json.startsWith("null", i);
        if (clazz == Boolean.class || clazz == boolean.class) {
            return json.startsWith("true", i) || json.startsWith("false", i) || json.startsWith("null", i);
        }
        if (Number.class.isAssignableFrom(clazz)) {
            return (first >= '0' && first <= '9') || first == '-' || json.startsWith("null", i);
        }
        return true;
    }

    @Nullable
    private static <T> Class<?> detectEnumAlternativeType(List<SettableField<T, ?>> fields) {
        Class<?> candidate = null;
        for (var field : fields) {
            var type = field.type();
            if (!type.isEnum()) {
                continue;
            }
            if (candidate == null) {
                candidate = type;
            } else if (!candidate.equals(type)) {
                return null;
            }
        }
        return candidate;
    }

    private Object coerceListValuesIfNeeded(Class<?> clazz, Object value) {
        Class<?> enumAlternativeType = detectEnumAlternativeType(fields);
        if (clazz != List.class || enumAlternativeType == null || !(value instanceof List<?> list) || list.isEmpty()) {
            return value;
        }

        final var converted = new ArrayList<>(list.size());
        for (var item : list) {
            if (enumAlternativeType.isInstance(item)) {
                converted.add(item);
                continue;
            }
            if (!(item instanceof String)) {
                return value;
            }
            try {
                final var itemJson = writer.writeValueAsString(item);
                converted.add(reader.readValue(itemJson, enumAlternativeType));
            } catch (Exception e) {
                ensureParsingException(e);
                return value;
            }
        }
        return converted;
    }
}
