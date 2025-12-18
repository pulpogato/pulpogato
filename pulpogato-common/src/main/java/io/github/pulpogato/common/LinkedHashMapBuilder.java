package io.github.pulpogato.common;

import java.util.LinkedHashMap;
import java.util.Map;

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
     * Creates a LinkedHashMap from the provided entries.
     *
     * @param entries the map entries to include in the LinkedHashMap
     * @param <K>     the type of keys maintained by the map
     * @param <V>     the type of mapped values
     * @return a LinkedHashMap containing the provided entries
     */
    @SafeVarargs
    public static <K, V> LinkedHashMap<K, V> of(Map.Entry<K, V>... entries) {
        var map = new LinkedHashMap<K, V>();
        for (var entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
