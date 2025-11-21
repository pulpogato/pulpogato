package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OffsetDateTimeDeserializerTest {
    static class Sample {
        @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime dateTime;
    }

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.arguments("1696517280", 1696517280L),
                Arguments.arguments("2023-10-05T14:48:00.123Z", 1696517280L),
                Arguments.arguments("2023-10-05T14:48:00Z", 1696517280L),
                Arguments.arguments("2023-10-05T16:48:00.123+02:00", 1696517280L),
                Arguments.arguments("2023-10-05T16:48:00+02:00", 1696517280L),
                Arguments.arguments("2025-06-03T05:53:24.752653000Z", 1748930004L));
    }

    @ParameterizedTest
    @MethodSource("params")
    void deserializeShouldParseValidDateTimeStrings(String input, long expected) throws Exception {
        var mapper = new ObjectMapper();
        String quotedInput = "\"" + input + "\"";
        var json = "{\"dateTime\": %s}".formatted(input.contains("-") ? quotedInput : input);
        Sample result = mapper.readValue(json, Sample.class);
        assertThat(result.dateTime.toEpochSecond()).isEqualTo(expected);
    }
}
