package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class DefaultHeadersExchangeFunctionTest {

    private ExchangeFunction exchangeFunction;
    private ClientResponse mockResponse;

    @BeforeEach
    void setUp() {
        exchangeFunction = mock(ExchangeFunction.class);
        mockResponse = mock(ClientResponse.class);
    }

    @Test
    void addsHeadersFromPropertiesFile() {
        // Test the default constructor behavior with test properties file
        var filter = new DefaultHeadersExchangeFunction();

        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        var result = filter.filter(originalRequest, exchangeFunction).block();

        // Verify that the exchange function was called (meaning the filter worked)
        var captor = org.mockito.ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());

        var capturedRequest = captor.getValue();

        // With our test properties file, both headers should be present
        assertThat(capturedRequest.headers().get("X-GitHub-Api-Version")).containsExactly("2022-11-28-test");
        assertThat(capturedRequest.headers().get("X-Pulpogato-Version")).containsExactly("1.0.0-test");

        assertThat(result).isEqualTo(mockResponse);
    }

    @Test
    void preservesOriginalRequestHeadersAndMethod() {
        var filter = new DefaultHeadersExchangeFunction();
        var originalRequest = ClientRequest.create(HttpMethod.POST, URI.create("https://api.github.com"))
                .header("Existing-Header", "existing-value")
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));

        filter.filter(originalRequest, exchangeFunction).block();

        var captor = org.mockito.ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());

        var capturedRequest = captor.getValue();
        // Verify that original headers are preserved
        assertThat(capturedRequest.headers().get("Existing-Header")).containsExactly("existing-value");
        assertThat(capturedRequest.method()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void returnsCorrectResponseFromExchangeFunction() {
        var expectedResponse = mock(ClientResponse.class);
        var filter = new DefaultHeadersExchangeFunction();
        var originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                .build();

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(expectedResponse));

        var actualResponse = filter.filter(originalRequest, exchangeFunction).block();

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }
}
