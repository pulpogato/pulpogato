package io.github.pulpogato.common.cache;

import java.util.function.Function;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Functional interface for mapping a ClientRequest to a cache key string.
 */
@FunctionalInterface
public interface CacheKeyMapper extends Function<ClientRequest, String> {}
