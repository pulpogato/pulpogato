package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CacheResponseInvalidationTest {
    private static final URI URI_VALUE = URI.create("https://api.github.com/resource");

    static Stream<Arguments> bypassMethods() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of("POST", "PUT", "HEAD").map(method -> Arguments.of(reactive, method)));
    }

    @ParameterizedTest
    @MethodSource("bypassMethods")
    void rangeOnOtherMethodsNeverCallsTheCacheMapper(boolean reactive, String method) throws Exception {
        exchange(
                reactive,
                method,
                true,
                new ConcurrentMapCache("bypass"),
                ObservationRegistry.NOOP,
                headers -> {
                    throw new AssertionError("unexpected cache key lookup");
                },
                204,
                new AtomicInteger(),
                null);
    }

    static Stream<Arguments> cacheMethods() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of("GET", "QUERY").map(method -> Arguments.of(reactive, method)));
    }

    @ParameterizedTest
    @MethodSource("cacheMethods")
    void rangeEvictionUsesTheFullRequestKeyBeforeDownstreamHeaderMutation(boolean reactive, String method)
            throws Exception {
        var cache = new ConcurrentMapCache("range");
        cache.put("original:full", "previous representation");
        exchange(
                reactive,
                method,
                true,
                cache,
                ObservationRegistry.NOOP,
                headers -> headers.getFirst(HttpHeaders.AUTHORIZATION) + ":"
                        + (headers.containsHeader(HttpHeaders.RANGE) || headers.containsHeader(HttpHeaders.IF_RANGE)
                                ? "partial"
                                : "full"),
                204,
                new AtomicInteger(),
                null);
        assertThat(cache.get("original:full")).isNull();
    }

    static Stream<Arguments> cleanupCases() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of(true, false)
                        .flatMap(range -> Stream.of(true, false)
                                .flatMap(failEviction -> Stream.of(true, false)
                                        .flatMap(failClose -> Stream.of(200, 204)
                                                .map(status -> Arguments.of(
                                                        reactive, range, failEviction, failClose, status))))));
    }

    static Stream<Arguments> deferredWrites() {
        return cacheMethods()
                .flatMap(arguments -> Stream.of("max-age=60", "no-store")
                        .map(control ->
                                Arguments.of(arguments.get()[0], arguments.get()[1], control)));
    }

    @ParameterizedTest
    @MethodSource("deferredWrites")
    void invalidationAlsoRemovesWritesQueuedBeforeTransactionCommit(boolean reactive, String method, String control)
            throws Exception {
        var deferred = new ArrayList<Runnable>();
        var cache = new ConcurrentMapCache("transaction") {
            @Override
            public void put(Object key, Object value) {
                deferred.add(() -> super.put(key, value));
            }

            @Override
            public void evict(Object key) {
                deferred.add(() -> super.evict(key));
            }
        };
        cache.put("key", "queued representation");
        exchange(
                reactive,
                method,
                false,
                cache,
                ObservationRegistry.NOOP,
                headers -> "key",
                204,
                new AtomicInteger(),
                null,
                control);
        assertThat(cache.get("key")).isNull();
        deferred.forEach(Runnable::run);
        assertThat(cache.get("key")).isNull();
    }

    @ParameterizedTest
    @MethodSource("cleanupCases")
    void releasesResponsesAndPreservesTheFailure(
            boolean reactive, boolean range, boolean failEviction, boolean failClose, int status) throws Exception {
        var failure = new IllegalStateException("cache operation failed");
        var cleanupFailure = failClose ? new IllegalArgumentException("response cleanup failed") : null;
        var closed = new AtomicInteger();
        var cache = new ConcurrentMapCache("failures") {
            @Override
            public boolean evictIfPresent(Object key) {
                if (failEviction) throw failure;
                return super.evictIfPresent(key);
            }
        };
        var observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                var status = context.getLowCardinalityKeyValue("cache.status");
                if (!failEviction && status != null && status.getValue().equals("SKIP")) throw failure;
            }
        });
        var assertion = assertThatThrownBy(() -> exchange(
                        reactive, "GET", range, cache, observations, headers -> "key", status, closed, cleanupFailure))
                .isSameAs(failure);
        assertThat(closed).hasValue(1);
        if (cleanupFailure != null) assertion.hasSuppressedException(cleanupFailure);
    }

    private void exchange(
            boolean reactive,
            String method,
            boolean range,
            ConcurrentMapCache cache,
            ObservationRegistry observations,
            Function<HttpHeaders, String> mapper,
            int status,
            AtomicInteger closed,
            RuntimeException cleanupFailure)
            throws Exception {
        exchange(reactive, method, range, cache, observations, mapper, status, closed, cleanupFailure, "no-store");
    }

    private void exchange(
            boolean reactive,
            String method,
            boolean range,
            ConcurrentMapCache cache,
            ObservationRegistry observations,
            Function<HttpHeaders, String> mapper,
            int status,
            AtomicInteger closed,
            RuntimeException cleanupFailure,
            String control)
            throws Exception {
        var request = new Request(HttpMethod.valueOf(method));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "original");
        if (range) {
            request.getHeaders().set(HttpHeaders.RANGE, "bytes=0-6");
            request.getHeaders().set(HttpHeaders.IF_RANGE, "\"v1\"");
        }
        if (reactive) {
            var filter = CachingExchangeFilterFunction.builder()
                    .cache(cache)
                    .cacheKeyMapper(value -> mapper.apply(value.headers()))
                    .observationRegistry(observations)
                    .build();
            var body = cleanupFailure == null ? Flux.<DataBuffer>empty() : Flux.<DataBuffer>error(cleanupFailure);
            var response = filter.filter(
                            ClientRequest.create(request.getMethod(), URI_VALUE)
                                    .headers(headers -> headers.addAll(request.getHeaders()))
                                    .build(),
                            next -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                                    .header(HttpHeaders.CACHE_CONTROL, control)
                                    .body(body.doFinally(signal -> closed.incrementAndGet()))
                                    .build()))
                    .block();
            response.releaseBody().block();
        } else {
            var filter = CachingClientHttpRequestInterceptor.builder()
                    .cache(cache)
                    .cacheKeyMapper(value -> mapper.apply(value.getHeaders()))
                    .observationRegistry(observations)
                    .build();
            try (var ignored = filter.intercept(request, new byte[0], (value, bytes) -> {
                value.getHeaders().set(HttpHeaders.AUTHORIZATION, "changed downstream");
                return new Response(status, closed, cleanupFailure, control);
            })) {
                // The caller closes responses that were successfully returned by the interceptor.
            }
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static final class Request implements HttpRequest {
        private final HttpMethod method;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = Map.of();

        @Override
        public URI getURI() {
            return URI_VALUE;
        }
    }

    @Getter
    private static final class Response implements ClientHttpResponse {
        private final HttpStatusCode statusCode;
        private final String statusText = "";
        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body = new ByteArrayInputStream(new byte[0]);
        private final AtomicInteger closed;
        private final RuntimeException cleanupFailure;

        Response(int status, AtomicInteger closed, RuntimeException cleanupFailure, String control) {
            this.statusCode = HttpStatusCode.valueOf(status);
            this.closed = closed;
            this.cleanupFailure = cleanupFailure;
            headers.setCacheControl(control);
        }

        @Override
        public void close() {
            closed.incrementAndGet();
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }
}
