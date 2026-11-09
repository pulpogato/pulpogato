package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

class PartialResponseCacheTest {
    private final ConcurrentMapCache cache = new ConcurrentMapCache("partial") {
        @Override
        public void evict(Object key) {
            // Transaction-aware providers may defer this operation until commit.
        }
    };
    private final AtomicInteger exchanges = new AtomicInteger();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ObservationRegistry observations = ObservationRegistry.create();
    private boolean revalidate;
    private boolean defaultMapper;
    private String cacheControl = "max-age=60";
    private boolean validators = true;
    private long contentLength = -1;

    PartialResponseCacheTest() {
        observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
    }

    static Stream<Arguments> successfulStatuses() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of(201, 204, 206).map(status -> Arguments.of(reactive, status)));
    }

    @ParameterizedTest
    @MethodSource("successfulStatuses")
    void doesNotStoreNon200Responses(boolean reactive, int status) throws Exception {
        assertThat(exchange(reactive, false, status, "partial")).isEqualTo(new Result(status, "partial"));
        assertThat(exchange(reactive, false, status, "partial")).isEqualTo(new Result(status, "partial"));
        assertThat(exchanges).hasValue(2);
        assertThat(cache.get("key")).isNull();
        assertThat(meters.get("pulpogato.cache.put")
                        .tag("cache.status", "INVALIDATED")
                        .timer()
                        .count())
                .isEqualTo(2);
    }

    static Stream<Arguments> headerSkips() {
        return successfulStatuses()
                .flatMap(arguments -> Stream.of("NO_CACHE_HEADERS", "CONTENT_LENGTH")
                        .map(reason ->
                                Arguments.of(arguments.get()[0], arguments.get()[1], reason)));
    }

    @ParameterizedTest
    @MethodSource("headerSkips")
    void non200SuccessPreservesHeaderSkipObservations(boolean reactive, int status, String reason) throws Exception {
        validators = !reason.equals("NO_CACHE_HEADERS");
        cacheControl = validators ? "max-age=60" : "";
        contentLength = validators ? CachingExchangeFilterFunction.DEFAULT_MAX_CACHEABLE_SIZE + 1 : -1;
        exchange(reactive, false, status, "partial");
        assertThat(meters.get("pulpogato.cache.put")
                        .tags("cache.status", "SKIP", "cache.skip.reason", reason)
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(meters.get("pulpogato.cache.put")
                        .tag("cache.status", "INVALIDATED")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(cache.getNativeCache()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rangeRequestsBypassExistingFullResponsesWithoutReplacingThem(boolean reactive) throws Exception {
        assertThat(exchange(reactive, false, 200, "complete")).isEqualTo(new Result(200, "complete"));
        assertThat(exchange(reactive, true, 206, "partial")).isEqualTo(new Result(206, "partial"));
        assertThat(exchange(reactive, false, 200, "unexpected")).isEqualTo(new Result(200, "complete"));
        assertThat(exchanges).hasValue(2);
    }

    static Stream<Arguments> rangeReplacements() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of(200, 201, 204).map(status -> Arguments.of(reactive, status)));
    }

    @ParameterizedTest
    @MethodSource("rangeReplacements")
    void fullSuccessOnARangeRequestInvalidatesThePreviousRepresentation(boolean reactive, int status) throws Exception {
        exchange(reactive, false, 200, "old");
        exchange(reactive, true, status, "replacement");
        assertThat(cache.get("key")).isNull();
        assertThat(exchange(reactive, false, 200, "current")).isEqualTo(new Result(200, "current"));
        assertThat(exchanges).hasValue(3);
    }

    static Stream<Arguments> replacements() {
        return successfulStatuses()
                .flatMap(arguments -> Stream.of("max-age=60", "no-store")
                        .map(control ->
                                Arguments.of(arguments.get()[0], arguments.get()[1], control)));
    }

    @ParameterizedTest
    @MethodSource("replacements")
    void uncacheableSuccessEvictsThePreviousRepresentation(boolean reactive, int status, String control)
            throws Exception {
        exchange(reactive, false, 200, "complete");
        revalidate = true;
        cacheControl = control;
        exchange(reactive, false, status, "");
        assertThat(cache.get("key")).isNull();
        if (control.equals("no-store")) {
            assertNoStoreSkip();
        }
        revalidate = false;
        cacheControl = "max-age=60";
        assertThat(exchange(reactive, false, 200, "replacement")).isEqualTo(new Result(200, "replacement"));
        assertThat(exchanges).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noStoreRangeResponseEvictsWithoutReadingOrReplacingTheFullEntry(boolean reactive) throws Exception {
        defaultMapper = true;
        exchange(reactive, false, 200, "complete");
        cacheControl = "no-store";
        assertThat(exchange(reactive, true, 206, "partial")).isEqualTo(new Result(206, "partial"));
        assertThat(cache.getNativeCache()).isEmpty();
        assertThat(meters.get("pulpogato.cache.get").timer().count()).isEqualTo(1);
        assertNoStoreSkip();
        cacheControl = "max-age=60";
        assertThat(exchange(reactive, false, 200, "replacement")).isEqualTo(new Result(200, "replacement"));
        assertThat(exchanges).hasValue(3);
    }

    private void assertNoStoreSkip() {
        assertThat(meters.get("pulpogato.cache.put")
                        .tags("cache.status", "SKIP", "cache.skip.reason", "NO_STORE")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void defaultMappersIgnoreEntriesFromTheLegacyNamespace(boolean reactive) throws Exception {
        var mapper = new ObjectMapper();
        var data = CacheKeyData.builder()
                .method("GET")
                .host("api.github.com")
                .path("/resource")
                .build();
        var legacyData = (ObjectNode) mapper.valueToTree(data);
        legacyData.remove("representationVersion");
        var legacyKey = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(legacyData)));
        cache.put(
                legacyKey,
                new CachedResponse(
                        "partial".getBytes(StandardCharsets.UTF_8),
                        Map.of("Cache-Control", List.of("max-age=60")),
                        "\"v1\"",
                        null,
                        60,
                        System.currentTimeMillis()));
        defaultMapper = true;
        assertThat(exchange(reactive, false, 200, "complete")).isEqualTo(new Result(200, "complete"));
        assertThat(exchanges).hasValue(1);
    }

    private Result exchange(boolean reactive, boolean range, int status, String body) throws Exception {
        var request = new Request();
        if (range) {
            request.headers.set(HttpHeaders.RANGE, "bytes=0-6");
        }
        if (reactive) {
            var filter = CachingExchangeFilterFunction.builder()
                    .cache(cache)
                    .cacheKeyMapper(defaultMapper ? new DefaultCacheKeyMapper() : value -> "key")
                    .alwaysRevalidate(revalidate)
                    .observationRegistry(observations)
                    .build();
            var response = filter.filter(
                            ClientRequest.create(HttpMethod.GET, request.getURI())
                                    .headers(headers -> headers.addAll(request.headers))
                                    .build(),
                            next -> {
                                exchanges.incrementAndGet();
                                return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                                        .headers(headers -> headers.addAll(responseHeaders()))
                                        .body(body)
                                        .build());
                            })
                    .block();
            return new Result(
                    response.statusCode().value(),
                    response.bodyToMono(String.class).block());
        }
        var filter = CachingClientHttpRequestInterceptor.builder()
                .cache(cache)
                .cacheKeyMapper(defaultMapper ? new DefaultHttpRequestCacheKeyMapper() : value -> "key")
                .alwaysRevalidate(revalidate)
                .observationRegistry(observations)
                .build();
        try (var response = filter.intercept(request, new byte[0], (value, bytes) -> {
            exchanges.incrementAndGet();
            return new Response(status, body, responseHeaders());
        })) {
            return new Result(
                    response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private HttpHeaders responseHeaders() {
        var headers = new HttpHeaders();
        if (validators) headers.setETag("\"v1\"");
        headers.setCacheControl(cacheControl);
        if (contentLength >= 0) headers.setContentLength(contentLength);
        return headers;
    }

    private record Result(int status, String body) {}

    @Getter
    private static final class Request implements HttpRequest {
        private final HttpMethod method = HttpMethod.GET;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = Map.of();

        @Override
        public URI getURI() {
            return URI.create("https://api.github.com/resource");
        }
    }

    @Getter
    private static final class Response implements ClientHttpResponse {
        private final HttpStatusCode statusCode;
        private final String statusText = "";
        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body;

        Response(int status, String content, HttpHeaders responseHeaders) {
            statusCode = HttpStatusCode.valueOf(status);
            body = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            headers.addAll(responseHeaders);
        }

        @Override
        public void close() {
            // ByteArrayInputStream owns no external resources.
        }
    }
}
