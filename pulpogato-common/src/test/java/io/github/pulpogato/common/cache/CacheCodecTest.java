package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CacheCodecTest {
    @Test
    void preservesCustomDecodersForMissHitAndRevalidation() {
        var decodes = new AtomicInteger();
        var strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.customCodecs()
                        .register(new AbstractDataBufferDecoder<Payload>(MediaType.APPLICATION_JSON) {
                            @Override
                            public boolean canDecode(ResolvableType type, MimeType mimeType) {
                                return type.toClass() == Payload.class;
                            }

                            @Override
                            public Payload decode(
                                    DataBuffer buffer,
                                    ResolvableType type,
                                    MimeType mimeType,
                                    Map<String, Object> hints) {
                                DataBufferUtils.release(buffer);
                                decodes.incrementAndGet();
                                return new Payload("custom");
                            }
                        }))
                .build();
        var cache = new ConcurrentMapCache("codecs");
        var filter = CachingExchangeFilterFunction.builder()
                .cache(cache)
                .exchangeStrategies(strategies)
                .build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/resource"))
                .build();
        var networkRequest = mock(HttpRequest.class);
        when(networkRequest.getMethod()).thenReturn(HttpMethod.GET);
        when(networkRequest.getURI()).thenReturn(request.url());
        var first = filter.filter(
                        request,
                        next -> Mono.just(ClientResponse.create(HttpStatus.OK, strategies)
                                .header("Content-Type", "application/json")
                                .header("ETag", "\"v1\"")
                                .header("Cache-Control", "max-age=60")
                                .cookie("example", "cookie-value")
                                .request(networkRequest)
                                .body("{}")
                                .build()))
                .block();
        assertThat(first.bodyToMono(Payload.class).block()).isEqualTo(new Payload("custom"));
        assertThat(first.request()).isSameAs(networkRequest);
        assertThat(first.cookies().getFirst("example").getValue()).isEqualTo("cookie-value");
        var hit = filter.filter(request, next -> Mono.error(new AssertionError("expected a cache hit")))
                .block();
        assertThat(hit.bodyToMono(Payload.class).block()).isEqualTo(new Payload("custom"));
        var revalidating = CachingExchangeFilterFunction.builder()
                .cache(cache)
                .alwaysRevalidate(true)
                .exchangeStrategies(strategies)
                .build();
        var validationRequest = mock(HttpRequest.class);
        when(validationRequest.getMethod()).thenReturn(HttpMethod.GET);
        when(validationRequest.getURI()).thenReturn(request.url());
        var refreshed = revalidating
                .filter(
                        request,
                        next -> Mono.just(ClientResponse.create(HttpStatus.NOT_MODIFIED, strategies)
                                .cookie("example", "updated-cookie")
                                .request(validationRequest)
                                .build()))
                .block();
        assertThat(refreshed.bodyToMono(Payload.class).block()).isEqualTo(new Payload("custom"));
        assertThat(refreshed.request()).isSameAs(validationRequest);
        assertThat(refreshed.cookies().getFirst("example").getValue()).isEqualTo("updated-cookie");
        assertThat(decodes).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISS", "HIT", "REVALIDATED"})
    void usesAnExplicitDecodeLimitForEveryCachePath(String path) {
        var cache = new ConcurrentMapCache("explicit-limit");
        if (!path.equals("MISS")) {
            cache.put(
                    "key",
                    new CachedResponse(new byte[2048], Map.of(), "\"v1\"", null, 60, System.currentTimeMillis()));
        }
        var strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024))
                .build();
        var filter = CachingExchangeFilterFunction.builder()
                .cache(cache)
                .cacheKeyMapper(request -> "key")
                .alwaysRevalidate(path.equals("REVALIDATED"))
                .exchangeStrategies(strategies)
                .build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/large"))
                .build();
        var response = filter.filter(
                        request,
                        next -> Mono.just(
                                path.equals("REVALIDATED")
                                        ? ClientResponse.create(HttpStatus.NOT_MODIFIED)
                                                .build()
                                        : largeResponse(2048)))
                .block();
        assertThatThrownBy(() -> response.bodyToMono(byte[].class).block())
                .isInstanceOf(DataBufferLimitException.class)
                .hasMessageContaining("1024");
    }

    @ParameterizedTest
    @ValueSource(ints = {300_000, 3_000_000, 16 * 1024 * 1024})
    void retainsThe16MiBDefaultForChunkedNetworkResponses(int size) {
        var cache = new ConcurrentMapCache("large-responses");
        var filter = CachingExchangeFilterFunction.builder().cache(cache).build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/large"))
                .build();
        var response =
                filter.filter(request, next -> Mono.just(largeResponse(size))).block();
        assertThat(response.bodyToMono(byte[].class).block()).hasSize(size).containsOnly((byte) 'x');
        if (size > CachingExchangeFilterFunction.DEFAULT_MAX_CACHEABLE_SIZE) {
            assertThat(cache.getNativeCache()).isEmpty();
            return;
        }
        var hit = filter.filter(request, next -> Mono.error(new AssertionError("expected a cache hit")))
                .block();
        assertThat(hit.bodyToMono(byte[].class).block()).hasSize(size).containsOnly((byte) 'x');
        var revalidating = CachingExchangeFilterFunction.builder()
                .cache(cache)
                .alwaysRevalidate(true)
                .build();
        var refreshed = revalidating
                .filter(
                        request,
                        next -> Mono.just(
                                ClientResponse.create(HttpStatus.NOT_MODIFIED).build()))
                .block();
        assertThat(refreshed.bodyToMono(byte[].class).block()).hasSize(size).containsOnly((byte) 'x');
    }

    @Test
    void keepsTheDefaultDecodeLimitBoundedAt16MiB() {
        var filter = CachingExchangeFilterFunction.builder().build();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/large"))
                .build();
        var response = filter.filter(request, next -> Mono.just(largeResponse(16 * 1024 * 1024 + 1)))
                .block();
        assertThatThrownBy(() -> response.bodyToMono(byte[].class).block())
                .isInstanceOf(DataBufferLimitException.class)
                .hasMessageContaining("16777216");
    }

    private ClientResponse largeResponse(int size) {
        var buffers = new DefaultDataBufferFactory();
        var body = Flux.range(0, (size + 4095) / 4096).<DataBuffer>map(index -> {
            var bytes = new byte[Math.min(4096, size - index * 4096)];
            Arrays.fill(bytes, (byte) 'x');
            return buffers.wrap(bytes);
        });
        return ClientResponse.create(HttpStatus.OK)
                .header("ETag", "\"large\"")
                .header("Cache-Control", "max-age=60")
                .body(body)
                .build();
    }

    private record Payload(String value) {}
}
