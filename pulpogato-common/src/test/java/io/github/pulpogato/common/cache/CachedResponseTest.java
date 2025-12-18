package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CachedResponseTest {

    private static final byte[] BODY = "test".getBytes();
    private static final Map<String, java.util.List<String>> HEADERS = Map.of();

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("returns false when maxAgeSeconds is negative")
        void returnsFalseWhenMaxAgeIsNegative() {
            var response = new CachedResponse(BODY, HEADERS, null, null, -1, 0);

            assertThat(response.isExpired(1_000_000)).isFalse();
        }

        @Test
        @DisplayName("returns false when within max-age window")
        void returnsFalseWhenWithinMaxAge() {
            var cachedAt = 1000L;
            var maxAge = 60L;
            var response = new CachedResponse(BODY, HEADERS, null, null, maxAge, cachedAt);

            assertThat(response.isExpired(cachedAt + 30_000)).isFalse();
        }

        @Test
        @DisplayName("returns false when exactly at max-age boundary")
        void returnsFalseWhenAtExactBoundary() {
            var cachedAt = 1000L;
            var maxAge = 60L;
            var response = new CachedResponse(BODY, HEADERS, null, null, maxAge, cachedAt);

            assertThat(response.isExpired(cachedAt + 60_000)).isFalse();
        }

        @Test
        @DisplayName("returns true when past max-age")
        void returnsTrueWhenPastMaxAge() {
            var cachedAt = 1000L;
            var maxAge = 60L;
            var response = new CachedResponse(BODY, HEADERS, null, null, maxAge, cachedAt);

            assertThat(response.isExpired(cachedAt + 60_001)).isTrue();
        }

        @Test
        @DisplayName("returns false when maxAgeSeconds is zero and time has not passed")
        void returnsFalseWhenMaxAgeZeroAndNoTimePassed() {
            var cachedAt = 1000L;
            var response = new CachedResponse(BODY, HEADERS, null, null, 0, cachedAt);

            assertThat(response.isExpired(cachedAt)).isFalse();
        }

        @Test
        @DisplayName("returns true when maxAgeSeconds is zero and any time has passed")
        void returnsTrueWhenMaxAgeZeroAndTimePassed() {
            var cachedAt = 1000L;
            var response = new CachedResponse(BODY, HEADERS, null, null, 0, cachedAt);

            assertThat(response.isExpired(cachedAt + 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("canRevalidate")
    class CanRevalidate {

        @Test
        @DisplayName("returns true when etag is present")
        void returnsTrueWhenEtagPresent() {
            var response = new CachedResponse(BODY, HEADERS, "\"abc123\"", null, -1, 0);

            assertThat(response.canRevalidate()).isTrue();
        }

        @Test
        @DisplayName("returns true when lastModified is present")
        void returnsTrueWhenLastModifiedPresent() {
            var response = new CachedResponse(BODY, HEADERS, null, "Wed, 21 Oct 2015 07:28:00 GMT", -1, 0);

            assertThat(response.canRevalidate()).isTrue();
        }

        @Test
        @DisplayName("returns true when both etag and lastModified are present")
        void returnsTrueWhenBothPresent() {
            var response = new CachedResponse(BODY, HEADERS, "\"abc123\"", "Wed, 21 Oct 2015 07:28:00 GMT", -1, 0);

            assertThat(response.canRevalidate()).isTrue();
        }

        @Test
        @DisplayName("returns false when neither etag nor lastModified is present")
        void returnsFalseWhenNeitherPresent() {
            var response = new CachedResponse(BODY, HEADERS, null, null, -1, 0);

            assertThat(response.canRevalidate()).isFalse();
        }
    }
}
