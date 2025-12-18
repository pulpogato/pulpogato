package io.github.pulpogato.common.cache;

import java.text.MessageFormat;
import java.util.Optional;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Default implementation of CacheKeyMapper that generates a cache key
 * based on the HTTP method, host, path, and query parameters of the request.
 */
public class DefaultCacheKeyMapper implements CacheKeyMapper {
    @Override
    public String apply(ClientRequest request) {
        final var url = request.url();
        final var host = Optional.ofNullable(request.headers().getFirst("Host"))
                .orElse(url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : ""));
        final var pathAndQuery = MessageFormat.format(
                "{0}{1}",
                url.getRawPath(), url.getRawQuery() != null ? MessageFormat.format("?{0}", url.getRawQuery()) : "");
        return MessageFormat.format("{0} {1} {2}", request.method().name(), host, pathAndQuery);
    }
}
