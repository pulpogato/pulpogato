package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RedirectHandlingTest {
    private static final URI ORIGINAL = URI.create("https://api.github.com/repos/old");
    private static final URI TARGET = URI.create("https://api.github.com/repos/new");

    private static final List<String> BODY_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.TRANSFER_ENCODING,
            HttpHeaders.CONTENT_ENCODING,
            HttpHeaders.CONTENT_LANGUAGE,
            HttpHeaders.CONTENT_LOCATION,
            HttpHeaders.CONTENT_RANGE,
            "Content-MD5",
            "Digest",
            "Content-Digest",
            "Repr-Digest",
            "Trailer",
            HttpHeaders.EXPECT);

    static Stream<Arguments> redirects() {
        return Stream.of(
                Arguments.of(301, "POST", "GET"),
                Arguments.of(302, "POST", "GET"),
                Arguments.of(303, "PUT", "GET"),
                Arguments.of(303, "HEAD", "HEAD"),
                Arguments.of(307, "POST", "POST"),
                Arguments.of(308, "PUT", "PUT"),
                Arguments.of(301, "GET", "GET"));
    }

    @ParameterizedTest
    @MethodSource("redirects")
    void reactiveRedirectResolvesRelativeLocationAndReleasesBody(int status, String original, String expected) {
        var drained = new AtomicBoolean();
        var calls = new AtomicInteger();
        var request = ClientRequest.create(HttpMethod.valueOf(original), ORIGINAL)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.CONTENT_LENGTH, "2")
                .headers(headers -> {
                    BODY_HEADERS.forEach(name -> headers.set(name, "body-metadata"));
                    headers.set("Authorization", "Bearer token");
                })
                .build();
        var result = new RedirectExchangeFunction()
                .filter(request, next -> {
                    if (calls.getAndIncrement() == 0) {
                        return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                                .header(HttpHeaders.LOCATION, "new")
                                .body(Flux.defer(() -> {
                                    drained.set(true);
                                    return Flux.empty();
                                }))
                                .build());
                    }
                    assertThat(drained).isTrue();
                    assertThat(next.url()).isEqualTo(TARGET);
                    assertThat(next.method()).isEqualTo(HttpMethod.valueOf(expected));
                    assertThat(next.headers().getFirst("Authorization")).isEqualTo("Bearer token");
                    if (!expected.equals(original)) {
                        assertThat(next.headers().headerNames()).doesNotContainAnyElementsOf(BODY_HEADERS);
                    } else {
                        assertThat(next.body()).isSameAs(request.body());
                        assertThat(next.headers().headerNames()).containsAll(BODY_HEADERS);
                    }
                    return Mono.just(
                            ClientResponse.create(HttpStatus.OK).body("done").build());
                })
                .block();
        assertThat(result.bodyToMono(String.class).block()).isEqualTo("done");
        assertThat(calls).hasValue(2);
    }

    @ParameterizedTest
    @MethodSource("redirects")
    void blockingRedirectUsesSameMethodPolicy(int status, String original, String expected) throws Exception {
        var calls = new AtomicInteger();
        var request = mock(HttpRequest.class);
        when(request.getURI()).thenReturn(ORIGINAL);
        when(request.getMethod()).thenReturn(HttpMethod.valueOf(original));
        var headers = new HttpHeaders();
        headers.setContentLength(2);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        BODY_HEADERS.forEach(name -> headers.set(name, "body-metadata"));
        headers.set("Authorization", "Bearer token");
        when(request.getHeaders()).thenReturn(headers);
        var redirect = mock(ClientHttpResponse.class);
        when(redirect.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
        var redirectHeaders = new HttpHeaders();
        redirectHeaders.setLocation(URI.create("new"));
        when(redirect.getHeaders()).thenReturn(redirectHeaders);
        var terminal = mock(ClientHttpResponse.class);
        when(terminal.getStatusCode()).thenReturn(HttpStatus.OK);
        when(terminal.getHeaders()).thenReturn(new HttpHeaders());
        var result = new RedirectClientHttpRequestInterceptor().intercept(request, new byte[] {1, 2}, (next, body) -> {
            if (calls.getAndIncrement() == 0) {
                return redirect;
            }
            verify(redirect).close();
            assertThat(next.getURI()).isEqualTo(TARGET);
            assertThat(next.getMethod()).isEqualTo(HttpMethod.valueOf(expected));
            assertThat(body).hasSize(expected.equals(original) ? 2 : 0);
            assertThat(next.getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
            if (!expected.equals(original)) {
                assertThat(next.getHeaders().headerNames()).doesNotContainAnyElementsOf(BODY_HEADERS);
            } else {
                assertThat(next.getHeaders().headerNames()).containsAll(BODY_HEADERS);
            }
            return terminal;
        });
        assertThat(result).isSameAs(terminal);
        assertThat(calls).hasValue(2);
    }

    static Stream<Arguments> locations() {
        return Stream.of(false, true)
                .flatMap(reactive -> Stream.of(
                        Arguments.of(reactive, "?page=2", "https://api.github.com/repos/old?page=2"),
                        Arguments.of(reactive, "?page=a%2Fb", "https://api.github.com/repos/old?page=a%2Fb"),
                        Arguments.of(reactive, "?page=2#top", "https://api.github.com/repos/old?page=2#top"),
                        Arguments.of(reactive, "?", "https://api.github.com/repos/old?"),
                        Arguments.of(reactive, "#top", "https://api.github.com/repos/old?before=1#top"),
                        Arguments.of(reactive, "../new", "https://api.github.com/new"),
                        Arguments.of(reactive, "/repos/new", "https://api.github.com/repos/new"),
                        Arguments.of(reactive, "https://api.github.com/new", "https://api.github.com/new")));
    }

    @ParameterizedTest(name = "reactive={0}, Location={1}")
    @MethodSource("locations")
    void resolvesRedirectLocations(boolean reactive, String location, String expected) throws Exception {
        var original = URI.create("https://api.github.com/repos/old?before=1#previous");
        var calls = new AtomicInteger();
        if (reactive) {
            var result = new RedirectExchangeFunction()
                    .filter(ClientRequest.create(HttpMethod.GET, original).build(), next -> {
                        if (calls.getAndIncrement() == 0) {
                            return Mono.just(ClientResponse.create(HttpStatus.FOUND)
                                    .header(HttpHeaders.LOCATION, location)
                                    .build());
                        }
                        assertThat(next.url()).isEqualTo(URI.create(expected));
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    })
                    .block();
            assertThat(result.statusCode()).isEqualTo(HttpStatus.OK);
        } else {
            var request = mock(HttpRequest.class);
            when(request.getURI()).thenReturn(original);
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            when(request.getHeaders()).thenReturn(new HttpHeaders());
            var redirect = mock(ClientHttpResponse.class);
            when(redirect.getStatusCode()).thenReturn(HttpStatus.FOUND);
            var headers = new HttpHeaders();
            headers.setLocation(URI.create(location));
            when(redirect.getHeaders()).thenReturn(headers);
            var terminal = mock(ClientHttpResponse.class);
            when(terminal.getStatusCode()).thenReturn(HttpStatus.OK);
            when(terminal.getHeaders()).thenReturn(new HttpHeaders());
            var result = new RedirectClientHttpRequestInterceptor().intercept(request, new byte[0], (next, body) -> {
                if (calls.getAndIncrement() == 0) {
                    return redirect;
                }
                assertThat(next.getURI()).isEqualTo(URI.create(expected));
                return terminal;
            });
            assertThat(result).isSameAs(terminal);
            verify(redirect).close();
        }
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRedirectNotModifiedResponse() {
        var calls = new AtomicInteger();
        var response = ClientResponse.create(HttpStatus.NOT_MODIFIED)
                .header(HttpHeaders.LOCATION, "new")
                .build();
        var result = new RedirectExchangeFunction()
                .filter(ClientRequest.create(HttpMethod.GET, ORIGINAL).build(), request -> {
                    calls.incrementAndGet();
                    return Mono.just(response);
                })
                .block();
        assertThat(result).isSameAs(response);
        assertThat(calls).hasValue(1);
    }

    @Test
    void limitsRedirectLoops() {
        var calls = new AtomicInteger();
        var result = new RedirectExchangeFunction()
                .filter(ClientRequest.create(HttpMethod.GET, ORIGINAL).build(), request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.MOVED_PERMANENTLY)
                            .header(HttpHeaders.LOCATION, "new")
                            .build());
                })
                .block();
        assertThat(result.statusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(calls).hasValue(6);
    }
}
