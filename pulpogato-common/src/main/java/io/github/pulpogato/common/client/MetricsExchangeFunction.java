package io.github.pulpogato.common.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Clock;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * A class that implements the {@link ExchangeFilterFunction} interface to capture
 * and record rate limit metrics from HTTP responses, typically for GitHub API usage.
 * Metrics are extracted from specific response headers and reported to a {@link MeterRegistry}.
 * This is particularly useful for monitoring API rate limits and ensuring that
 * applications can appropriately handle rate limit constraints.
 *
 * <h2>Functionality</h2>
 * The following HTTP headers are monitored for rate limit metrics:
 * <ul>
 *   <li> {@code x-ratelimit-limit}: The maximum number of requests that can be made.
 *   <li> {@code x-ratelimit-remaining}: The number of requests remaining in the current limit window.
 *   <li> {@code x-ratelimit-used}: The number of requests already used.
 *   <li> {@code x-ratelimit-reset}: The timestamp for when the rate limit resets.
 *   <li> {@code x-ratelimit-resource}: The resource for which the rate limit applies.
 * </ul>
 */
@Builder
public class MetricsExchangeFunction implements ExchangeFilterFunction {

    /**
     * A registry instance used to record and manage application metrics.
     * This is used for monitoring and gathering metrics data related to
     * rate limiting and other quantifiable events during HTTP request processing.
     */
    private final MeterRegistry registry;
    /**
     * The `clock` variable represents the system's default implementation of a clock,
     * which is used for retrieving the current date and time.
     * <p>
     * By default, it is initialized to the UTC time zone using {@link Clock#systemUTC()}.
     */
    @Builder.Default
    private final Clock clock = Clock.systemUTC();
    /**
     * The base prefix used for naming metrics related to GitHub API rate limits.
     */
    @Builder.Default
    private final String prefix = "github.api.rateLimit";
    /**
     * A list of default tags applied to all metrics recorded by the {@code MetricsExchangeFunction}.
     */
    @Builder.Default
    private final List<Tag> defaultTags = List.of();

    // Lombok's @Getter(lazy=true) defers this initializer to first access instead of running it as
    // a plain field initializer, which would otherwise race @Builder.Default's assignment of
    // registry/clock/prefix/defaultTags in the generated constructor. The recorder's gauge map must
    // persist across calls, so it can't just be rebuilt per call.
    @Getter(lazy = true)
    private final RateLimitMetricsRecorder recorder =
            new RateLimitMetricsRecorder(registry, clock, prefix, defaultTags);

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        return next.exchange(request).doOnNext(this::recordRateLimitMetrics);
    }

    private void recordRateLimitMetrics(ClientResponse response) {
        getRecorder().recordMetrics(headerName -> getFirstHeader(response, headerName));
    }

    private String getFirstHeader(ClientResponse response, String headerName) {
        List<String> headerValues = response.headers().header(headerName);
        if (headerValues != null && !headerValues.isEmpty()) {
            return headerValues.getFirst();
        }
        return null;
    }
}
