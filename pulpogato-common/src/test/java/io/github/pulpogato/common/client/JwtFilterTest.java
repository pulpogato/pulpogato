package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class JwtFilterTest {

    private JwtFactory jwtFactory;
    private ExchangeFunction exchangeFunction;
    private ClientResponse mockResponse;

    @BeforeEach
    void setUp() {
        jwtFactory = mock(JwtFactory.class);
        exchangeFunction = mock(ExchangeFunction.class);
        mockResponse = mock(ClientResponse.class);
    }

    @Test
    void addsAuthorizationBearerHeader() {
        when(jwtFactory.create(any(), any())).thenReturn("test-jwt-token");
        var filter = JwtFilter.builder().jwtFactory(jwtFactory).build();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        filter.filter(originalRequest, exchangeFunction).block();

        var captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());

        var capturedRequest = captor.getValue();
        var authHeader = capturedRequest.headers().getFirst("Authorization");

        assertThat(authHeader).isEqualTo("Bearer test-jwt-token");
    }

    @Test
    void callsFactoryWithCorrectTimestamps() {
        var fixedInstant = Instant.parse("2024-01-01T12:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var filter =
                JwtFilter.builder().jwtFactory(jwtFactory).clock(fixedClock).build();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        filter.filter(originalRequest, exchangeFunction).block();

        var issuedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        var expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jwtFactory).create(issuedAtCaptor.capture(), expiresAtCaptor.capture());

        // iat should be 60 seconds before current time
        assertThat(issuedAtCaptor.getValue()).isEqualTo(fixedInstant.minusSeconds(60));

        // exp should be 540 seconds (TOKEN_VALIDITY) after current time
        assertThat(expiresAtCaptor.getValue()).isEqualTo(fixedInstant.plusSeconds(540));
    }

    @Test
    void preservesOriginalRequestProperties() {
        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var filter = JwtFilter.builder().jwtFactory(jwtFactory).build();

        var originalRequest = ClientRequest.create(HttpMethod.POST, URI.create("https://api.github.com/repos"))
                .header("Content-Type", "application/json")
                .header("X-Custom-Header", "custom-value")
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        filter.filter(originalRequest, exchangeFunction).block();

        var captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());

        var capturedRequest = captor.getValue();
        assertThat(capturedRequest.method()).isEqualTo(HttpMethod.POST);
        assertThat(capturedRequest.url()).isEqualTo(URI.create("https://api.github.com/repos"));
        assertThat(capturedRequest.headers().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(capturedRequest.headers().getFirst("X-Custom-Header")).isEqualTo("custom-value");
    }

    @Test
    void returnsResponseFromExchangeFunction() {
        var expectedResponse = mock(ClientResponse.class);
        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var filter = JwtFilter.builder().jwtFactory(jwtFactory).build();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(expectedResponse));

        var actualResponse = filter.filter(originalRequest, exchangeFunction).block();

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void cachesTokenBetweenRequests() {
        var fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        when(jwtFactory.create(any(), any())).thenReturn("cached-token");
        var filter =
                JwtFilter.builder().jwtFactory(jwtFactory).clock(fixedClock).build();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        // Make two requests
        filter.filter(originalRequest, exchangeFunction).block();
        filter.filter(originalRequest, exchangeFunction).block();

        // Factory should only be called once due to caching
        verify(jwtFactory, times(1)).create(any(), any());

        // Both requests should have the same token
        var captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction, times(2)).exchange(captor.capture());
        var token1 = captor.getAllValues().get(0).headers().getFirst("Authorization");
        var token2 = captor.getAllValues().get(1).headers().getFirst("Authorization");
        assertThat(token1).isEqualTo(token2);
    }

    @Test
    void refreshesTokenWhenCloseToExpiry() {
        var currentTime = new AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
        var mutableClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };

        when(jwtFactory.create(any(), any())).thenReturn("token-1", "token-2");
        var filter =
                JwtFilter.builder().jwtFactory(jwtFactory).clock(mutableClock).build();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        // Make first request
        filter.filter(originalRequest, exchangeFunction).block();

        // Advance time to within 30 seconds of expiry (token valid for 540s, refresh buffer is 30s)
        currentTime.set(currentTime.get().plus(Duration.ofSeconds(515)));

        // Make second request - should get a new token
        filter.filter(originalRequest, exchangeFunction).block();

        // Factory should be called twice
        verify(jwtFactory, times(2)).create(any(), any());

        // Tokens should be different
        var captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction, times(2)).exchange(captor.capture());
        var token1 = captor.getAllValues().get(0).headers().getFirst("Authorization");
        var token2 = captor.getAllValues().get(1).headers().getFirst("Authorization");
        assertThat(token1).isEqualTo("Bearer token-1");
        assertThat(token2).isEqualTo("Bearer token-2");
    }
}
