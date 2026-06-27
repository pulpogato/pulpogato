package io.github.pulpogato.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkedHashMapBuilderTest {

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
    void shouldPreserveOrder() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("c", "3"),
                LinkedHashMapBuilder.entry("a", "1"),
                LinkedHashMapBuilder.entry("b", "2"));

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

        assertThat(map).hasSize(1);
        assertThat(map.get("only")).isEqualTo("value");
    }

    @Test
    void shouldHandleNullKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(LinkedHashMapBuilder.entry(null, "value"));

        assertThat(map).hasSize(1);
        assertThat(map.get(null)).isEqualTo("value");
    }

    @Test
    void shouldOverwriteOnDuplicateKeys() {
        Map<String, String> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("key", "first"), LinkedHashMapBuilder.entry("key", "second"));

        assertThat(map).hasSize(1);
        assertThat(map.get("key")).isEqualTo("second");
    }

    @Test
    void shouldSupportMixedNumericValueTypes() {
        Map<String, Number> map = LinkedHashMapBuilder.of(
                LinkedHashMapBuilder.entry("int", 1), LinkedHashMapBuilder.entry("float", 1.5f));

        assertThat(map.get("int")).isEqualTo(1);
        assertThat(map.get("float")).isEqualTo(1.5f);
    }
}
