/**
 * HTTP client filters and interceptors for GitHub API access.
 *
 * <p>Each concern is available in two forms: a WebClient
 * {@link org.springframework.web.reactive.function.client.ExchangeFilterFunction}
 * and a {@code RestClient} {@code ClientHttpRequestInterceptor}, so callers can use
 * either reactive or blocking HTTP stacks.
 *
 * <p>Provided filters:
 * <ul>
 *   <li>{@link io.github.pulpogato.common.client.DefaultHeadersExchangeFunction} — adds
 *       {@code X-GitHub-Api-Version} and {@code X-Pulpogato-Version} headers</li>
 *   <li>{@link io.github.pulpogato.common.client.JwtFilter} — GitHub App JWT authentication
 *       with token caching via {@link io.github.pulpogato.common.client.JwtFactory}</li>
 *   <li>{@link io.github.pulpogato.common.client.MetricsExchangeFunction} — records
 *       GitHub rate-limit headers to a Micrometer {@code MeterRegistry}</li>
 *   <li>{@link io.github.pulpogato.common.client.RedirectExchangeFunction} — follows 3xx
 *       redirects (e.g. renamed repositories)</li>
 *   <li>{@link io.github.pulpogato.common.client.NoContentExchangeFunction} — handles
 *       204 No Content responses without deserialization errors</li>
 * </ul>
 */
@NullMarked
package io.github.pulpogato.common.client;

import org.jspecify.annotations.NullMarked;
