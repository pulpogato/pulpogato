package io.github.pulpogato.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeBuilderTest {

    @Test
    void shouldRenderMapWithNullValuesUsingLinkedHashMapBuilder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", null);

        String rendered = CodeBuilder.render(map);

        assertThat(rendered).contains("io.github.pulpogato.common.util.LinkedHashMapBuilder.of");
        assertThat(rendered)
                .contains("io.github.pulpogato.common.util.LinkedHashMapBuilder.entry(\"key1\", \"value1\")");
        assertThat(rendered).contains("io.github.pulpogato.common.util.LinkedHashMapBuilder.entry(\"key2\", null)");
    }

    @Test
    void shouldRenderStringLiteral() {
        String rendered = CodeBuilder.render("hello");

        assertThat(rendered).isEqualTo("\"hello\"");
    }

    @Test
    void shouldRenderNumberLiteral() {
        String rendered = CodeBuilder.render(42);

        assertThat(rendered).isEqualTo("42");
    }

    @Test
    void shouldRenderListValues() {
        String rendered = CodeBuilder.render(List.of("value1", 2, null));

        assertThat(rendered).contains("value1");
        assertThat(rendered).contains("2");
        assertThat(rendered).contains("null");
    }

    @Test
    void shouldRenderEmptyMap() {
        String rendered = CodeBuilder.render(new LinkedHashMap<>());

        assertThat(rendered).contains("io.github.pulpogato.common.util.LinkedHashMapBuilder.of");
    }

    @Test
    void shouldRenderEmptyList() {
        String rendered = CodeBuilder.render(List.of());

        assertThat(rendered).isNotBlank();
    }
}
