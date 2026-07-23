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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class JwtClientHttpRequestInterceptorTest {

    private JwtFactory jwtFactory;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse mockResponse;

    @BeforeEach
    void setUp() {
        jwtFactory = mock(JwtFactory.class);
        execution = mock(ClientHttpRequestExecution.class);
        mockResponse = mock(ClientHttpResponse.class);
    }

    private static TestHttpRequest request(HttpMethod method, String uri) {
        return new TestHttpRequest(method, URI.create(uri));
    }

    @RequiredArgsConstructor
    @Getter
    @NullMarked
    private static final class TestHttpRequest implements HttpRequest {
        private final HttpMethod method;
        private final HttpHeaders headers = new HttpHeaders();
        private final java.util.Map<String, Object> attributes = new java.util.HashMap<>();
        private final URI uri;

        @Override
        public URI getURI() {
            return uri;
        }
    }

    @Test
    void addsAuthorizationBearerHeader() throws Exception {
        when(jwtFactory.create(any(), any())).thenReturn("test-jwt-token");
        var interceptor =
                JwtClientHttpRequestInterceptor.builder().jwtFactory(jwtFactory).build();

        var originalRequest = request(HttpMethod.GET, "https://api.github.com");
        when(execution.execute(any(), any())).thenReturn(mockResponse);

        interceptor.intercept(originalRequest, new byte[0], execution);

        var captor = ArgumentCaptor.forClass(TestHttpRequest.class);
        verify(execution).execute(captor.capture(), any());

        assertThat(captor.getValue().getHeaders().getFirst("Authorization")).isEqualTo("Bearer test-jwt-token");
    }

    @Test
    void callsFactoryWithCorrectTimestamps() throws Exception {
        var fixedInstant = Instant.parse("2024-01-01T12:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var interceptor = JwtClientHttpRequestInterceptor.builder()
                .jwtFactory(jwtFactory)
                .clock(fixedClock)
                .build();

        var originalRequest = request(HttpMethod.GET, "https://api.github.com");
        when(execution.execute(any(), any())).thenReturn(mockResponse);

        interceptor.intercept(originalRequest, new byte[0], execution);

        var issuedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        var expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jwtFactory).create(issuedAtCaptor.capture(), expiresAtCaptor.capture());

        assertThat(issuedAtCaptor.getValue()).isEqualTo(fixedInstant.minusSeconds(60));
        assertThat(expiresAtCaptor.getValue()).isEqualTo(fixedInstant.plusSeconds(540));
    }

    @Test
    void preservesOriginalRequestProperties() throws Exception {
        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var interceptor =
                JwtClientHttpRequestInterceptor.builder().jwtFactory(jwtFactory).build();

        var originalRequest = request(HttpMethod.POST, "https://api.github.com/repos");
        originalRequest.getHeaders().add("Content-Type", "application/json");
        originalRequest.getHeaders().add("X-Custom-Header", "custom-value");
        when(execution.execute(any(), any())).thenReturn(mockResponse);

        interceptor.intercept(originalRequest, new byte[0], execution);

        var captor = ArgumentCaptor.forClass(TestHttpRequest.class);
        verify(execution).execute(captor.capture(), any());

        var capturedRequest = captor.getValue();
        assertThat(capturedRequest.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(capturedRequest.getURI()).isEqualTo(URI.create("https://api.github.com/repos"));
        assertThat(capturedRequest.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(capturedRequest.getHeaders().getFirst("X-Custom-Header")).isEqualTo("custom-value");
    }

    @Test
    void returnsResponseFromExecution() throws Exception {
        var expectedResponse = mock(ClientHttpResponse.class);
        when(jwtFactory.create(any(), any())).thenReturn("test-token");
        var interceptor =
                JwtClientHttpRequestInterceptor.builder().jwtFactory(jwtFactory).build();

        var originalRequest = request(HttpMethod.GET, "https://api.github.com");
        when(execution.execute(any(), any())).thenReturn(expectedResponse);

        var actualResponse = interceptor.intercept(originalRequest, new byte[0], execution);

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void cachesTokenBetweenRequests() throws Exception {
        var fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
        when(jwtFactory.create(any(), any())).thenReturn("cached-token");
        var interceptor = JwtClientHttpRequestInterceptor.builder()
                .jwtFactory(jwtFactory)
                .clock(fixedClock)
                .build();

        when(execution.execute(any(), any())).thenReturn(mockResponse);

        interceptor.intercept(request(HttpMethod.GET, "https://api.github.com"), new byte[0], execution);
        interceptor.intercept(request(HttpMethod.GET, "https://api.github.com"), new byte[0], execution);

        verify(jwtFactory, times(1)).create(any(), any());

        var captor = ArgumentCaptor.forClass(TestHttpRequest.class);
        verify(execution, times(2)).execute(captor.capture(), any());
        var token1 = captor.getAllValues().get(0).getHeaders().getFirst("Authorization");
        var token2 = captor.getAllValues().get(1).getHeaders().getFirst("Authorization");
        assertThat(token1).isEqualTo(token2);
    }

    @Test
    void refreshesTokenWhenCloseToExpiry() throws Exception {
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
        var interceptor = JwtClientHttpRequestInterceptor.builder()
                .jwtFactory(jwtFactory)
                .clock(mutableClock)
                .build();

        when(execution.execute(any(), any())).thenReturn(mockResponse);

        interceptor.intercept(request(HttpMethod.GET, "https://api.github.com"), new byte[0], execution);

        currentTime.set(currentTime.get().plus(Duration.ofSeconds(515)));

        interceptor.intercept(request(HttpMethod.GET, "https://api.github.com"), new byte[0], execution);

        verify(jwtFactory, times(2)).create(any(), any());

        var captor = ArgumentCaptor.forClass(TestHttpRequest.class);
        verify(execution, times(2)).execute(captor.capture(), any());
        var token1 = captor.getAllValues().get(0).getHeaders().getFirst("Authorization");
        var token2 = captor.getAllValues().get(1).getHeaders().getFirst("Authorization");
        assertThat(token1).isEqualTo("Bearer token-1");
        assertThat(token2).isEqualTo("Bearer token-2");
    }
}
