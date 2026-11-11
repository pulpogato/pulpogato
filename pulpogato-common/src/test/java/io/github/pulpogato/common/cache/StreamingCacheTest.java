package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class StreamingCacheTest {
    private final ConcurrentMapCache cache = new ConcurrentMapCache("streaming");
    private final DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
    private final CachingExchangeFilterFunction filter = CachingExchangeFilterFunction.builder()
            .cache(cache)
            .cacheKeyMapper(request -> "key")
            .maxCacheableSize(64)
            .build();

    @Test
    void emitsFirstBufferWithoutWaitingForResponseCompletionAndDoesNotCacheCancellation() {
        var stream = exchange(Flux.concat(Mono.fromSupplier(() -> buffers.wrap(new byte[8])), Mono.never()));
        StepVerifier.create(stream.take(1).doOnNext(DataBufferUtils::release))
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void streamsLargeUnknownLengthResponsesWithoutStoringThem() {
        var stream = exchange(Flux.range(0, 10000).map(index -> buffers.wrap(new byte[1024])));
        assertThat(stream.doOnNext(DataBufferUtils::release).count().block()).isEqualTo(10000);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void doesNotStorePartialResponseAfterBodyError() {
        var failure = new IllegalStateException("connection lost");
        StepVerifier.create(exchange(
                                Flux.concat(Mono.fromSupplier(() -> buffers.wrap(new byte[8])), Mono.error(failure)))
                        .doOnNext(DataBufferUtils::release))
                .expectNextCount(1)
                .expectErrorMatches(error -> error == failure)
                .verify(Duration.ofSeconds(5));
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void cleanupAfterCancellationDoesNotCacheTheRemainingBody() {
        // given
        var subscriptions = new AtomicInteger();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/resource"))
                .build();
        var response = filter.filter(
                        request,
                        next -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("ETag", "\"v1\"")
                                .body(Flux.defer(() -> subscriptions.getAndIncrement() == 0
                                        ? Flux.concat(Mono.fromSupplier(() -> buffers.wrap(new byte[8])), Mono.never())
                                        : Flux.just(buffers.wrap(new byte[4]))))
                                .build()))
                .block(Duration.ofSeconds(5));

        // when
        response.body(BodyExtractors.toDataBuffers())
                .take(1)
                .doOnNext(DataBufferUtils::release)
                .blockLast(Duration.ofSeconds(5));
        response.releaseBody().block(Duration.ofSeconds(5));

        // then
        assertThat(subscriptions.get()).isEqualTo(2);
        assertThat(cache.get("key")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseCleanupPreservesCachedBody(boolean alwaysRevalidate) {
        // given
        var requests = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/files", (request, response) -> {
                    requests.incrementAndGet();
                    if (request.requestHeaders().contains("If-None-Match")) {
                        return response.status(304).send();
                    }
                    return response.header("Content-Type", "application/json")
                            .header("ETag", "\"v1\"")
                            .header("Cache-Control", "max-age=3600")
                            .sendString(Mono.just("[1,2,3]"));
                }))
                .bindNow();
        try {
            var client = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .filter(CachingExchangeFilterFunction.builder()
                            .cache(cache)
                            .cacheKeyMapper(request -> "key")
                            .alwaysRevalidate(alwaysRevalidate)
                            .build())
                    .build();

            // when
            var first = client.get()
                    .uri("/files")
                    .exchangeToMono(response -> response.bodyToMono(int[].class))
                    .block(Duration.ofSeconds(5));
            var second = client.get()
                    .uri("/files")
                    .exchangeToMono(response -> response.bodyToMono(int[].class))
                    .block(Duration.ofSeconds(5));

            // then
            assertThat(first).containsExactly(1, 2, 3);
            assertThat(second).containsExactly(1, 2, 3);
            assertThat(requests.get()).isEqualTo(alwaysRevalidate ? 2 : 1);
        } finally {
            server.disposeNow();
        }
    }

    private Flux<DataBuffer> exchange(Flux<DataBuffer> body) {
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/resource"))
                .build();
        return filter.filter(
                        request,
                        next -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("ETag", "\"v1\"")
                                .body(body)
                                .build()))
                .flatMapMany(response -> response.body(BodyExtractors.toDataBuffers()));
    }
}
