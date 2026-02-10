package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.pulpogato.common.jackson.OffsetDateTimeJackson3Deserializer;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

class OffsetDateTimeJackson3DeserializerTest {
    static class Sample {
        @JsonDeserialize(using = OffsetDateTimeJackson3Deserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime dateTime;
    }

    static Stream<Arguments> params() {
        return Stream.of(
                // String parsing formats are covered by OffsetDateTimeUtilTest
                Arguments.arguments("2023-10-05T14:48:00Z", 1696517280L),
                Arguments.arguments("1696517280", 1696517280L));
    }

    @ParameterizedTest
    @MethodSource("params")
    void deserializeShouldParseValidDateTimeStrings(String input, long expected) {
        var mapper = new ObjectMapper();
        String quotedInput = "\"" + input + "\"";
        var json = "{\"dateTime\": %s}".formatted(input.contains("-") ? quotedInput : input);
        Sample result = mapper.readValue(json, Sample.class);
        assertThat(result.dateTime.toEpochSecond()).isEqualTo(expected);
    }
}
