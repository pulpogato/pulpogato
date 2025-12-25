package io.github.pulpogato.common.cache;

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
 *   <li>ETag / If-None-Match
 *   <li>Last-Modified / If-Modified-Since
 *   <li>Cache-Control max-age
 * </ul>
 *
 * <p>When a cached response exists and is still fresh (within max-age), it is returned directly.
 * When expired, conditional headers are added and on 304 Not Modified the cached response body
 * is returned.
 *
 * <p>Responses larger than {@link #maxCacheableSize} are not cached but are still returned
 * successfully. This prevents memory issues with very large responses.
 *
 * <p>Example usage:
 * <pre>{@code
 * HttpCache cache = new InMemoryHttpCache();
 * WebClient client = WebClient.builder()
 *     .filter(new CachingExchangeFilterFunction(cache))
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

    // Use 16MB buffer limit for exchange strategies to handle responses larger than cache limit
    private static final ExchangeStrategies LARGE_BUFFER_STRATEGIES = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

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
     * Creates a new CachingExchangeFilterFunction with the default max cacheable size (2MB).
     *
     * @param cache          The cache instance to store responses
     * @param cacheKeyMapper Function to generate cache keys from requests
     * @param clock          Clock for time-based operations
     * @deprecated Use the builder() method instead
     */
    @Deprecated
    public CachingExchangeFilterFunction(Cache cache, CacheKeyMapper cacheKeyMapper, Clock clock) {
        this(cache, cacheKeyMapper, clock, DEFAULT_MAX_CACHEABLE_SIZE);
    }

    /**
     * Creates a new CachingExchangeFilterFunction with a custom max cacheable size.
     *
     * @param cache            The cache instance to store responses
     * @param cacheKeyMapper   Function to generate cache keys from requests
     * @param clock            Clock for time-based operations
     * @param maxCacheableSize Maximum response size in bytes to cache (responses larger than this
     *                         will be returned but not cached)
     * @deprecated Use the builder() method instead
     */
    @Deprecated
    public CachingExchangeFilterFunction(
            Cache cache, CacheKeyMapper cacheKeyMapper, Clock clock, int maxCacheableSize) {
        this.cache = cache;
        this.cacheKeyMapper = cacheKeyMapper;
        this.clock = clock;
        this.maxCacheableSize = maxCacheableSize;
    }

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        // Only cache GET requests
        if (!request.method().name().equals("GET")) {
            return next.exchange(request);
        }

        var cacheKey = cacheKeyMapper.apply(request);
        var cached = cache.get(cacheKey, CachedResponse.class);

        // If we have a fresh cached response, return it
        if (cached != null && !cached.isExpired(clock.millis())) {
            return Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                    .headers(h -> h.putAll(cached.getHeaders()))
                    .header(CACHE_HEADER_NAME, "HIT")
                    .body(Flux.just(bufferFactory.wrap(cached.getBody())))
                    .build());
        }

        // Build request with conditional headers if we have a stale cache entry
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
            // On 304, return cached body
            if (response.statusCode().value() == 304 && cached != null) {
                return response.releaseBody()
                        .then(Mono.just(ClientResponse.create(HttpStatus.OK, LARGE_BUFFER_STRATEGIES)
                                .headers(h -> h.putAll(cached.getHeaders()))
                                .header(CACHE_HEADER_NAME, "REVALIDATED")
                                .body(Flux.just(bufferFactory.wrap(cached.getBody())))
                                .build()));
            }

            // Cache the response if it has caching headers
            if (response.statusCode().is2xxSuccessful()) {
                return cacheResponse(cacheKey, response);
            }

            return Mono.just(response);
        });
    }

    private Mono<ClientResponse> cacheResponse(String cacheKey, ClientResponse response) {
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

        // Copy headers to a plain Map for serialization
        var headerMap = new HashMap<String, List<String>>();
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
                    // If response is too large, skip caching but still return the data
                    if (body.length > maxCacheableSize) {
                        return ClientResponse.create(response.statusCode(), LARGE_BUFFER_STRATEGIES)
                                .headers(h -> h.putAll(headerMap))
                                .header(CACHE_HEADER_NAME, "SKIP")
                                .body(Flux.just(bufferFactory.wrap(body)))
                                .build();
                    }

                    // Cache and return
                    var cachedResponse =
                            new CachedResponse(body, headerMap, etag, lastModified, maxAge, clock.millis());
                    cache.put(cacheKey, cachedResponse);

                    return ClientResponse.create(response.statusCode(), LARGE_BUFFER_STRATEGIES)
                            .headers(h -> h.putAll(headerMap))
                            .header(CACHE_HEADER_NAME, "MISS")
                            .body(Flux.just(bufferFactory.wrap(body)))
                            .build();
                });
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
