package io.github.pulpogato.common.cache;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

/**
 * Default implementation of {@link HttpRequestCacheKeyMapper} that generates a cache key
 * from the HTTP method, host, path, query parameters, {@code Accept} and
 * {@code Content-Type} headers, and (if present) the subject of a JWT bearer token.
 *
 * <p>The request attributes are collected into a {@link CacheKeyData} and hashed by
 * {@link CacheKeyHasher}.
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of {@link DefaultCacheKeyMapper}.
 */
public class DefaultHttpRequestCacheKeyMapper implements HttpRequestCacheKeyMapper {

    @Override
    public String apply(HttpRequest request) {
        final var url = request.getURI();
        final var headers = request.getHeaders();
        final var hostHeader = headers.getFirst("Host");
        final var host = hostHeader != null ? hostHeader : url.getHost();
        final var port = url.getPort() != -1 ? url.getPort() : null;

        final var data = CacheKeyData.builder()
                .method(request.getMethod().name())
                .host(host)
                .port(port)
                .path(url.getRawPath())
                .query(url.getRawQuery())
                .accept(headers.getFirst(HttpHeaders.ACCEPT))
                .contentType(headers.getFirst(HttpHeaders.CONTENT_TYPE))
                .jwtSubject(CacheKeyHasher.extractJwtSubject(headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .build();

        return CacheKeyHasher.hash(data);
    }
}
