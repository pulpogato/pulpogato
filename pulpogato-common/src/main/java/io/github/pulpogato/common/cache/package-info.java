/**
 * RFC 9111 conditional HTTP caching for Pulpogato clients.
 *
 * <p>Provides a framework-agnostic caching engine and adapters for both Spring WebClient
 * ({@link io.github.pulpogato.common.cache.CachingExchangeFilterFunction}) and
 * {@code RestClient} ({@link io.github.pulpogato.common.cache.CachingClientHttpRequestInterceptor}).
 * The shared {@link io.github.pulpogato.common.cache.HttpCacheEngine} handles cache reads,
 * writes, and revalidation; adapters are responsible for buffering request/response bodies
 * and building conditional requests.
 *
 * <p>Cache keys are derived from request metadata via a {@link io.github.pulpogato.common.cache.CacheKeyMapper};
 * the default implementation hashes the HTTP method, URI, and relevant headers.
 * Responses include an {@code X-Pulpogato-Cache} header indicating the cache outcome
 * (HIT, MISS, REVALIDATED, STALE, etc.).
 */
@NullMarked
package io.github.pulpogato.common.cache;

import org.jspecify.annotations.NullMarked;
