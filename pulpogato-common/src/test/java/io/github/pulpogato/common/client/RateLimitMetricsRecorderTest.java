package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class RateLimitMetricsRecorderTest {

    private static final Instant FIXED_INSTANT = Instant.ofEpochSecond(1_700_000_000L);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

    private RateLimitMetricsRecorder recorder() {
        return new RateLimitMetricsRecorder(registry, clock, "github.api.rateLimit", List.of());
    }

    private UnaryOperator<String> lookup(Map<String, String> headers) {
        return headers::get;
    }

    @Test
    void recordsLimitRemainingAndUsed() {
        recorder()
                .recordMetrics(lookup(Map.of(
                        "x-ratelimit-limit", "5000",
                        "x-ratelimit-remaining", "4999",
                        "x-ratelimit-used", "1",
                        "x-ratelimit-resource", "core")));

        assertThat(registry.get("github.api.rateLimit.limit").gauge().value()).isEqualTo(5000);
        assertThat(registry.get("github.api.rateLimit.remaining").gauge().value())
                .isEqualTo(4999);
        assertThat(registry.get("github.api.rateLimit.used").gauge().value()).isEqualTo(1);
    }

    @Test
    void clampsSecondsToResetAtZeroWhenResetIsInThePast() {
        var pastReset = FIXED_INSTANT.getEpochSecond() - 100;

        recorder()
                .recordMetrics(
                        lookup(Map.of("x-ratelimit-reset", String.valueOf(pastReset), "x-ratelimit-resource", "core")));

        assertThat(registry.get("github.api.rateLimit.secondsToReset").gauge().value())
                .isEqualTo(0);
    }

    @Test
    void computesSecondsToResetWhenResetIsInTheFuture() {
        var futureReset = FIXED_INSTANT.getEpochSecond() + 300;

        recorder()
                .recordMetrics(lookup(
                        Map.of("x-ratelimit-reset", String.valueOf(futureReset), "x-ratelimit-resource", "core")));

        assertThat(registry.get("github.api.rateLimit.secondsToReset").gauge().value())
                .isEqualTo(300);
    }

    @Test
    void ignoresUnparseableHeaderValues() {
        recorder().recordMetrics(lookup(Map.of("x-ratelimit-limit", "not-a-number", "x-ratelimit-resource", "core")));

        assertThat(registry.find("github.api.rateLimit.limit").gauge()).isNull();
    }

    @Test
    void reusesTheSameGaugeAcrossRecordMetricsCalls() {
        var recorder = recorder();
        recorder.recordMetrics(lookup(Map.of("x-ratelimit-remaining", "10", "x-ratelimit-resource", "core")));
        recorder.recordMetrics(lookup(Map.of("x-ratelimit-remaining", "5", "x-ratelimit-resource", "core")));

        assertThat(registry.getMeters().stream()
                        .filter(m -> m.getId().getName().equals("github.api.rateLimit.remaining"))
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("github.api.rateLimit.remaining").gauge().value())
                .isEqualTo(5);
    }

    @Test
    void tagsGaugeWithUnknownResourceWhenMissing() {
        recorder().recordMetrics(lookup(Map.of("x-ratelimit-remaining", "10")));

        assertThat(registry.get("github.api.rateLimit.remaining")
                        .tag("resource", "unknown")
                        .gauge()
                        .value())
                .isEqualTo(10);
    }
}
