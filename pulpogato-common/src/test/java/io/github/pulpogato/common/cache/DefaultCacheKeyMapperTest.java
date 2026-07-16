package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;

class DefaultCacheKeyMapperTest {

    private final DefaultCacheKeyMapper mapper = new DefaultCacheKeyMapper();

    private static final String SHA256_HEX_PATTERN = "[0-9a-f]{64}";

    @Test
    @DisplayName("produces a 64-character lowercase hex (SHA-256) key")
    void producesMd5HexKey() {
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                .build();

        var key = mapper.apply(request);

        assertThat(key).matches(SHA256_HEX_PATTERN);
    }

    @Test
    @DisplayName("is deterministic for equivalent requests")
    void isDeterministic() {
        var requestA = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                .build();
        var requestB = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                .build();

        assertThat(mapper.apply(requestA)).isEqualTo(mapper.apply(requestB));
    }

    @Nested
    @DisplayName("HTTP method")
    class HttpMethodTests {

        @Test
        @DisplayName("differs by HTTP method")
        void differsByMethod() {
            var getRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();
            var postRequest = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.com/data"))
                    .build();

            assertThat(mapper.apply(getRequest)).isNotEqualTo(mapper.apply(postRequest));
        }
    }

    @Nested
    @DisplayName("Host resolution")
    class HostResolutionTests {

        @Test
        @DisplayName("uses Host header when present, over the URL host")
        void usesHostHeader() {
            var withHeader = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Host", "custom-host.example.com")
                    .build();
            var withoutHeader = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();

            assertThat(mapper.apply(withHeader)).isNotEqualTo(mapper.apply(withoutHeader));
        }

        @Test
        @DisplayName("differs by port")
        void differsByPort() {
            var defaultPort = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();
            var explicitPort = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com:8443/data"))
                    .build();

            assertThat(mapper.apply(defaultPort)).isNotEqualTo(mapper.apply(explicitPort));
        }
    }

    @Nested
    @DisplayName("Path and query")
    class PathAndQueryTests {

        @Test
        @DisplayName("differs by path")
        void differsByPath() {
            var requestA = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/users/123"))
                    .build();
            var requestB = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/users/456"))
                    .build();

            assertThat(mapper.apply(requestA)).isNotEqualTo(mapper.apply(requestB));
        }

        @Test
        @DisplayName("differs by query parameters")
        void differsByQueryParameters() {
            var requestA = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://api.example.com/search?q=test&page=1"))
                    .build();
            var requestB = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://api.example.com/search?q=test&page=2"))
                    .build();

            assertThat(mapper.apply(requestA)).isNotEqualTo(mapper.apply(requestB));
        }

        @Test
        @DisplayName("no query string does not blow up and is distinct from a request with one")
        void handlesEmptyQuery() {
            var noQuery = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();
            var withQuery = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data?a=b"))
                    .build();

            assertThat(mapper.apply(noQuery)).isNotEqualTo(mapper.apply(withQuery));
        }
    }

    @Nested
    @DisplayName("Accept and Content-Type headers")
    class HeaderTests {

        @Test
        @DisplayName("differs by Accept header")
        void differsByAccept() {
            var withAccept = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
            var withoutAccept = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();

            assertThat(mapper.apply(withAccept)).isNotEqualTo(mapper.apply(withoutAccept));
        }

        @Test
        @DisplayName("differs by Content-Type header")
        void differsByContentType() {
            var withContentType = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.com/data"))
                    .header("Content-Type", "application/json")
                    .build();
            var withoutContentType = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.com/data"))
                    .build();

            assertThat(mapper.apply(withContentType)).isNotEqualTo(mapper.apply(withoutContentType));
        }
    }

    @Nested
    @DisplayName("JWT Authorization header")
    class JwtTests {

        @Test
        @DisplayName("differs by JWT subject")
        void differsByJwtSubject() {
            var requestA = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Authorization", "Bearer " + jwtWithSubject("user-a"))
                    .build();
            var requestB = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Authorization", "Bearer " + jwtWithSubject("user-b"))
                    .build();

            assertThat(mapper.apply(requestA)).isNotEqualTo(mapper.apply(requestB));
        }

        @Test
        @DisplayName("is the same key for the same JWT subject even if the token itself differs")
        void sameSubjectSameKey() {
            var requestA = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header(
                            "Authorization",
                            "Bearer "
                                    + JWT.create()
                                            .withSubject("user-a")
                                            .withIssuedAt(Instant.ofEpochSecond(1))
                                            .sign(Algorithm.HMAC256("secret-one")))
                    .build();
            var requestB = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header(
                            "Authorization",
                            "Bearer "
                                    + JWT.create()
                                            .withSubject("user-a")
                                            .withIssuedAt(Instant.ofEpochSecond(2))
                                            .sign(Algorithm.HMAC256("secret-two")))
                    .build();

            assertThat(mapper.apply(requestA)).isEqualTo(mapper.apply(requestB));
        }

        @Test
        @DisplayName("does not blow up for a non-JWT Authorization header (e.g. a personal access token)")
        void nonJwtAuthorizationHeaderIsIgnored() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Authorization", "token ghp_notActuallyAJwt")
                    .build();

            assertThat(mapper.apply(request)).matches(SHA256_HEX_PATTERN);
        }

        private String jwtWithSubject(String subject) {
            return JWT.create().withSubject(subject).sign(Algorithm.HMAC256("secret"));
        }
    }
}
