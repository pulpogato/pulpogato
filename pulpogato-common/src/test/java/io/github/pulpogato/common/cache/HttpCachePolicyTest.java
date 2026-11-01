package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

class HttpCachePolicyTest {
    private static final URI URI_VALUE = URI.create("https://api.github.com/resource");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC);

    static Stream<Arguments> policies() {
        return Stream.of(false, true)
                .flatMap(reactive -> Stream.of(
                        Arguments.of(reactive, "", "", 2, true),
                        Arguments.of(reactive, "max-age=60", "", 1, true),
                        Arguments.of(reactive, "max-age=0", "", 2, true),
                        Arguments.of(reactive, "no-cache, max-age=60", "", 2, true),
                        Arguments.of(reactive, "No-Store, max-age=60", "", 2, false),
                        Arguments.of(reactive, "max-age=60, extension=\"x,no-store,y\"", "", 1, true),
                        Arguments.of(reactive, "", "Thu, 1 Jan 1970 00:17:40 GMT", 1, true),
                        Arguments.of(reactive, "", "Thu, 1 Jan 1970 00:16:40 GMT", 2, true),
                        Arguments.of(reactive, "no-cache", "Thu, 1 Jan 1970 00:17:40 GMT", 2, true),
                        Arguments.of(reactive, "no-store", "Thu, 1 Jan 1970 00:17:40 GMT", 2, false)));
    }

    @ParameterizedTest(name = "reactive={0}, Cache-Control={1}")
    @MethodSource("policies")
    void respectsFreshnessAndStorage(
            boolean reactive, String control, String expires, int expectedRequests, boolean stored) throws Exception {
        var cache = new ConcurrentMapCache("test");
        var requests = new AtomicInteger();
        if (reactive) {
            var filter = CachingExchangeFilterFunction.builder()
                    .cache(cache)
                    .clock(CLOCK)
                    .cacheKeyMapper(request -> "key")
                    .build();
            var request = ClientRequest.create(HttpMethod.GET, URI_VALUE).build();
            for (var i = 0; i < 2; i++) {
                var response = filter.filter(request, next -> {
                            requests.incrementAndGet();
                            return Mono.just(ClientResponse.create(HttpStatus.OK)
                                    .header("ETag", "\"v1\"")
                                    .header("Cache-Control", control)
                                    .header("Expires", expires)
                                    .body("body")
                                    .build());
                        })
                        .block();
                assertThat(response.bodyToMono(String.class).block()).isEqualTo("body");
            }
        } else {
            var interceptor = CachingClientHttpRequestInterceptor.builder()
                    .cache(cache)
                    .clock(CLOCK)
                    .cacheKeyMapper(request -> "key")
                    .build();
            for (var i = 0; i < 2; i++) {
                var request = new HttpRequest() {
                    private final HttpHeaders headers = new HttpHeaders();

                    public HttpMethod getMethod() {
                        return HttpMethod.GET;
                    }

                    public URI getURI() {
                        return URI_VALUE;
                    }

                    public HttpHeaders getHeaders() {
                        return headers;
                    }

                    public Map<String, Object> getAttributes() {
                        return new HashMap<>();
                    }
                };
                try (var response = interceptor.intercept(request, new byte[0], (next, body) -> {
                    requests.incrementAndGet();
                    return response(control, expires);
                })) {
                    assertThat(new String(response.getBody().readAllBytes())).isEqualTo("body");
                }
            }
        }
        assertThat(requests).hasValue(expectedRequests);
        assertThat(cache.get("key") != null).isEqualTo(stored);
    }

    private static ClientHttpResponse response(String control, String expires) {
        return new ClientHttpResponse() {
            public HttpStatus getStatusCode() {
                return HttpStatus.OK;
            }

            public String getStatusText() {
                return "OK";
            }

            public void close() {}

            public HttpHeaders getHeaders() {
                var headers = new HttpHeaders();
                headers.setETag("\"v1\"");
                headers.setCacheControl(control);
                headers.set("Expires", expires);
                return headers;
            }

            public ByteArrayInputStream getBody() {
                return new ByteArrayInputStream("body".getBytes());
            }
        };
    }
}
