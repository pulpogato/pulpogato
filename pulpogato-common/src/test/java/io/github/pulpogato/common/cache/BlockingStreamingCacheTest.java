package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

class BlockingStreamingCacheTest {
    private final ConcurrentMapCache cache = new ConcurrentMapCache("streaming");
    private final CachingClientHttpRequestInterceptor interceptor = CachingClientHttpRequestInterceptor.builder()
            .cache(cache)
            .cacheKeyMapper(request -> "key")
            .maxCacheableSize(64)
            .build();

    @ParameterizedTest
    @ValueSource(ints = {0, 63, 64, 65, 1048576})
    void buffersOnlyTheCacheablePrefixAndPreservesTheCompleteBody(int size) throws IOException {
        var bytes = new byte[size];
        for (var index = 0; index < size; index++) {
            bytes[index] = (byte) index;
        }
        var input = new ByteArrayInputStream(bytes);
        var original = new Response(input);
        try (var result = interceptor.intercept(new Request(), new byte[0], (request, body) -> original)) {
            assertThat(input.available()).isEqualTo(Math.max(0, size - 65));
            assertThat(original.closed).isEqualTo(size <= 64);
            assertThat(cache.get("key") != null).isEqualTo(size <= 64);
            assertThat(result.getBody().readAllBytes()).isEqualTo(bytes);
        }
        assertThat(original.closed).isTrue();
    }

    static Stream<Throwable> readFailures() {
        return Stream.of(
                new IOException("connection lost"),
                new IllegalStateException("read failed"),
                new AssertionError("read failed"));
    }

    @ParameterizedTest
    @MethodSource("readFailures")
    void preservesReadFailuresAndSuppressesCleanupFailures(Throwable failure) {
        var original = new Response(new InputStream() {
            @Override
            public int read() throws IOException {
                if (failure instanceof IOException ioFailure) {
                    throw ioFailure;
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw (Error) failure;
            }
        });
        var cleanup = new IllegalStateException("close failed");
        original.closeFailure = cleanup;
        assertThatThrownBy(() -> interceptor.intercept(new Request(), new byte[0], (request, body) -> original))
                .isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(cleanup);
        assertThat(original.closed).isTrue();
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void doesNotRetryAFailingCloseAfterSuccessfulBuffering() {
        var original = new Response(new ByteArrayInputStream(new byte[0]));
        original.closeFailure = new IllegalStateException("close failed");
        assertThatThrownBy(() -> interceptor.intercept(new Request(), new byte[0], (request, body) -> original))
                .isSameAs(original.closeFailure);
        assertThat(original.closes).isEqualTo(1);
        assertThat(cache.get("key")).isNull();
    }

    @Getter
    private static final class Request implements HttpRequest {
        private final HttpMethod method = HttpMethod.GET;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = Map.of();

        @Override
        public URI getURI() {
            return URI.create("https://api.github.com/resource");
        }
    }

    @RequiredArgsConstructor
    @Getter
    private static final class Response implements ClientHttpResponse {
        private final InputStream body;
        private final HttpStatus statusCode = HttpStatus.OK;
        private final String statusText = "OK";
        private final HttpHeaders headers = new HttpHeaders();
        private boolean closed;
        private int closes;
        private RuntimeException closeFailure;

        {
            headers.setETag("\"v1\"");
        }

        @Override
        public void close() {
            closed = true;
            closes++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
