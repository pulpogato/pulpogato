package io.github.pulpogato.common.cache;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Default implementation of CacheKeyMapper that generates a cache key
 * from the HTTP method, host, path, query parameters, {@code Accept} and
 * {@code Content-Type} headers, and (if present) the subject of a JWT bearer token.
 *
 * <p>The request attributes are collected into a {@link CacheKeyData}, serialized to JSON,
 * and hashed with SHA-256. This avoids hand-rolling a delimited string, which gets awkward once
 * header values (which may themselves contain the delimiters) join the key.
 */
public class DefaultCacheKeyMapper implements CacheKeyMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String apply(ClientRequest request) {
        final var url = request.url();
        final var headers = request.headers();
        final var hostHeader = headers.getFirst("Host");
        final var host = hostHeader != null ? hostHeader : url.getHost();
        final var port = url.getPort() != -1 ? url.getPort() : null;

        final var data = CacheKeyData.builder()
                .method(request.method().name())
                .host(host)
                .port(port)
                .path(url.getRawPath())
                .query(url.getRawQuery())
                .accept(headers.getFirst(HttpHeaders.ACCEPT))
                .contentType(headers.getFirst(HttpHeaders.CONTENT_TYPE))
                .jwtSubject(extractJwtSubject(headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .build();

        return sha256Hex(toJson(data));
    }

    private static @Nullable String extractJwtSubject(@Nullable String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        final var token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length())
                : authorizationHeader;
        try {
            return JWT.decode(token).getSubject();
        } catch (JWTDecodeException e) {
            return null;
        }
    }

    private static byte[] toJson(CacheKeyData data) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize cache key data", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
