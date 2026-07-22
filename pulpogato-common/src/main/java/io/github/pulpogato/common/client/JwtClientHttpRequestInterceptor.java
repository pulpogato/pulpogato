package io.github.pulpogato.common.client;

import java.io.IOException;
import java.time.Clock;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A {@link ClientHttpRequestInterceptor} that adds a JWT token for GitHub App authentication.
 *
 * <p>This interceptor uses a {@link JwtFactory} to generate JSON Web Tokens and adds them to
 * every outgoing request as a Bearer token in the Authorization header.</p>
 *
 * <p>The generated JWT is cached and reused until it's within 30 seconds of expiry,
 * at which point a new token is generated. Tokens are valid for approximately 9 minutes.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * var jwtFactory = new JwtFactory(privateKeyPem, "12345");
 *
 * var jwtInterceptor = JwtClientHttpRequestInterceptor.builder()
 *     .jwtFactory(jwtFactory)
 *     .build();
 *
 * RestClient restClient = RestClient.builder()
 *     .baseUrl("https://api.github.com")
 *     .requestInterceptor(jwtInterceptor)
 *     .build();
 * }</pre>
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of {@link JwtFilter}.</p>
 *
 * @see JwtFactory
 * @see <a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app">GitHub JWT Documentation</a>
 */
@Builder
public class JwtClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final JwtFactory jwtFactory;

    @Builder.Default
    private final Clock clock = Clock.systemUTC();

    @Getter(lazy = true)
    private final JwtTokenCache tokenCache = new JwtTokenCache(jwtFactory, clock);

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        var jwt = getTokenCache().getOrGenerateJwt();
        request.getHeaders().set("Authorization", "Bearer " + jwt);
        return execution.execute(request, body);
    }
}
