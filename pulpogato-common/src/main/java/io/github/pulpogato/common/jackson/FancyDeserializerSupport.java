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
import lombok.extern.slf4j.Slf4j;

/**
 * Shared deserialization logic for <code>anyOf</code>, <code>allOf</code>, and <code>oneOf</code>.
 * Used by both Jackson 2 and Jackson 3 deserializers via composition.
 *
 * @param <T> The type being deserialized
 */
@Slf4j
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
     */
    @FunctionalInterface
    public interface ContextReader {
        Object readValue(Class<?> type) throws Exception;
    }

    /**
     * Serializes a value to a JSON string.
     */
    @FunctionalInterface
    public interface JsonWriter {
        String writeValueAsString(Object value) throws Exception;
    }

    /**
     * Deserializes a JSON string to a typed value.
     */
    @FunctionalInterface
    public interface JsonReader {
        Object readValue(String json, Class<?> type) throws Exception;
    }

    /**
     * Tracks types currently being deserialized on this thread to prevent infinite recursion
     * from self-referential union types (e.g., Permissions containing a Permissions field).
     */
    private static final ThreadLocal<Set<Class<?>>> IN_PROGRESS = ThreadLocal.withInitial(HashSet::new);

    private final Class<T> type;
    private final Supplier<T> initializer;
    private final Mode mode;
    private final List<SettableField<T, ?>> fields;
    private final JsonWriter writer;
    private final JsonReader reader;
    private final Predicate<Exception> isParsingException;
    private final Class<?> enumAlternativeType;

    /**
     * Constructs the support instance.
     *
     * @param type               The class being deserialized
     * @param initializer        Supplier that creates a new instance
     * @param mode               The deserialization mode (anyOf, oneOf, allOf)
     * @param fields             The fields that can be set
     * @param writer             Jackson-version-specific JSON writer
     * @param reader             Jackson-version-specific JSON reader
     * @param isParsingException Predicate that identifies Jackson parsing exceptions
     */
    public FancyDeserializerSupport(
            Class<T> type,
            Supplier<T> initializer,
            Mode mode,
            List<SettableField<T, ?>> fields,
            JsonWriter writer,
            JsonReader reader,
            Predicate<Exception> isParsingException) {
        this.type = type;
        this.initializer = initializer;
        this.mode = mode;
        this.fields = fields;
        this.writer = writer;
        this.reader = reader;
        this.isParsingException = isParsingException;
        this.enumAlternativeType = detectEnumAlternativeType(fields);
    }

    /**
     * Performs the deserialization by trying to read the input as Map, List, String, or Number.
     *
     * @param contextReader Reads from the Jackson parser/context
     * @return The deserialized value, or null if all attempts fail
     */
    public T deserialize(ContextReader contextReader) {
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
                    try {
                        final var str = contextReader.readValue(String.class);
                        final var strAsString = writer.writeValueAsString(str);
                        setAllFields(strAsString, returnValue);
                    } catch (Exception e2) {
                        ensureParsingException(e2);
                        try {
                            final var num = contextReader.readValue(Number.class);
                            final var numAsString = writer.writeValueAsString(num);
                            setAllFields(numAsString, returnValue);
                        } catch (Exception e3) {
                            ensureParsingException(e3);
                            log.debug("Failed to parse", e3);
                            return null;
                        }
                    }
                }
            }
            return returnValue;
        } finally {
            inProgress.remove(type);
        }
    }

    protected void handleMapValue(Object mapValue, String mapAsString, T returnValue) {
        setAllFields(mapAsString, returnValue);
    }

    protected final Mode mode() {
        return mode;
    }

    protected final List<SettableField<T, ?>> fields() {
        return fields;
    }

    protected final JsonWriter writer() {
        return writer;
    }

    protected final void ensureParsingException(Exception e) {
        if (!isParsingException.test(e)) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    protected final void setAllFields(String mapAsString, T returnValue) {
        for (var pair : fields) {
            final boolean successful = setField(pair, mapAsString, returnValue);
            if (mode == Mode.ONE_OF && successful) {
                return;
            }
        }
    }

    protected final <X> boolean setField(SettableField<T, X> field, String string, T retval) {
        return setFieldWithReader(field, string, retval, reader);
    }

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
