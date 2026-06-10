package io.github.pulpogato.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkedHashMapBuilderTest {

    @Test
    void shouldCreateEmptyMapWhenNoEntriesProvided() {
        Map<String, String> map = LinkedHashMapBuilder.of();

        assertThat(map).isEmpty();
    }

    @Test
    void shouldCreateSingleEntryMap() {
        Map<String, String> map = LinkedHashMapBuilder.of(LinkedHashMapBuilder.entry("only", "value"));

        assertThat(map).hasSize(1);
        assertThat(map).containsEntry("only", "value");
    }

    @Test
    void shouldHandleNullValues() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("key1", "value1"), LinkedHashMapBuilder.entry("key2", null));

        assertThat(map).hasSize(2);
        assertThat(map.get("key1")).isEqualTo("value1");
        assertThat(map.get("key2")).isNull();
        assertThat(map).containsEntry("key2", null);
    }

    @Test
    void shouldHandleNullKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry(null, "null-key-value"),
                LinkedHashMapBuilder.entry("key", "value"));

        assertThat(map).hasSize(2);
        assertThat(map).containsEntry(null, "null-key-value");
        assertThat(map.get(null)).isEqualTo("null-key-value");
    }

    @Test
    void shouldUseLastValueForDuplicateKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("dup", "first"),
                LinkedHashMapBuilder.entry("dup", "second"));

        assertThat(map).hasSize(1);
        assertThat(map).containsEntry("dup", "second");
    }

    @Test
    void shouldSupportDifferentTypeParameters() {
        Map<Integer, Number> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry(1, 10),
                LinkedHashMapBuilder.entry(2, 2.5d));

        assertThat(map).hasSize(2);
        assertThat(map.get(1)).isEqualTo(10);
        assertThat(map.get(2)).isEqualTo(2.5d);
    }

    @Test
    void shouldPreserveOrder() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("c", "3"),
                LinkedHashMapBuilder.entry("a", "1"),
                LinkedHashMapBuilder.entry("b", "2"));

        assertThat(map.keySet()).containsExactly("c", "a", "b");
    }
}
