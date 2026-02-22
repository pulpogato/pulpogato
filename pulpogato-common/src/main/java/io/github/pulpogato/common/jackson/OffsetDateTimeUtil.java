package io.github.pulpogato.common.jackson;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Utility for parsing date-time strings in the various formats returned by the GitHub API.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class OffsetDateTimeUtil {
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX"),
            DateTimeFormatter.ISO_INSTANT);

    /**
     * Parses a date-time string by trying each supported format in order.
     *
     * @param text the date-time string to parse, or {@code null}
     * @return the parsed {@link OffsetDateTime}, or {@code null} if the input is {@code null} or
     *     cannot be parsed by any supported format
     */
    @Nullable
    public static OffsetDateTime parseStringDateTime(@Nullable String text) {
        if (text == null) {
            return null;
        }
        return OffsetDateTimeUtil.FORMATTERS.stream()
                .map(formatter -> {
                    try {
                        return OffsetDateTime.parse(text, formatter);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
