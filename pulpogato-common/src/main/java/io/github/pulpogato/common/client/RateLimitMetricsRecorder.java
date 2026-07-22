package io.github.pulpogato.common.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

/**
 * Framework-agnostic rate-limit metrics recording shared by {@link MetricsExchangeFunction} and
 * {@link MetricsClientHttpRequestInterceptor}: reads the {@code x-ratelimit-*} response headers
 * via a caller-supplied lookup and reports them to a {@link MeterRegistry}.
 */
class RateLimitMetricsRecorder {

    private static final String RATE_LIMIT_LIMIT = "x-ratelimit-limit";
    private static final String RATE_LIMIT_REMAINING = "x-ratelimit-remaining";
    private static final String RATE_LIMIT_USED = "x-ratelimit-used";
    private static final String RATE_LIMIT_RESET = "x-ratelimit-reset";
    private static final String RATE_LIMIT_RESOURCE = "x-ratelimit-resource";

    private final MeterRegistry registry;
    private final Clock clock;
    private final String prefix;
    private final List<Tag> defaultTags;

    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    RateLimitMetricsRecorder(MeterRegistry registry, Clock clock, String prefix, List<Tag> defaultTags) {
        this.registry = registry;
        this.clock = clock;
        this.prefix = prefix;
        this.defaultTags = defaultTags;
    }

    void recordMetrics(UnaryOperator<String> headerLookup) {
        var resource = headerLookup.apply(RATE_LIMIT_RESOURCE);

        List<Tag> tags = new ArrayList<>(defaultTags);
        tags.add(Tag.of("resource", resource != null ? resource : "unknown"));

        withNumericHeader(headerLookup, RATE_LIMIT_LIMIT, v -> setGauge(prefix + ".limit", tags, v));
        withNumericHeader(headerLookup, RATE_LIMIT_REMAINING, v -> setGauge(prefix + ".remaining", tags, v));
        withNumericHeader(headerLookup, RATE_LIMIT_USED, v -> setGauge(prefix + ".used", tags, v));
        withNumericHeader(
                headerLookup,
                RATE_LIMIT_RESET,
                resetValue -> setGauge(
                        prefix + ".secondsToReset",
                        tags,
                        Math.max(0, resetValue - clock.instant().getEpochSecond())));
    }

    private void withNumericHeader(UnaryOperator<String> headerLookup, String headerName, LongConsumer consumer) {
        var value = headerLookup.apply(headerName);
        if (value != null) {
            try {
                consumer.accept(Long.parseLong(value));
            } catch (NumberFormatException ignore) {
                // ignore this
            }
        }
    }

    private void setGauge(String name, List<Tag> tags, long value) {
        var key = name + "/" + tags;
        var ref = gauges.computeIfAbsent(key, k -> {
            var atomicLong = new AtomicLong(value);
            registry.gauge(name, tags, atomicLong, AtomicLong::get);
            return atomicLong;
        });
        ref.set(value);
    }
}
