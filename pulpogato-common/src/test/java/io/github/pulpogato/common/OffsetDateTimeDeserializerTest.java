package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OffsetDateTimeDeserializerTest {
    static class Sample {
        @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime dateTime;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // language=JSON
                """
                    {"dateTime": 1696517280}""",
                // language=JSON
                """
                    {"dateTime": "2023-10-05T14:48:00.123Z"}""",
                // language=JSON
                """
                    {"dateTime": "2023-10-05T14:48:00Z"}""",
                // language=JSON
                """
                    {"dateTime": "2023-10-05T16:48:00.123+02:00"}""",
                // language=JSON
                """
                    {"dateTime": "2023-10-05T16:48:00+02:00"}"""
            })
    void deserializeShouldParseValidDateTimeStrings(String input) throws Exception {
        var mapper = new ObjectMapper();
        Sample result = mapper.readValue(input, Sample.class);
        assertThat(result.dateTime.toEpochSecond()).isEqualTo(1696517280L);
    }
}
