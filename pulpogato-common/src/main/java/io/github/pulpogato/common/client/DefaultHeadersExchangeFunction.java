package io.github.pulpogato.common.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
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

    private final String pulpogatoVersion;
    private final String githubApiVersion;

    public DefaultHeadersExchangeFunction() {
        this(loadProperty("pulpogato.version"), loadProperty("github.api.version"));
    }

    private static String loadProperty(String key) {
        try (InputStream input = new ClassPathResource("pulpogato-headers.properties").getInputStream()) {
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty(key, null);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        ClientRequest.Builder builder = ClientRequest.from(request);
        if (githubApiVersion != null) {
            builder.header("X-GitHub-Api-Version", githubApiVersion);
        }
        if (pulpogatoVersion != null) {
            builder.header("X-Pulpogato-Version", pulpogatoVersion);
        }
        return next.exchange(builder.build());
    }
}
