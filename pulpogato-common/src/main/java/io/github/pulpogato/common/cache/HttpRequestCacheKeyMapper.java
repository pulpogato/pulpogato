package io.github.pulpogato.common.cache;

import java.util.function.Function;
import org.springframework.http.HttpRequest;

/**
 * Functional interface for mapping an {@link HttpRequest} to a cache key string.
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of {@link CacheKeyMapper}.
 */
@FunctionalInterface
public interface HttpRequestCacheKeyMapper extends Function<HttpRequest, String> {}
