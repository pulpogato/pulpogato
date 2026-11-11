package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class CredentialIsolationIntegrationTest {
    private static final String CREDENTIAL_HEADER = "X-Test-Credential";
    private final ConcurrentMapCache cache = new ConcurrentMapCache("credentials");
    private final AtomicInteger exchanges = new AtomicInteger();

    static Stream<Arguments> adapters() {
        return Stream.of(true, false)
                .flatMap(reactive -> Stream.of(true, false)
                        .map(upstreamAuthentication -> Arguments.of(reactive, upstreamAuthentication)));
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void isolatesCredentialsAndAnonymousRequestsThroughTheClient(boolean reactive, boolean upstreamAuthentication) {
        var fetch = reactive ? reactiveClient(upstreamAuthentication) : blockingClient(upstreamAuthentication);
        assertThat(fetch.apply("Bearer alice")).isEqualTo("Bearer alice");
        assertThat(fetch.apply("Bearer bob")).isEqualTo("Bearer bob");
        assertThat(fetch.apply(null)).isEqualTo("anonymous");
        assertThat(fetch.apply("Bearer alice")).isEqualTo("Bearer alice");
        assertThat(exchanges).hasValue(3);
        assertThat(cache.getNativeCache()).hasSize(3);
    }

    private Function<String, String> reactiveClient(boolean upstreamAuthentication) {
        var builder = WebClient.builder().baseUrl("https://api.github.com").exchangeFunction(request -> {
            exchanges.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
                    .header(HttpHeaders.ETAG, "\"v1\"")
                    .body(identity(request.headers()))
                    .build());
        });
        if (upstreamAuthentication) {
            builder.filter((request, next) -> {
                var authenticated = ClientRequest.from(request)
                        .headers(CredentialIsolationIntegrationTest::authenticate)
                        .build();
                return next.exchange(authenticated);
            });
        }
        builder.filter(CachingExchangeFilterFunction.builder().cache(cache).build());
        var client = builder.build();
        return credential -> {
            var call = client.get().uri("/user");
            if (credential != null)
                call.header(upstreamAuthentication ? CREDENTIAL_HEADER : HttpHeaders.AUTHORIZATION, credential);
            return call.retrieve().bodyToMono(String.class).block();
        };
    }

    private Function<String, String> blockingClient(boolean upstreamAuthentication) {
        var builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory((uri, method) -> new Request(uri, method, exchanges));
        if (upstreamAuthentication) {
            builder.requestInterceptor((request, body, next) -> {
                authenticate(request.getHeaders());
                return next.execute(request, body);
            });
        }
        builder.requestInterceptor(
                CachingClientHttpRequestInterceptor.builder().cache(cache).build());
        var client = builder.build();
        return credential -> {
            var call = client.get().uri("/user");
            if (credential != null)
                call.header(upstreamAuthentication ? CREDENTIAL_HEADER : HttpHeaders.AUTHORIZATION, credential);
            return call.retrieve().body(String.class);
        };
    }

    private static void authenticate(HttpHeaders headers) {
        var credential = headers.getFirst(CREDENTIAL_HEADER);
        if (credential != null) headers.set(HttpHeaders.AUTHORIZATION, credential);
    }

    private static String identity(HttpHeaders headers) {
        var authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return authorization == null ? "anonymous" : authorization;
    }

    @Getter
    @RequiredArgsConstructor
    private static final class Request implements ClientHttpRequest {
        private final URI uri;
        private final HttpMethod method;
        private final AtomicInteger exchanges;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = new HashMap<>();
        private final OutputStream body = OutputStream.nullOutputStream();

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public ClientHttpResponse execute() {
            exchanges.incrementAndGet();
            return new Response(identity(headers));
        }
    }

    @Getter
    private static final class Response implements ClientHttpResponse {
        private final HttpStatusCode statusCode = HttpStatus.OK;
        private final String statusText = "OK";
        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body;

        Response(String identity) {
            headers.setCacheControl("max-age=60");
            headers.setETag("\"v1\"");
            body = new ByteArrayInputStream(identity.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            // This response owns only an in-memory byte array, with no external resources to release.
        }
    }
}
