package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

class DefaultHeadersTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void suppliesMissingDefaults(boolean reactive) throws Exception {
        var headers = new HttpHeaders();
        headers.set("Other-Header", "other-value");

        var result = applyHeaders(reactive, headers);

        assertThat(result.get("X-GitHub-Api-Version")).containsExactly("2022-11-28-test");
        assertThat(result.get("X-Pulpogato-Version")).containsExactly("1.0.0-test");
        assertThat(result.get("Other-Header")).containsExactly("other-value");
    }

    @ParameterizedTest
    @MethodSource("clientsAndHeaders")
    void preservesExplicitHeaderValues(boolean reactive, String headerName) throws Exception {
        var headers = new HttpHeaders();
        headers.set(headerName, "caller-value");

        var result = applyHeaders(reactive, headers);

        assertThat(result.get(headerName)).containsExactly("caller-value");
        assertThat(result.containsHeader("X-GitHub-Api-Version")).isTrue();
        assertThat(result.containsHeader("X-Pulpogato-Version")).isTrue();
    }

    static Stream<Arguments> clientsAndHeaders() {
        return Stream.of(false, true)
                .flatMap(reactive -> Stream.of(
                                "X-GitHub-Api-Version",
                                "x-github-api-version",
                                "X-Pulpogato-Version",
                                "x-pulpogato-version")
                        .map(header -> Arguments.of(reactive, header)));
    }

    private HttpHeaders applyHeaders(boolean reactive, HttpHeaders headers) throws Exception {
        var captured = new AtomicReference<HttpHeaders>();
        if (reactive) {
            var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com"))
                    .headers(values -> values.addAll(headers))
                    .build();
            new DefaultHeadersExchangeFunction()
                    .filter(request, outgoing -> {
                        captured.set(outgoing.headers());
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    })
                    .block();
        } else {
            var request = mock(HttpRequest.class);
            when(request.getHeaders()).thenReturn(headers);
            new DefaultHeadersClientHttpRequestInterceptor().intercept(request, new byte[0], (outgoing, body) -> {
                captured.set(outgoing.getHeaders());
                return mock(ClientHttpResponse.class);
            });
        }
        return captured.get();
    }
}
