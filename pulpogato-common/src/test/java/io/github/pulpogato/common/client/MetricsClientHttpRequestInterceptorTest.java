package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

@ExtendWith(MockitoExtension.class)
class MetricsClientHttpRequestInterceptorTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private MeterRegistry meterRegistry;

    @Mock
    private HttpRequest request;

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private ClientHttpResponse response;

    private MetricsClientHttpRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        interceptor = MetricsClientHttpRequestInterceptor.builder()
                .registry(meterRegistry)
                .clock(clock)
                .build();
    }

    @Test
    void testRateLimitMetricsAreRecorded() throws Exception {
        Instant now = clock.instant();
        Instant hourLater = now.plus(1, ChronoUnit.HOURS);

        var headers = new HttpHeaders();
        headers.add("x-ratelimit-limit", "5000");
        headers.add("x-ratelimit-remaining", "4999");
        headers.add("x-ratelimit-used", "1");
        headers.add("x-ratelimit-reset", String.valueOf(hourLater.getEpochSecond()));
        headers.add("x-ratelimit-resource", "core");
        given(response.getHeaders()).willReturn(headers);
        given(execution.execute(any(), any())).willReturn(response);

        var result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result).isEqualTo(response);

        var meters = meterRegistry.getMeters();
        assertThat(meters)
                .isNotEmpty()
                .hasSize(4)
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.limit");
                    assertThat(meter.getId().getTag("resource")).isEqualTo("core");
                    assertThat(meter).isInstanceOf(DefaultGauge.class);
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(5000);
                })
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.remaining");
                    assertThat(meter.getId().getTag("resource")).isEqualTo("core");
                    assertThat(meter).isInstanceOf(DefaultGauge.class);
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(4999);
                })
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.used");
                    assertThat(meter.getId().getTag("resource")).isEqualTo("core");
                    assertThat(meter).isInstanceOf(DefaultGauge.class);
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(1);
                })
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.secondsToReset");
                    assertThat(meter.getId().getTag("resource")).isEqualTo("core");
                    assertThat(meter).isInstanceOf(DefaultGauge.class);
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(3600);
                });
    }

    @Test
    void testGaugeValuesUpdateOnSubsequentResponses() throws Exception {
        Instant now = clock.instant();
        Instant hourLater = now.plus(1, ChronoUnit.HOURS);

        var headers = new HttpHeaders();
        headers.add("x-ratelimit-limit", "5000");
        headers.add("x-ratelimit-remaining", "4999");
        headers.add("x-ratelimit-used", "1");
        headers.add("x-ratelimit-reset", String.valueOf(hourLater.getEpochSecond()));
        headers.add("x-ratelimit-resource", "core");
        given(response.getHeaders()).willReturn(headers);
        given(execution.execute(any(), any())).willReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        var response2 = mock(ClientHttpResponse.class);
        var headers2 = new HttpHeaders();
        headers2.add("x-ratelimit-limit", "5000");
        headers2.add("x-ratelimit-remaining", "4990");
        headers2.add("x-ratelimit-used", "10");
        headers2.add("x-ratelimit-reset", String.valueOf(hourLater.getEpochSecond()));
        headers2.add("x-ratelimit-resource", "core");
        given(response2.getHeaders()).willReturn(headers2);
        given(execution.execute(any(), any())).willReturn(response2);

        var result2 = interceptor.intercept(request, new byte[0], execution);

        assertThat(result2).isEqualTo(response2);

        var meters = meterRegistry.getMeters();
        assertThat(meters).hasSize(4);

        assertThat(meters)
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.remaining");
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(4990);
                })
                .anySatisfy(meter -> {
                    assertThat(meter.getId().getName()).isEqualTo("github.api.rateLimit.used");
                    var gauge = (DefaultGauge) meter;
                    assertThat(gauge.value()).isEqualTo(10);
                });
    }

    @Test
    void testRateLimitMetricsWithMissingHeaders() throws Exception {
        var headers = new HttpHeaders();
        headers.add("x-ratelimit-limit", "5000");
        given(response.getHeaders()).willReturn(headers);
        given(execution.execute(any(), any())).willReturn(response);

        var result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result).isEqualTo(response);

        var meters = meterRegistry.getMeters();
        assertThat(meters).isNotEmpty().hasSize(1);
        var theMeter = meters.getFirst();
        assertThat(theMeter).isInstanceOf(DefaultGauge.class);
        assertThat(theMeter.getId().getName()).isEqualTo("github.api.rateLimit.limit");
        var gauge = (DefaultGauge) theMeter;
        assertThat(gauge.value()).isEqualTo(5000);
    }
}
