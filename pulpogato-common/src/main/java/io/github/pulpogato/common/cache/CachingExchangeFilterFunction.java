package io.github.pulpogato.common.cache;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NoOpCache;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 * serve fresh hits again instead of revalidating on every subsequent request.
 *
 * <p>Responses larger than {@link #maxCacheableSize} are not cached but are still returned
 * successfully. This prevents memory issues with very large responses.
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
     * Name of the custom header indicating cache status.
     */
    public static final String CACHE_HEADER_NAME = "X-Pulpogato-Cache";

    /**
     * Default maximum size for cacheable responses (2MB).
     */
    public static final int DEFAULT_MAX_CACHEABLE_SIZE = 2 * 1024 * 1024;

    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");

    // Use a 16MB buffer limit for exchange strategies to handle responses larger than the cache limit
    private static final ExchangeStrategies LARGE_BUFFER_STRATEGIES = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    public static final String CACHE_STATUS = "cache.status";
    public static final String CACHE_HIT = "HIT";
    public static final String CACHE_REVALIDATED = "REVALIDATED";
    public static final String CACHE_SKIP = "SKIP";
    public static final String CACHE_MISS = "MISS";

    /**
     * {@code cache.status} value on a {@code pulpogato.cache.get} span for a cached entry that
     * exists but will be revalidated or refetched (expired, or {@link #alwaysRevalidate}).
     */
    public static final String CACHE_STALE = "STALE";

    /**
     * {@code cache.status} value on a {@code pulpogato.cache.put} span for a response written to the cache.
     */
    public static final String CACHE_STORED = "STORED";

    // Observation names for the two cache operations. They wrap only the cache read/write, so on a
    // MISS/REVALIDATE they sit as siblings of the network exchange span rather than wrapping it.
    private static final String OBSERVATION_CACHE_GET = "pulpogato.cache.get";
    private static final String OBSERVATION_CACHE_PUT = "pulpogato.cache.put";
    private static final String URI = "uri";

    // High-cardinality tag carrying the value produced by the configured CacheKeyMapper. Lets
    // operations on the same entry be correlated, including entries that share a path but differ
    // by query, headers, or identity. The built-in mappers hash it; a custom mapper controls its
    // content, so it may expose whatever that mapper returns.
    private static final String CACHE_KEY = "cache.key";

    private static final String OBSERVATION_CONTEXT_KEY = "micrometer.observation";

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

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        // Only cache GET requests
        return switch (request.method().name()) {
            case "GET", "QUERY" -> getResponseWithCache(request, next);
            default -> next.exchange(request);
        };
    }

    private @NonNull Mono<ClientResponse> getResponseWithCache(
            @NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        // Read the parent observation from the reactive context so the cache.get/cache.put spans
        // are parented to the client request span. Reading it explicitly avoids relying on a
        // ThreadLocal that may be absent on the thread this runs on (e.g. boundedElastic).
        return Mono.deferContextual(ctx -> doFilter(request, next, ctx.getOrDefault(OBSERVATION_CONTEXT_KEY, null)));
    }

    private Mono<ClientResponse> doFilter(ClientRequest request, ExchangeFunction next, Observation parent) {
        var cacheKey = cacheKeyMapper.apply(request);
        var cached = lookup(cacheKey, request, parent);

        // If we have a fresh cached response and not forcing revalidation, return it
        if (cached != null && !cached.isExpired(clock.millis()) && !alwaysRevalidate) {
            return Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                    .headers(h -> h.putAll(cached.getHeaders()))
                    .header(CACHE_HEADER_NAME, CACHE_HIT)
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
                var refreshed = refreshFromNotModified(cacheKey, request, cached, response, parent);
                return response.releaseBody()
                        .then(Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                                .headers(h -> h.putAll(refreshed.getHeaders()))
                                .header(CACHE_HEADER_NAME, CACHE_REVALIDATED)
                                .body(Flux.just(bufferFactory.wrap(refreshed.getBody())))
                                .build()));
            }

            // Cache the response if it has caching headers
            if (response.statusCode().is2xxSuccessful()) {
                return cacheResponse(cacheKey, request, response, parent);
            }

            return Mono.just(response);
        });
    }

    /**
     * Applies a 304 Not Modified to the stored entry per
     * <a href="https://www.rfc-editor.org/rfc/rfc9111#section-4.3.4">RFC 9111 section 4.3.4</a>:
     * header fields from the 304 replace the corresponding stored fields, and the freshness lifetime
     * restarts from now so the entry can serve fresh hits again instead of revalidating on every
     * subsequent request. The refreshed entry is written back to the cache inside a
     * {@code pulpogato.cache.put} span.
     */
    private CachedResponse refreshFromNotModified(
            String cacheKey,
            ClientRequest request,
            CachedResponse cached,
            ClientResponse notModified,
            Observation parent) {
        var updateHeaders = notModified.headers().asHttpHeaders();

        // Overlay the 304's header fields onto the stored headers (case-insensitively via HttpHeaders).
        var merged = new HttpHeaders();
        cached.getHeaders().forEach(merged::addAll);
        updateHeaders.forEach((name, values) -> {
            // A 304 carries no body, so a Content-Length it sends (often 0) must not overwrite the
            // length of the stored representation we are about to serve.
            if (!HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                merged.put(name, new ArrayList<>(values));
            }
        });

        var headerMap = HashMap.<String, List<String>>newHashMap(merged.size());
        merged.forEach((name, values) -> headerMap.put(name, new ArrayList<>(values)));

        // Recompute caching metadata from the merged headers so any refreshed ETag, Last-Modified, or
        // Cache-Control the server sent on the 304 is reflected. When the 304 omits a header, the
        // merged headers retain the stored value; if even that is absent, fall back to the entry's
        // existing metadata so revalidation never discards known freshness information. A header is
        // absent exactly when getFirst returns null, so a present-but-updated value still wins.
        var mergedCacheControl = merged.getFirst(HttpHeaders.CACHE_CONTROL);
        var etag = merged.getETag() != null ? merged.getETag() : cached.getEtag();
        var lastModified = merged.getFirst(HttpHeaders.LAST_MODIFIED) != null
                ? merged.getFirst(HttpHeaders.LAST_MODIFIED)
                : cached.getLastModified();
        var maxAge = mergedCacheControl != null ? parseMaxAge(mergedCacheControl) : cached.getMaxAgeSeconds();

        var refreshed = new CachedResponse(cached.getBody(), headerMap, etag, lastModified, maxAge, clock.millis());
        recordPut(parent, request, cacheKey, CACHE_STORED, () -> cache.put(cacheKey, refreshed));
        return refreshed;
    }

    /**
     * Reads the cache entry inside a {@code pulpogato.cache.get} span, tagging the outcome
     * ({@link #CACHE_HIT}/{@link #CACHE_STALE}/{@link #CACHE_MISS}). The span wraps only the read,
     * so it captures the cache backend's lookup latency without enclosing the network exchange.
     */
    private CachedResponse lookup(String cacheKey, ClientRequest request, Observation parent) {
        var observation = Observation.createNotStarted(OBSERVATION_CACHE_GET, observationRegistry)
                .parentObservation(parent)
                .highCardinalityKeyValue(URI, request.url().toString())
                .highCardinalityKeyValue(CACHE_KEY, cacheKey);
        return observation.observe(() -> {
            var cached = cache.get(cacheKey, CachedResponse.class);
            var freshHit = cached != null && !cached.isExpired(clock.millis()) && !alwaysRevalidate;
            observation.lowCardinalityKeyValue(
                    CACHE_STATUS, cached == null ? CACHE_MISS : (freshHit ? CACHE_HIT : CACHE_STALE));
            return cached;
        });
    }

    private Mono<ClientResponse> cacheResponse(
            String cacheKey, ClientRequest request, ClientResponse response, Observation parent) {
        var headers = response.headers().asHttpHeaders();
        var etag = headers.getETag();
        var lastModified = headers.getFirst("Last-Modified");
        var cacheControl = headers.getFirst("Cache-Control");

        var maxAge = parseMaxAge(cacheControl);

        // Only cache if there are caching headers
        if (etag == null && lastModified == null && maxAge < 0) {
            return Mono.just(response);
        }

        // Check Content-Length if available - skip caching if too large
        var contentLength = headers.getContentLength();
        if (contentLength > maxCacheableSize) {
            return Mono.just(response);
        }

        // Copy headers to a plain Map for serialization.
        // Optimized with pre-allocated HashMap to avoid resizing.
        var headerMap = HashMap.<String, List<String>>newHashMap(headers.size());
        headers.forEach((key, values) -> headerMap.put(key, new ArrayList<>(values)));

        // Buffer the response body and check size
        return response.body(BodyExtractors.toDataBuffers())
                .reduce(new ByteArrayOutputStream(), (baos, buffer) -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    baos.writeBytes(bytes);
                    return baos;
                })
                .map(ByteArrayOutputStream::toByteArray)
                .defaultIfEmpty(new byte[0])
                .map(body -> {
                    // If the response is too large, skip caching but still return the data
                    if (body.length > maxCacheableSize) {
                        recordPut(parent, request, cacheKey, CACHE_SKIP, null);
                        return ClientResponse.create(response.statusCode(), LARGE_BUFFER_STRATEGIES)
                                .headers(h -> h.putAll(headerMap))
                                .header(CACHE_HEADER_NAME, CACHE_SKIP)
                                .body(Flux.just(bufferFactory.wrap(body)))
                                .build();
                    }

                    // Cache and return
                    var cachedResponse =
                            new CachedResponse(body, headerMap, etag, lastModified, maxAge, clock.millis());
                    recordPut(parent, request, cacheKey, CACHE_STORED, () -> cache.put(cacheKey, cachedResponse));

                    return ClientResponse.create(response.statusCode(), LARGE_BUFFER_STRATEGIES)
                            .headers(h -> h.putAll(headerMap))
                            .header(CACHE_HEADER_NAME, CACHE_MISS)
                            .body(Flux.just(bufferFactory.wrap(body)))
                            .build();
                });
    }

    /**
     * Records a {@code pulpogato.cache.put} span for the store phase, tagging it with the outcome
     * ({@link #CACHE_STORED} or {@link #CACHE_SKIP}). When {@code store} is non-null it runs inside
     * the span so the span captures the cache backend's write latency; a null {@code store} records
     * a zero-work span documenting that the response was not cached.
     */
    private void recordPut(Observation parent, ClientRequest request, String cacheKey, String status, Runnable store) {
        var observation = Observation.createNotStarted(OBSERVATION_CACHE_PUT, observationRegistry)
                .parentObservation(parent)
                .highCardinalityKeyValue(URI, request.url().toString())
                .highCardinalityKeyValue(CACHE_KEY, cacheKey)
                .lowCardinalityKeyValue(CACHE_STATUS, status);
        observation.observe(store != null ? store : () -> {});
    }

    private static long parseMaxAge(String cacheControl) {
        if (cacheControl != null) {
            var matcher = MAX_AGE_PATTERN.matcher(cacheControl);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        return -1;
    }
}
