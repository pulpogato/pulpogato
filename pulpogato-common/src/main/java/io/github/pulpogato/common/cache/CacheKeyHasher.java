package io.github.pulpogato.common.cache;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/**
 * Framework-agnostic hashing shared by {@link DefaultCacheKeyMapper} and
 * {@link DefaultHttpRequestCacheKeyMapper}: serializes a {@link CacheKeyData} to JSON and hashes
 * it with SHA-256. This avoids hand-rolling a delimited string, which gets awkward once header
 * values (which may themselves contain the delimiters) join the key.
 */
class CacheKeyHasher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CacheKeyHasher() {}

    static String hash(CacheKeyData data) {
        return sha256Hex(toJson(data));
    }

    static @Nullable String extractJwtSubject(@Nullable String authorizationHeader) {
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
