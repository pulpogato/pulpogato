package io.github.pulpogato.common;

import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapBuilder {
    private LinkedHashMapBuilder() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    @SafeVarargs
    public static <K, V> LinkedHashMap<K, V> of(Map.Entry<K, V>... entries) {
        var map = new LinkedHashMap<K, V>();
        for (var entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
