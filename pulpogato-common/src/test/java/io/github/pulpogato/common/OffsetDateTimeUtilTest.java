package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.common.jackson.OffsetDateTimeUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OffsetDateTimeUtilTest {

    static Stream<Arguments> validDateTimes() {
        return Stream.of(
                Arguments.of("nanosecond precision", "2023-10-05T14:48:00.123456789Z", 1696517280L),
                Arguments.of("millisecond precision", "2023-10-05T14:48:00.123Z", 1696517280L),
                Arguments.of("no fractional seconds", "2023-10-05T14:48:00Z", 1696517280L),
                Arguments.of("positive offset", "2023-10-05T16:48:00+02:00", 1696517280L),
                Arguments.of("no seconds", "2023-10-31T17:50Z", 1698774600L),
                Arguments.of("ISO instant", "2023-10-05T14:48:00Z", 1696517280L));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("validDateTimes")
    void shouldParseValidDateTimeStrings(
            @SuppressWarnings("unused") String description, String input, long expectedEpochSecond) {
        var result = OffsetDateTimeUtil.parseStringDateTime(input);
        assertThat(result).isNotNull();
        assertThat(result.toEpochSecond()).isEqualTo(expectedEpochSecond);
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(OffsetDateTimeUtil.parseStringDateTime(null)).isNull();
    }

    @Test
    void shouldReturnNullForUnparseableInput() {
        assertThat(OffsetDateTimeUtil.parseStringDateTime("not-a-date")).isNull();
    }
}
