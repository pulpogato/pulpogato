package io.github.pulpogato.common.jackson;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Utility for parsing date-time strings in the various formats returned by the GitHub API.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class OffsetDateTimeUtil {
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .appendOffsetId()
            .toFormatter();

    /**
     * Parses a date-time string by using a flexible formatter.
     *
     * <p>Optimized with a single DateTimeFormatter using optional blocks instead of multiple formatters
     * in a loop with try-catch blocks to significantly improve performance and reduce exception overhead
     * in this hot path, where many dates are parsed from API responses.
     *
     * @param text the date-time string to parse, or {@code null}
     * @return the parsed {@link OffsetDateTime}, or {@code null} if the input is {@code null} or
     *     cannot be parsed
     */
    @Nullable
    public static OffsetDateTime parseStringDateTime(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
