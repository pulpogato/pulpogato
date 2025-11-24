package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    static class Fixture {
        private StringOrInteger value;
    }

    @Test
    void shouldSerializeFixtureWithStringInStringValue() {
        var fixture = Fixture.builder()
                .value(StringOrInteger.builder().stringValue("example").build())
                .build();

        var objectMapper = new ObjectMapper();

        var written = objectMapper.writeValueAsString(fixture);

        assertThat(written).isEqualTo("""
                {"value":"example"}""");

        Fixture readFixture = objectMapper.readValue(written, Fixture.class);
        assertThat(readFixture).isEqualTo(fixture);
    }

    @Test
    void shouldSerializeFixtureWithIntegerInStringValue() {
        var fixture = Fixture.builder()
                .value(StringOrInteger.builder().stringValue("4").build())
                .build();

        var objectMapper = new ObjectMapper();

        var written = objectMapper.writeValueAsString(fixture);

        assertThat(written).isEqualTo("""
                {"value":"4"}""");

        Fixture readFixture = objectMapper.readValue(written, Fixture.class);
        assertThat(readFixture).isEqualTo(fixture);
    }

    @Test
    void shouldSerializeFixtureWithIntegerInIntegerValue() {
        var fixture = Fixture.builder()
                .value(StringOrInteger.builder().integerValue(42L).build())
                .build();

        var objectMapper = new ObjectMapper();

        var written = objectMapper.writeValueAsString(fixture);

        assertThat(written).isEqualTo("""
                {"value":42}""");

        Fixture readFixture = objectMapper.readValue(written, Fixture.class);
        assertThat(readFixture).isEqualTo(fixture);
    }
}
