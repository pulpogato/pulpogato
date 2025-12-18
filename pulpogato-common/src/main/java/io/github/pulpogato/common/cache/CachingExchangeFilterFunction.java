package io.github.pulpogato.common.cache;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
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
 * <p>Example usage:
 * <pre>{@code
 * HttpCache cache = new InMemoryHttpCache();
 * WebClient client = WebClient.builder()
 *     .filter(new CachingExchangeFilterFunction(cache))
 *     .build();
 * }</pre>
 */
@RequiredArgsConstructor
public class CachingExchangeFilterFunction implements ExchangeFilterFunction {

    /**
     * Name of the custom header indicating cache status.
     */
    public static final String CACHE_HEADER_NAME = "X-Pulpogato-Cache";

    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    /**
     * The cache instance to store and retrieve cached responses.
     */
    private final Cache cache;

    /**
     * Function to map ClientRequest to cache key strings.
     */
    private final CacheKeyMapper cacheKeyMapper;

    /**
     * Clock instance for time-based operations.
     */
    private final Clock clock;

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
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .headers(h -> h.putAll(cached.headers()))
                    .header(CACHE_HEADER_NAME, "HIT")
                    .body(Flux.just(bufferFactory.wrap(cached.body())))
                    .build());
        }

        // Build request with conditional headers if we have a stale cache entry
        var requestBuilder = ClientRequest.from(request);
        if (cached != null && cached.canRevalidate()) {
            if (cached.etag() != null) {
                requestBuilder.header("If-None-Match", cached.etag());
            }
            if (cached.lastModified() != null) {
                requestBuilder.header("If-Modified-Since", cached.lastModified());
            }
        }

        var conditionalRequest = requestBuilder.build();

        return next.exchange(conditionalRequest).flatMap(response -> {
            // On 304, return cached body
            if (response.statusCode().value() == 304 && cached != null) {
                return response.releaseBody()
                        .then(Mono.just(ClientResponse.create(HttpStatus.OK)
                                .headers(h -> h.putAll(cached.headers()))
                                .header(CACHE_HEADER_NAME, "REVALIDATED")
                                .body(Flux.just(bufferFactory.wrap(cached.body())))
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
        // Copy headers to a plain Map for serialization
        var headerMap = new HashMap<String, List<String>>();
        headers.forEach((key, values) -> headerMap.put(key, new ArrayList<>(values)));
        return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).map(body -> {
            var cachedResponse = new CachedResponse(body, headerMap, etag, lastModified, maxAge, clock.millis());
            cache.put(cacheKey, cachedResponse);

            return ClientResponse.create(response.statusCode())
                    .headers(h -> headers.forEach(h::put))
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
