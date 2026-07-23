package io.github.pulpogato.common.client;

import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * <p>
 * An implementation of the {@link ExchangeFilterFunction} interface that adds default headers
 * to every outgoing HTTP request, specifically for use with GitHub APIs or similar integrations.
 * This class ensures consistency in client requests by setting predefined headers.
 * </p>
 * <p>
 * The following headers are added to each request:
 * </p>
 * <ul>
 *   <li> "X-GitHub-Api-Version": specifies the version of the GitHub API being targeted.
 *   <li> "X-Pulpogato-Version": indicates the version of the Pulpogato client.
 * </ul>
 * <p>
 * This functionality is particularly useful when interacting with APIs that require consistent
 * versioning or client information in the request headers.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultHeadersExchangeFunction implements ExchangeFilterFunction {

    private final Map<String, String> headers;

    /**
     * Creates a new instance with default headers loaded from {@code pulpogato-headers.properties}.
     * The properties file is loaded from the classpath and provides values for
     * {@code pulpogato.version} and {@code github.api.version}.
     */
    public DefaultHeadersExchangeFunction() {
        this(PulpogatoHeadersLoader.loadHeaders());
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        ClientRequest.Builder builder = ClientRequest.from(request);
        headers.forEach(builder::header);
        return next.exchange(builder.build());
    }
}
