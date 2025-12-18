package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;

class DefaultCacheKeyMapperTest {

    private final DefaultCacheKeyMapper mapper = new DefaultCacheKeyMapper();

    @Nested
    @DisplayName("HTTP method")
    class HttpMethodTests {

        @Test
        @DisplayName("includes GET method in cache key")
        void includesGetMethod() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).startsWith("GET ");
        }

        @Test
        @DisplayName("includes POST method in cache key")
        void includesPostMethod() {
            var request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.com/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).startsWith("POST ");
        }

        @Test
        @DisplayName("includes PUT method in cache key")
        void includesPutMethod() {
            var request = ClientRequest.create(HttpMethod.PUT, URI.create("https://api.example.com/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).startsWith("PUT ");
        }
    }

    @Nested
    @DisplayName("Host resolution")
    class HostResolutionTests {

        @Test
        @DisplayName("uses Host header when present")
        void usesHostHeader() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .header("Host", "custom-host.example.com")
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET custom-host.example.com /data");
        }

        @Test
        @DisplayName("derives host from URL when Host header is absent")
        void derivesHostFromUrl() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /data");
        }

        @Test
        @DisplayName("includes port in host when non-standard port is used")
        void includesPortWhenNonStandard() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com:8443/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com:8443 /data");
        }

        @Test
        @DisplayName("omits port when using default HTTPS port")
        void omitsDefaultHttpsPort() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com:443/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com:443 /data");
        }
    }

    @Nested
    @DisplayName("Path and query")
    class PathAndQueryTests {

        @Test
        @DisplayName("includes path in cache key")
        void includesPath() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/users/123"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /users/123");
        }

        @Test
        @DisplayName("includes query parameters in cache key")
        void includesQueryParameters() {
            var request = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://api.example.com/search?q=test&page=1"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /search?q=test&page=1");
        }

        @Test
        @DisplayName("handles empty query string")
        void handlesEmptyQuery() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/data"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).doesNotContain("?");
        }

        @Test
        @DisplayName("handles root path")
        void handlesRootPath() {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /");
        }

        @Test
        @DisplayName("preserves URL-encoded characters in path")
        void preservesEncodedPath() {
            var request = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://api.example.com/path%20with%20spaces"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /path%20with%20spaces");
        }

        @Test
        @DisplayName("preserves URL-encoded characters in query")
        void preservesEncodedQuery() {
            var request = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://api.example.com/search?q=hello%20world"))
                    .build();

            var key = mapper.apply(request);

            assertThat(key).isEqualTo("GET api.example.com /search?q=hello%20world");
        }
    }
}
