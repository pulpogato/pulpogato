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

        // Optimized with StringBuilder to reduce object allocations in hot path
        final var sb = new StringBuilder();
        sb.append(request.method().name()).append(' ').append(host);

        if (url.getPort() != -1) {
            sb.append(':').append(url.getPort());
        }

        sb.append(' ').append(url.getRawPath());

        final var query = url.getRawQuery();
        if (query != null) {
            sb.append('?').append(query);
        }

        return sb.toString();
    }
}
