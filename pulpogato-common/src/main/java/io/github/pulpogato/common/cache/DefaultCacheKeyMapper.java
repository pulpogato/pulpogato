package io.github.pulpogato.common.cache;

import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Default implementation of CacheKeyMapper that generates a cache key
 * based on the HTTP method, host, path, and query parameters of the request.
 */
public class DefaultCacheKeyMapper implements CacheKeyMapper {
    @Override
    public String apply(ClientRequest request) {
        final var url = request.url();
        final var hostHeader = request.headers().getFirst("Host");
        final var host = hostHeader != null ? hostHeader : url.getHost();
        final var port = url.getPort() != -1 ? ":" + url.getPort() : "";
        final var query = url.getRawQuery() != null ? "?" + url.getRawQuery() : "";
        final var pathAndQuery = url.getRawPath() + query;
        return request.method().name() + " " + host + port + " " + pathAndQuery;
    }
}
