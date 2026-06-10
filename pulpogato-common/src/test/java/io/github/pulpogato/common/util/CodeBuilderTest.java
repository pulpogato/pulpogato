package io.github.pulpogato.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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
}
