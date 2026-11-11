package io.github.pulpogato.common.cache;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NoOpCache;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP caching filter that implements conditional request handling.
 *
 * <p>This filter intercepts requests and responses to provide HTTP caching based on:
 *
 * <ul>
 *   <li>{@code ETag} / {@code If-None-Match}
 *   <li>{@code Last-Modified} / {@code If-Modified-Since}
 *   <li>{@code Cache-Control max-age}
 * </ul>
 *
 * <p>When a cached response exists and is still fresh (within max-age), it is returned directly.
 * When expired, conditional headers are added and on 304 Not Modified the cached response body
 * is returned. Per <a href="https://www.rfc-editor.org/rfc/rfc9111#section-4.3.4">RFC 9111
 * section 4.3.4</a>, a 304 also refreshes the stored entry: the header fields from the 304 replace
 * the corresponding stored fields and the freshness lifetime restarts, so a validated entry can
 * serve fresh hits again instead of revalidating on every subsequent request. If the server instead
 * returns a fresh 200 for a stale entry, the stored value is invalidated and replaced.
 *
 * <p>Responses larger than {@link #maxCacheableSize} are not cached but are still returned
 * successfully. Cache copies are bounded by this limit while the original body is streamed to the caller.
 * Entries are stored only when the caller consumes the complete body; cancellation and errors never store partial data.
 * For unknown-length responses, the MISS/INVALIDATED header describes the network lookup; an oversized
 * body is reported as a cache.put SKIP observation when the limit is crossed.
 *
 * <p>Example usage:
 * <pre>{@code
 * HttpCache cache = new InMemoryHttpCache();
 * WebClient client = WebClient.builder()
 *     .filter(CachingExchangeFilterFunction.builder().cache(cache).build())
 *     .build();
 * }</pre>
 */
@Builder
public class CachingExchangeFilterFunction implements ExchangeFilterFunction {

    /**
     * Default maximum size for cacheable responses (2MB).
     */
    public static final int DEFAULT_MAX_CACHEABLE_SIZE = 2 * 1024 * 1024;

    // Use a 16MB buffer limit for exchange strategies to handle responses larger than the cache limit
    private static final ExchangeStrategies LARGE_BUFFER_STRATEGIES = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    private static final String OBSERVATION_CONTEXT_KEY = "micrometer.observation";
    private static final String CLIENT_TYPE = "WebClient";

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    /**
     * The cache instance to store and retrieve cached responses.
     */
    @Builder.Default
    private final Cache cache = new NoOpCache("no-op-cache");

    /**
     * Function to map ClientRequest to cache key strings.
     */
    @Builder.Default
    private final CacheKeyMapper cacheKeyMapper = new DefaultCacheKeyMapper();

    /**
     * Clock instance for time-based operations.
     */
    @Builder.Default
    private final Clock clock = Clock.systemUTC();

    /**
     * Maximum size in bytes for responses to be cached.
     * Responses larger than this will be returned but not cached.
     */
    @Builder.Default
    private final int maxCacheableSize = DEFAULT_MAX_CACHEABLE_SIZE;

    /**
     * When true, always send conditional requests to revalidate cached responses,
     * even if they haven't expired according to max-age. This is useful when
     * the data may change more frequently than the cache headers suggest.
     */
    @Builder.Default
    private final boolean alwaysRevalidate = false;

    /**
     * Observation registry for recording cache interaction spans.
     * Defaults to {@link ObservationRegistry#NOOP} which adds no overhead.
     */
    @Builder.Default
    private final ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

    @Getter(lazy = true)
    private final HttpCacheEngine engine = engine();

    private HttpCacheEngine engine() {
        return new HttpCacheEngine(cache, clock, observationRegistry, maxCacheableSize, alwaysRevalidate, CLIENT_TYPE);
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return switch (request.method().name()) {
            case "GET", "QUERY" ->
                request.headers().containsHeader(HttpHeaders.RANGE)
                        ? getRangeResponse(request, next)
                        : getResponseWithCache(request, next);
            default -> next.exchange(request);
        };
    }

    private Mono<ClientResponse> getRangeResponse(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(ctx -> {
            var fullRequest = ClientRequest.from(request)
                    .headers(headers -> {
                        headers.remove(HttpHeaders.RANGE);
                        headers.remove(HttpHeaders.IF_RANGE);
                    })
                    .build();
            var key = cacheKeyMapper.apply(fullRequest);
            return next.exchange(request)
                    .flatMap(response -> invalidateUncacheableResponse(
                            key,
                            request.url().toString(),
                            response,
                            status -> status.is2xxSuccessful() && status.value() != 206,
                            ctx.getOrDefault(OBSERVATION_CONTEXT_KEY, null)));
        });
    }

    private Mono<ClientResponse> invalidateUncacheableResponse(
            String key,
            String uri,
            ClientResponse response,
            Predicate<HttpStatusCode> replacementStatus,
            @Nullable Observation parent) {
        return Mono.fromCallable(() -> {
                    var status = response.statusCode();
                    getEngine()
                            .invalidateUncacheable(
                                    key,
                                    uri,
                                    toMap(response.headers().asHttpHeaders()),
                                    replacementStatus.test(status),
                                    status.is2xxSuccessful(),
                                    parent);
                    return response;
                })
                .onErrorResume(failure -> Mono.defer(response::releaseBody)
                        .onErrorResume(cleanupFailure -> {
                            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                            return Mono.empty();
                        })
                        .then(Mono.error(failure)));
    }

    private Mono<ClientResponse> getResponseWithCache(ClientRequest request, ExchangeFunction next) {
        // Read the parent observation from the reactive context so the cache.get/cache.put spans
        // are parented to the client request span. Reading it explicitly avoids relying on a
        // ThreadLocal that may be absent on the thread this runs on (e.g. boundedElastic).
        return Mono.deferContextual(ctx -> doFilter(request, next, ctx.getOrDefault(OBSERVATION_CONTEXT_KEY, null)));
    }

    private Mono<ClientResponse> doFilter(ClientRequest request, ExchangeFunction next, @Nullable Observation parent) {
        var cacheKey = cacheKeyMapper.apply(request);
        var cached = getEngine().lookup(cacheKey, request.url().toString(), parent);

        // If we have a fresh cached response and not forcing revalidation, return it
        if (cached != null && !cached.isExpired(clock.millis()) && !alwaysRevalidate) {
            return Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                    .headers(h -> h.putAll(cached.getHeaders()))
                    .header(HttpCacheEngine.CACHE_HEADER_NAME, HttpCacheEngine.CACHE_HIT)
                    .body(Flux.just(bufferFactory.wrap(cached.getBody())))
                    .build());
        }

        // Build request with conditional headers if we have a cache entry (stale or forcing revalidation)
        var requestBuilder = ClientRequest.from(request);
        if (cached != null && cached.canRevalidate()) {
            if (cached.getEtag() != null) {
                requestBuilder.header("If-None-Match", cached.getEtag());
            }
            if (cached.getLastModified() != null) {
                requestBuilder.header("If-Modified-Since", cached.getLastModified());
            }
        }

        var conditionalRequest = requestBuilder.build();

        return next.exchange(conditionalRequest).flatMap(response -> {
            // On 304 the stored representation is still valid: refresh its freshness and merge the
            // updated header fields (RFC 9111 4.3.4), then serve the cached body.
            if (response.statusCode().value() == 304 && cached != null) {
                var refreshed = getEngine()
                        .refreshFromNotModified(
                                cacheKey,
                                request.url().toString(),
                                cached,
                                toMap(response.headers().asHttpHeaders()),
                                parent);
                return response.releaseBody()
                        .then(Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                                .headers(h -> h.putAll(refreshed.getHeaders()))
                                .header(HttpCacheEngine.CACHE_HEADER_NAME, HttpCacheEngine.CACHE_REVALIDATED)
                                .body(Flux.just(bufferFactory.wrap(refreshed.getBody())))
                                .build()));
            }

            // Cache the response if it has caching headers
            if (response.statusCode().value() == 200) {
                return cacheResponse(cacheKey, request, response, cached != null, parent);
            }

            return invalidateUncacheableResponse(
                    cacheKey, request.url().toString(), response, HttpStatusCode::is2xxSuccessful, parent);
        });
    }

    private Mono<ClientResponse> cacheResponse(
            String cacheKey,
            ClientRequest request,
            ClientResponse response,
            boolean wasCached,
            @Nullable Observation parent) {
        var headers = response.headers().asHttpHeaders();
        var etag = headers.getETag();
        var lastModified = headers.getFirst("Last-Modified");
        var cacheControl = HttpCacheEngine.cacheControl(toMap(headers));

        if (HttpCacheEngine.hasDirective(cacheControl, "no-store")) {
            return invalidateUncacheableResponse(cacheKey, request.url().toString(), response, status -> false, parent);
        }

        var maxAge = HttpCacheEngine.parseMaxAge(cacheControl);
        var uri = request.url().toString();

        // Only cache if there are caching headers, and the (known) length is within the limit
        var skipReason = getEngine().skipReason(etag, lastModified, maxAge, headers.getContentLength());
        if (skipReason != null) {
            getEngine().recordSkip(cacheKey, uri, skipReason, parent);
            return Mono.just(response);
        }

        // Copy headers to a plain Map for serialization.
        var headerMap = toMap(headers);

        var putStatus = wasCached ? HttpCacheEngine.CACHE_INVALIDATED : HttpCacheEngine.CACHE_STORED;
        var headerStatus = wasCached ? HttpCacheEngine.CACHE_INVALIDATED : HttpCacheEngine.CACHE_MISS;
        var bodySubscribed = new AtomicBoolean();
        var body = Flux.defer(() -> {
            var source = response.body(BodyExtractors.toDataBuffers());
            // WebClient subscribes again during response cleanup; only the first subscription
            // can contain the complete representation, even if it was cancelled or failed.
            if (!bodySubscribed.compareAndSet(false, true)) {
                return source;
            }
            var collector = new CacheBody();
            return source.doOnNext(buffer -> {
                        if (collector.append(buffer, maxCacheableSize)) {
                            getEngine().recordSkip(cacheKey, uri, HttpCacheEngine.SKIP_BODY_SIZE, parent);
                        }
                    })
                    .doOnComplete(() -> {
                        var retained = collector.content;
                        if (retained != null) {
                            var cachedResponse = new CachedResponse(
                                    retained.toByteArray(), headerMap, etag, lastModified, maxAge, clock.millis());
                            getEngine()
                                    .recordPut(
                                            cacheKey,
                                            uri,
                                            putStatus,
                                            () -> cache.put(cacheKey, cachedResponse),
                                            parent);
                        }
                    })
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        });
        return Mono.just(ClientResponse.create(response.statusCode(), LARGE_BUFFER_STRATEGIES)
                .headers(h -> h.putAll(headerMap))
                .header(HttpCacheEngine.CACHE_HEADER_NAME, headerStatus)
                .body(body)
                .build());
    }

    private static final class CacheBody {
        private @Nullable ByteArrayOutputStream content = new ByteArrayOutputStream();

        boolean append(DataBuffer buffer, int limit) {
            var retained = content;
            if (retained == null) {
                return false;
            }
            if (buffer.readableByteCount() > limit - retained.size()) {
                content = null;
                return true;
            }
            try (var buffers = buffer.readableByteBuffers()) {
                while (buffers.hasNext()) {
                    var readable = buffers.next();
                    var bytes = new byte[readable.remaining()];
                    readable.get(bytes);
                    retained.writeBytes(bytes);
                }
            }
            return false;
        }
    }

    private static Map<String, List<String>> toMap(HttpHeaders headers) {
        var map = HashMap.<String, List<String>>newHashMap(headers.size());
        headers.forEach((name, values) -> map.put(name, new ArrayList<>(values)));
        return map;
    }
}
