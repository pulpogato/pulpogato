package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.jackson.OffsetDateTimeJackson2Deserializer;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OffsetDateTimeJackson2DeserializerTest {
    static class Sample {
        @JsonDeserialize(using = OffsetDateTimeJackson2Deserializer.class)
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
    void deserializeShouldParseValidDateTimeStrings(String input, long expected) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String quotedInput = "\"" + input + "\"";
        var json = "{\"dateTime\": %s}".formatted(input.contains("-") ? quotedInput : input);
        Sample result = mapper.readValue(json, Sample.class);
        assertThat(result.dateTime.toEpochSecond()).isEqualTo(expected);
    }
}
