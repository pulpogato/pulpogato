package io.github.pulpogato.common.cache;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Framework-agnostic hashing shared by {@link DefaultCacheKeyMapper} and
 * {@link DefaultHttpRequestCacheKeyMapper}: serializes a {@link CacheKeyData} to JSON and hashes
 * it with SHA-256. This avoids hand-rolling a delimited string, which gets awkward once header
 * values (which may themselves contain the delimiters) join the key.
 */
class CacheKeyHasher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final byte[] DEFAULT_CREDENTIAL_KEY = newCredentialKey();

    private CacheKeyHasher() {}

    static String hash(CacheKeyData data) {
        return sha256Hex(toJson(data));
    }

    static byte[] defaultCredentialKey() {
        return DEFAULT_CREDENTIAL_KEY.clone();
    }

    private static byte[] newCredentialKey() {
        var key = new byte[32];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    static @Nullable String credentialFingerprint(@Nullable List<String> authorizationHeaders, byte[] key) {
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return null;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(OBJECT_MAPPER.writeValueAsBytes(authorizationHeaders)));
        } catch (GeneralSecurityException | JsonProcessingException failure) {
            throw new IllegalStateException("Failed to fingerprint authorization credentials", failure);
        }
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
