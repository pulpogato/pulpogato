package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

class CacheKeyHasherTest {

    private static final String SHA256_HEX_PATTERN = "[0-9a-f]{64}";

    private static CacheKeyData data(String path) {
        return CacheKeyData.builder()
                .method("GET")
                .host("api.github.com")
                .port(443)
                .path(path)
                .build();
    }

    @Test
    void hashIsASha256HexString() {
        assertThat(CacheKeyHasher.hash(data("/repos/foo/bar"))).matches(SHA256_HEX_PATTERN);
    }

    @Test
    void hashIsDeterministicForEquivalentData() {
        assertThat(CacheKeyHasher.hash(data("/repos/foo/bar"))).isEqualTo(CacheKeyHasher.hash(data("/repos/foo/bar")));
    }

    @Test
    void hashDiffersWhenPathDiffers() {
        assertThat(CacheKeyHasher.hash(data("/repos/foo/bar")))
                .isNotEqualTo(CacheKeyHasher.hash(data("/repos/foo/baz")));
    }

    @Test
    void extractsJwtSubjectFromBearerToken() {
        var token = JWT.create().withSubject("installation-123").sign(Algorithm.HMAC256("secret"));

        assertThat(CacheKeyHasher.extractJwtSubject("Bearer " + token)).isEqualTo("installation-123");
    }

    @Test
    void extractsJwtSubjectWithoutBearerPrefix() {
        var token = JWT.create().withSubject("installation-123").sign(Algorithm.HMAC256("secret"));

        assertThat(CacheKeyHasher.extractJwtSubject(token)).isEqualTo("installation-123");
    }

    @Test
    void sameSubjectAcrossDifferentTokensYieldsSameSubject() {
        var tokenA = JWT.create().withSubject("installation-123").sign(Algorithm.HMAC256("secret-a"));
        var tokenB = JWT.create()
                .withSubject("installation-123")
                .withIssuedAt(java.time.Instant.now().minusSeconds(3600))
                .sign(Algorithm.HMAC256("secret-b"));

        assertThat(CacheKeyHasher.extractJwtSubject("Bearer " + tokenA))
                .isEqualTo(CacheKeyHasher.extractJwtSubject("Bearer " + tokenB));
    }

    @Test
    void returnsNullForNonJwtAuthorizationHeader() {
        assertThat(CacheKeyHasher.extractJwtSubject("token ghp_abc123")).isNull();
    }

    @Test
    void returnsNullForNullAuthorizationHeader() {
        assertThat(CacheKeyHasher.extractJwtSubject(null)).isNull();
    }
}
