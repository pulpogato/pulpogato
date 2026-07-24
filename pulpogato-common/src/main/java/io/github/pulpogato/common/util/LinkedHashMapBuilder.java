package io.github.pulpogato.common.util;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for building LinkedHashMap instances in a concise manner.
 */
public class LinkedHashMapBuilder {
    /**
     * Private constructor to prevent instantiation.
     */
    private LinkedHashMapBuilder() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    /**
     * Creates a Map.Entry from the provided key and value.
     * This entry supports null values.
     *
     * @param key   the key
     * @param value the value
     * @param <K>   the type of key
     * @param <V>   the type of value
     * @return a Map.Entry containing the provided key and value
     */
    public static <K, V> Map.Entry<@Nullable K, @Nullable V> entry(@Nullable K key, @Nullable V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    /**
     * Creates a LinkedHashMap from the provided entries.
     *
     * @param entries the map entries to include in the LinkedHashMap
     * @param <K>     the type of keys maintained by the map
     * @param <V>     the type of mapped values
     * @return a LinkedHashMap containing the provided entries
     */
    @SafeVarargs
    public static <K, V> Map<K, V> of(Map.Entry<K, V>... entries) {
        var map = LinkedHashMap.<K, V>newLinkedHashMap(entries.length);
        for (var entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
