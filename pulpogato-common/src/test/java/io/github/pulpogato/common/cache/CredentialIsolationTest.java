package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;

class CredentialIsolationTest {
    private static final URI URI_VALUE = URI.create("https://api.github.com/user");

    @ParameterizedTest
    @CsvSource(
            value = {
                "<anonymous>, token ghp_alice",
                "token ghp_alice, token ghp_bob",
                "Bearer ghs_alice, Bearer ghs_bob",
                "Basic YWxpY2U6cGFzcw==, Basic Ym9iOnBhc3M="
            },
            nullValues = "<anonymous>")
    void isolatesCredentialsInBothAdapters(String first, String second) {
        assertThat(reactiveKey(first)).isNotEqualTo(reactiveKey(second));
        assertThat(blockingKey(first)).isNotEqualTo(blockingKey(second));
        assertThat(reactiveKey(first)).isEqualTo(blockingKey(first));
    }

    @Test
    void fingerprintsAllAuthorizationValuesWithoutAmbiguousDelimiters() {
        var variants = List.of(
                List.of("Bearer shared", "Bearer alice"),
                List.of("Bearer shared", "Bearer bob"),
                List.of("Bearer shared, Bearer alice"),
                List.of("Bearer alice", "Bearer shared"));
        var reactive = new DefaultCacheKeyMapper();
        var blocking = new DefaultHttpRequestCacheKeyMapper();
        var keys = variants.stream()
                .map(values -> {
                    var request = reactiveRequest(null);
                    var reactiveRequest = ClientRequest.from(request)
                            .headers(headers -> headers.put(HttpHeaders.AUTHORIZATION, values))
                            .build();
                    var blockingRequest = blockingRequest(null);
                    blockingRequest.getHeaders().put(HttpHeaders.AUTHORIZATION, values);
                    var key = reactive.apply(reactiveRequest);
                    assertThat(blocking.apply(blockingRequest)).isEqualTo(key);
                    return key;
                })
                .toList();
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void sharedFingerprintSecretsAreDefensivelyCopiedAndAgreeAcrossAdapters() {
        var secret = new byte[32];
        Arrays.fill(secret, (byte) 7);
        var reactive = new DefaultCacheKeyMapper(secret);
        var blocking = new DefaultHttpRequestCacheKeyMapper(secret);
        var first = reactive.apply(reactiveRequest("Basic dXNlcjpwYXNz"));
        Arrays.fill(secret, (byte) 9);
        assertThat(reactive.apply(reactiveRequest("Basic dXNlcjpwYXNz"))).isEqualTo(first);
        assertThat(blocking.apply(blockingRequest("Basic dXNlcjpwYXNz"))).isEqualTo(first);
        assertThat(new DefaultCacheKeyMapper(secret).apply(reactiveRequest("Basic dXNlcjpwYXNz")))
                .isNotEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16, 31})
    void rejectsShortFingerprintSecrets(int length) {
        assertThatThrownBy(() -> new DefaultCacheKeyMapper(new byte[length]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultHttpRequestCacheKeyMapper(new byte[length]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String reactiveKey(String authorization) {
        return new DefaultCacheKeyMapper().apply(reactiveRequest(authorization));
    }

    private static ClientRequest reactiveRequest(String authorization) {
        var builder = ClientRequest.create(HttpMethod.GET, URI_VALUE);
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return builder.build();
    }

    private static String blockingKey(String authorization) {
        return new DefaultHttpRequestCacheKeyMapper().apply(blockingRequest(authorization));
    }

    private static Request blockingRequest(String authorization) {
        var request = new Request();
        if (authorization != null) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    @Getter
    private static final class Request implements HttpRequest {
        private final HttpMethod method = HttpMethod.GET;
        private final URI uri = URI_VALUE;
        private final HttpHeaders headers = new HttpHeaders();
        private final Map<String, Object> attributes = Map.of();

        @Override
        public URI getURI() {
            return uri;
        }
    }
}
