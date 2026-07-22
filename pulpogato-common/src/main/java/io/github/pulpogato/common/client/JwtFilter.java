package io.github.pulpogato.common.client;

import java.time.Clock;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * An {@link ExchangeFilterFunction} that adds a JWT token for GitHub App authentication.
 *
 * <p>This filter uses a {@link JwtFactory} to generate JSON Web Tokens and adds them to every
 * outgoing request as a Bearer token in the Authorization header.</p>
 *
 * <p>The generated JWT is cached and reused until it's within 30 seconds of expiry,
 * at which point a new token is generated. Tokens are valid for approximately 9 minutes.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * var jwtFactory = new JwtFactory(privateKeyPem, "12345");
 *
 * var jwtFilter = JwtFilter.builder()
 *     .jwtFactory(jwtFactory)
 *     .build();
 *
 * WebClient webClient = WebClient.builder()
 *     .baseUrl("https://api.github.com")
 *     .filter(jwtFilter)
 *     .build();
 * }</pre>
 *
 * @see JwtFactory
 * @see <a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app">GitHub JWT Documentation</a>
 */
@Builder
public class JwtFilter implements ExchangeFilterFunction {

    private final JwtFactory jwtFactory;

    @Builder.Default
    private final Clock clock = Clock.systemUTC();

    @Getter(lazy = true)
    private final JwtTokenCache tokenCache = new JwtTokenCache(jwtFactory, clock);

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        var jwt = getTokenCache().getOrGenerateJwt();
        var newRequest = ClientRequest.from(request)
                .header("Authorization", "Bearer " + jwt)
                .build();
        return next.exchange(newRequest);
    }
}
