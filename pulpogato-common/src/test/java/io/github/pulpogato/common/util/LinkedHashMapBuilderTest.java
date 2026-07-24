package io.github.pulpogato.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkedHashMapBuilderTest {

    @Test
    void shouldHandleNullValues() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("key1", "value1"), LinkedHashMapBuilder.entry("key2", null));

        assertThat(map)
                .isInstanceOf(LinkedHashMap.class)
                .hasSize(2)
                .containsEntry("key1", "value1")
                .containsEntry("key2", null);
    }

    @Test
    void shouldPreserveOrder() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("c", "3"),
                LinkedHashMapBuilder.entry("a", "1"),
                LinkedHashMapBuilder.entry("b", "2"));

        assertThat(map).isInstanceOf(LinkedHashMap.class).hasSize(3);
        assertThat(map.keySet()).containsExactly("c", "a", "b");
    }

    @Test
    void shouldReturnEmptyMapWhenNoEntriesProvided() {
        Map<String, String> map = LinkedHashMapBuilder.of();

        assertThat(map).isEmpty();
    }

    @Test
    void shouldHandleSingleEntry() {
        Map<String, String> map = LinkedHashMapBuilder.of(LinkedHashMapBuilder.entry("only", "value"));

        assertThat(map).isInstanceOf(LinkedHashMap.class).hasSize(1).containsEntry("only", "value");
    }

    @Test
    void shouldHandleNullKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(LinkedHashMapBuilder.entry(null, "value"));

        assertThat(map).isInstanceOf(LinkedHashMap.class).hasSize(1).containsEntry(null, "value");
    }

    @Test
    void shouldOverwriteOnDuplicateKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("key", "first"), LinkedHashMapBuilder.entry("key", "second"));

        assertThat(map).isInstanceOf(LinkedHashMap.class).hasSize(1).containsEntry("key", "second");
    }

    @Test
    void shouldSupportMixedNumericValueTypes() {
        Map<String, Number> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("int", 1), LinkedHashMapBuilder.entry("float", 1.5f));

        assertThat(map)
                .isInstanceOf(LinkedHashMap.class)
                .hasSize(2)
                .containsEntry("int", 1)
                .containsEntry("float", 1.5f);
    }
}
