package io.github.pulpogato.common.jackson;

import io.github.pulpogato.common.Mode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lenient deserialization logic for github-files unions.
 *
 * <p>This keeps unknown-key fallback behavior out of {@link FancyDeserializerSupport}, so core
 * deserialization remains strict.
 *
 * @param <T> The type being deserialized
 */
public class LenientFancyDeserializerSupport<T> extends FancyDeserializerSupport<T> {
    private static final Map<Class<?>, Set<String>> JSON_PROPERTY_CACHE = new ConcurrentHashMap<>();
    private final JsonReader lenientReader;

    /**
     * Constructs the support instance.
     *
     * @param type               The class being deserialized
     * @param initializer        Supplier that creates a new instance
     * @param mode               The deserialization mode (anyOf, oneOf, allOf)
     * @param fields             The fields that can be set
     * @param writer             Jackson-version-specific JSON writer
     * @param reader             Jackson-version-specific JSON reader
     * @param lenientReader      Jackson-version-specific reader with unknown-property tolerance
     * @param isParsingException Predicate that identifies Jackson parsing exceptions
     */
    public LenientFancyDeserializerSupport(
            Class<T> type,
            Supplier<T> initializer,
            Mode mode,
            List<SettableField<T, ?>> fields,
            JsonWriter writer,
            JsonReader reader,
            JsonReader lenientReader,
            Predicate<Exception> isParsingException) {
        super(type, initializer, mode, fields, writer, reader, isParsingException);
        this.lenientReader = lenientReader;
    }

    @Override
    protected void handleMapValue(Object mapValue, String mapAsString, T returnValue) {
        if (mode() == Mode.ONE_OF) {
            setOneOfField(mapValue, mapAsString, returnValue);
            return;
        }
        setAllFields(mapAsString, returnValue);
    }

    private void setOneOfField(Object mapValue, String mapAsString, T returnValue) {
        if (!(mapValue instanceof Map<?, ?> map)) {
            setAllFields(mapAsString, returnValue);
            return;
        }

        var sortedCandidates = orderOneOfCandidatesByRecognizedKeys(map);
        for (var pair : sortedCandidates) {
            final boolean successful = setOneOfFieldWithUnknownKeyFallback(pair, map, mapAsString, returnValue);
            if (successful) {
                return;
            }
        }
    }

    private List<SettableField<T, ?>> orderOneOfCandidatesByRecognizedKeys(Map<?, ?> inputMap) {
        var inputKeys = stringKeys(inputMap);
        if (inputKeys.isEmpty()) {
            return fields();
        }

        var sorted = new ArrayList<>(fields());
        sorted.sort((left, right) -> {
            var leftScore = countRecognizedKeys(inputKeys, knownJsonProperties(left.type()));
            var rightScore = countRecognizedKeys(inputKeys, knownJsonProperties(right.type()));
            return Integer.compare(rightScore, leftScore);
        });
        return sorted;
    }

    private int countRecognizedKeys(Set<String> inputKeys, Set<String> knownKeys) {
        if (inputKeys.isEmpty() || knownKeys.isEmpty()) {
            return 0;
        }
        var score = 0;
        for (var key : inputKeys) {
            if (knownKeys.contains(key)) {
                score++;
            }
        }
        return score;
    }

    private Set<String> stringKeys(Map<?, ?> inputMap) {
        if (inputMap.isEmpty()) {
            return Collections.emptySet();
        }

        var keys = new HashSet<String>();
        for (var key : inputMap.keySet()) {
            if (key instanceof String str) {
                keys.add(str);
            }
        }
        return keys;
    }

    private boolean setOneOfFieldWithUnknownKeyFallback(
            SettableField<T, ?> field, Map<?, ?> inputMap, String mapAsString, T retval) {
        if (super.setField(field, mapAsString, retval)) {
            return true;
        }

        var knownKeys = knownJsonProperties(field.type());
        if (knownKeys.isEmpty()) {
            return false;
        }

        var inputKeys = stringKeys(inputMap);
        var recognizedCount = countRecognizedKeys(inputKeys, knownKeys);
        if (recognizedCount == 0) {
            return false;
        }
        if (knownKeys.containsAll(inputKeys)) {
            // No unknown keys at this level, but nested strict parsing may still fail.
            return setFieldLenient(field, mapAsString, retval);
        }

        var filtered = filterToKnownKeys(inputMap, knownKeys);
        if (filtered.isEmpty()) {
            return false;
        }

        try {
            var filteredAsString = writer().writeValueAsString(filtered);
            if (super.setField(field, filteredAsString, retval)) {
                return true;
            }
        } catch (Exception e) {
            ensureParsingException(e);
        }
        return setFieldLenient(field, mapAsString, retval);
    }

    private static Set<String> knownJsonProperties(Class<?> type) {
        return JSON_PROPERTY_CACHE.computeIfAbsent(type, LenientFancyDeserializerSupport::scanKnownJsonProperties);
    }

    private static Set<String> scanKnownJsonProperties(Class<?> type) {
        var names = new HashSet<String>();
        var current = type;
        while (current != null && current != Object.class) {
            for (var field : current.getDeclaredFields()) {
                var jackson2Property = field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
                if (jackson2Property != null && !jackson2Property.value().isBlank()) {
                    names.add(jackson2Property.value());
                    continue;
                }

                names.add(field.getName());
            }
            current = current.getSuperclass();
        }
        return names;
    }

    private Map<String, Object> filterToKnownKeys(Map<?, ?> inputMap, Set<String> knownKeys) {
        var filtered = new LinkedHashMap<String, Object>();
        for (var entry : inputMap.entrySet()) {
            if (entry.getKey() instanceof String key && knownKeys.contains(key)) {
                filtered.put(key, entry.getValue());
            }
        }
        return filtered;
    }

    private <X> boolean setFieldLenient(SettableField<T, X> field, String string, T retval) {
        if (lenientReader == null) {
            return false;
        }
        return setFieldWithReader(field, string, retval, lenientReader);
    }
}
