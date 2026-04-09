package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MetricsExchangeFunctionTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private MeterRegistry meterRegistry;

    @Mock
    private ExchangeFunction exchangeFunction;

    @Mock
    private ClientResponse response;

    @Mock
    private ClientResponse.Headers headers;

    private MetricsExchangeFunction metricsExchangeFunction;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsExchangeFunction = MetricsExchangeFunction.builder()
                .registry(meterRegistry)
                .clock(clock)
                .build();
    }

    @Test
    void testRateLimitMetricsAreRecorded() {
        // GIVEN
        Instant now = clock.instant();
        Instant hourLater = now.plus(1, ChronoUnit.HOURS);

        given(headers.header("x-ratelimit-limit")).willReturn(List.of("5000"));
        given(headers.header("x-ratelimit-remaining")).willReturn(List.of("4999"));
        given(headers.header("x-ratelimit-used")).willReturn(List.of("1"));
        given(headers.header("x-ratelimit-reset")).willReturn(List.of(String.valueOf(hourLater.getEpochSecond())));
        given(headers.header("x-ratelimit-resource")).willReturn(List.of("core"));
        given(response.headers()).willReturn(headers);

        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(response));

        // WHEN
        ClientRequest clientRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/test"))
                .build();

        StepVerifier.create(metricsExchangeFunction.filter(clientRequest, exchangeFunction))
                .expectNext(response)
                .verifyComplete();

        // THEN
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
    void testGaugeValuesUpdateOnSubsequentResponses() {
        // GIVEN - first response
        Instant now = clock.instant();
        Instant hourLater = now.plus(1, ChronoUnit.HOURS);

        given(headers.header("x-ratelimit-limit")).willReturn(List.of("5000"));
        given(headers.header("x-ratelimit-remaining")).willReturn(List.of("4999"));
        given(headers.header("x-ratelimit-used")).willReturn(List.of("1"));
        given(headers.header("x-ratelimit-reset")).willReturn(List.of(String.valueOf(hourLater.getEpochSecond())));
        given(headers.header("x-ratelimit-resource")).willReturn(List.of("core"));
        given(response.headers()).willReturn(headers);
        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(response));

        var clientRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/test"))
                .build();

        StepVerifier.create(metricsExchangeFunction.filter(clientRequest, exchangeFunction))
                .expectNext(response)
                .verifyComplete();

        // GIVEN - second response with different values
        ClientResponse response2 = org.mockito.Mockito.mock(ClientResponse.class);
        ClientResponse.Headers headers2 = org.mockito.Mockito.mock(ClientResponse.Headers.class);
        given(headers2.header("x-ratelimit-limit")).willReturn(List.of("5000"));
        given(headers2.header("x-ratelimit-remaining")).willReturn(List.of("4990"));
        given(headers2.header("x-ratelimit-used")).willReturn(List.of("10"));
        given(headers2.header("x-ratelimit-reset")).willReturn(List.of(String.valueOf(hourLater.getEpochSecond())));
        given(headers2.header("x-ratelimit-resource")).willReturn(List.of("core"));
        given(response2.headers()).willReturn(headers2);
        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(response2));

        // WHEN - second call
        StepVerifier.create(metricsExchangeFunction.filter(clientRequest, exchangeFunction))
                .expectNext(response2)
                .verifyComplete();

        // THEN - still only 4 meters, values reflect the second response
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
    void testRateLimitMetricsWithMissingHeaders() {
        // GIVEN
        given(headers.header("x-ratelimit-limit")).willReturn(List.of("5000"));
        given(headers.header("x-ratelimit-remaining")).willReturn(null);
        given(headers.header("x-ratelimit-used")).willReturn(null);
        given(headers.header("x-ratelimit-reset")).willReturn(null);
        given(headers.header("x-ratelimit-resource")).willReturn(null);
        given(response.headers()).willReturn(headers);

        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(response));

        // WHEN
        ClientRequest clientRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/test"))
                .build();

        StepVerifier.create(metricsExchangeFunction.filter(clientRequest, exchangeFunction))
                .expectNext(response)
                .verifyComplete();

        // THEN
        var meters = meterRegistry.getMeters();
        assertThat(meters).isNotEmpty().hasSize(1);
        var theMeter = meters.getFirst();
        assertThat(theMeter).isInstanceOf(DefaultGauge.class);
        assertThat(theMeter.getId().getName()).isEqualTo("github.api.rateLimit.limit");
        var gauge = (DefaultGauge) theMeter;
        assertThat(gauge.value()).isEqualTo(5000);
    }
}
