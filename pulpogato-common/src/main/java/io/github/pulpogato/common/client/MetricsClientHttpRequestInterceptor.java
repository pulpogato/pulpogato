package io.github.pulpogato.common.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A {@link ClientHttpRequestInterceptor} that captures and records rate limit metrics from HTTP
 * responses, typically for GitHub API usage. Metrics are extracted from specific response headers
 * and reported to a {@link MeterRegistry}. This is particularly useful for monitoring API rate
 * limits and ensuring that applications can appropriately handle rate limit constraints.
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
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of {@link MetricsExchangeFunction}.</p>
 */
@Builder
public class MetricsClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final MeterRegistry registry;

    @Builder.Default
    private final Clock clock = Clock.systemUTC();

    @Builder.Default
    private final String prefix = "github.api.rateLimit";

    @Builder.Default
    private final List<Tag> defaultTags = List.of();

    @Getter(lazy = true)
    private final RateLimitMetricsRecorder recorder =
            new RateLimitMetricsRecorder(registry, clock, prefix, defaultTags);

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        var response = execution.execute(request, body);
        getRecorder().recordMetrics(headerName -> getFirstHeader(response, headerName));
        return response;
    }

    private String getFirstHeader(ClientHttpResponse response, String headerName) {
        List<String> headerValues = response.getHeaders().get(headerName);
        if (headerValues != null && !headerValues.isEmpty()) {
            return headerValues.getFirst();
        }
        return null;
    }
}
