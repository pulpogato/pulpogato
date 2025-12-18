package io.github.pulpogato.common.cache;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents a cached HTTP response with its caching metadata.
 *
 * <p>This record stores the response body along with HTTP headers and caching metadata
 * (ETag, Last-Modified, Cache-Control max-age) and the timestamp when
 * the response was cached. This information is used to determine cache
 * freshness and to send conditional requests (If-None-Match, If-Modified-Since).
 *
 * @param body The cached response body as bytes
 * @param headers All response headers to preserve (Content-Type, etc.)
 * @param etag The ETag header value from the response, if present
 * @param lastModified The Last-Modified header value from the response, if present
 * @param maxAgeSeconds The max-age value from Cache-Control header in seconds, or -1 if not present
 * @param cachedAtMillis The system time in milliseconds when this response was cached
 */
public record CachedResponse(
        byte[] body,
        Map<String, List<String>> headers,
        @Nullable String etag,
        @Nullable String lastModified,
        long maxAgeSeconds,
        long cachedAtMillis) {

    /**
     * Checks if this cached response has expired based on the max-age directive.
     *
     * @param currentTimeMillis the current time in milliseconds to compare against
     * @return true if the cached response has exceeded its max-age, false if still fresh or no max-age was specified
     */
    public boolean isExpired(long currentTimeMillis) {
        if (maxAgeSeconds < 0) {
            return false;
        }
        var ageMillis = currentTimeMillis - cachedAtMillis;
        return ageMillis > (maxAgeSeconds * 1000);
    }

    /**
     * Checks if this cached response can be validated with conditional headers.
     *
     * @return true if either ETag or Last-Modified is present
     */
    public boolean canRevalidate() {
        return etag != null || lastModified != null;
    }
}
