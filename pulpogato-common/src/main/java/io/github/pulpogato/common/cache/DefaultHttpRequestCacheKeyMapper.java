package io.github.pulpogato.common.cache;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

/**
 * Default implementation of {@link HttpRequestCacheKeyMapper} that generates a cache key
 * from the HTTP method, host, path, query parameters, {@code Accept} and
 * {@code Content-Type} headers, and a fingerprint of the Authorization header.
 * Tokens with different permissions must not share cached responses, even when their JWT subjects match.
 *
 * <p>The request attributes are collected into a {@link CacheKeyData} and hashed by
 * {@link CacheKeyHasher}.
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of {@link DefaultCacheKeyMapper}.
 */
public class DefaultHttpRequestCacheKeyMapper implements HttpRequestCacheKeyMapper {

    private final byte[] credentialKey;

    /** Creates a mapper using the process-local credential fingerprint key. */
    public DefaultHttpRequestCacheKeyMapper() {
        this(CacheKeyHasher.defaultCredentialKey());
    }

    /**
     * Creates a mapper with a shared credential fingerprint key for cross-process cache reuse.
     * @param credentialKey a cryptographically random secret of at least 32 bytes, shared only by clients sharing a cache
     */
    public DefaultHttpRequestCacheKeyMapper(byte[] credentialKey) {
        if (credentialKey.length < 32) {
            throw new IllegalArgumentException("Credential fingerprint keys must contain at least 32 bytes");
        }
        this.credentialKey = credentialKey.clone();
    }

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
                .credentialFingerprint(
                        CacheKeyHasher.credentialFingerprint(headers.get(HttpHeaders.AUTHORIZATION), credentialKey))
                .build();

        return CacheKeyHasher.hash(data);
    }
}
