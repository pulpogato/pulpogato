package io.github.pulpogato.common.client;

import java.io.IOException;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A {@link ClientHttpRequestInterceptor} that adds default headers to every outgoing HTTP
 * request, specifically for use with GitHub APIs or similar integrations.
 *
 * <p>The following headers are added to each request:
 *
 * <ul>
 *   <li>"X-GitHub-Api-Version": specifies the version of the GitHub API being targeted.
 *   <li>"X-Pulpogato-Version": indicates the version of the Pulpogato client.
 * </ul>
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of
 * {@link DefaultHeadersExchangeFunction}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultHeadersClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final Map<String, String> headers;

    /**
     * Creates a new instance with default headers loaded from {@code pulpogato-headers.properties}.
     * The properties file is loaded from the classpath and provides values for
     * {@code pulpogato.version} and {@code github.api.version}.
     */
    public DefaultHeadersClientHttpRequestInterceptor() {
        this(PulpogatoHeadersLoader.loadHeaders());
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        headers.forEach((name, value) -> request.getHeaders().set(name, value));
        return execution.execute(request, body);
    }
}
