package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
