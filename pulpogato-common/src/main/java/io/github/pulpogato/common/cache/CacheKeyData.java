package io.github.pulpogato.common.cache;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * All the request attributes that participate in a cache key, prior to hashing.
 *
 * <p>Kept as a plain data type (rather than assembled directly into a string) so new
 * discriminating fields can be added without hand-rolling string concatenation and
 * escaping/delimiter edge cases.
 */
@Getter
@Builder
public class CacheKeyData {
    private final String method;
    private final String host;
    private final @Nullable Integer port;
    private final String path;
    private final @Nullable String query;
    private final @Nullable String accept;
    private final @Nullable String contentType;
    private final @Nullable String jwtSubject;
}
