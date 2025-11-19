package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringOrIntegerTest {

    @Test
    void testToCodeWithInteger() {
        StringOrInteger value = StringOrInteger.builder().integerValue(3L).build();
        assertThat(value.toCode()).isEqualTo("""
                        io.github.pulpogato.common.StringOrInteger.builder()
                            .integerValue(3L)
                            .build()""");
    }

    @Test
    void testToCodeWithString() {
        StringOrInteger value = StringOrInteger.builder().stringValue("example").build();
        assertThat(value.toCode()).isEqualTo("""
                        io.github.pulpogato.common.StringOrInteger.builder()
                            .stringValue("example")
                            .build()""");
    }
}
