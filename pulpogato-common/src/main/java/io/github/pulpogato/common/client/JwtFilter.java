package io.github.pulpogato.common.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class JwtFilter implements ExchangeFilterFunction {

    private static final Duration CLOCK_DRIFT = Duration.ofSeconds(60);
    private static final Duration REFRESH_BUFFER = Duration.ofSeconds(30);
    private static final Duration TOKEN_VALIDITY = Duration.ofSeconds(540);

    private final JwtFactory jwtFactory;

    @Builder.Default
    private final Clock clock = Clock.systemUTC();

    private final ReentrantLock lock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        var jwt = getOrGenerateJwt();
        var newRequest = ClientRequest.from(request)
                .header("Authorization", "Bearer " + jwt)
                .build();
        return next.exchange(newRequest);
    }

    private String getOrGenerateJwt() {
        var checkTime = clock.instant();
        if (isCachedTokenValid(checkTime)) {
            return cachedToken;
        }

        lock.lock();
        try {
            // Double-check after acquiring lock
            var generateTime = clock.instant();
            if (isCachedTokenValid(generateTime)) {
                return cachedToken;
            }

            tokenExpiresAt = generateTime.plus(TOKEN_VALIDITY);
            cachedToken = jwtFactory.create(generateTime.minus(CLOCK_DRIFT), tokenExpiresAt);
            String maskedToken = cachedToken.substring(Math.max(0, cachedToken.length() - 8));
            log.info("Generated new JWT token that expires at {}: '...{}'", tokenExpiresAt, maskedToken);
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isCachedTokenValid(Instant checkTime) {
        return cachedToken != null
                && tokenExpiresAt != null
                && checkTime.isBefore(tokenExpiresAt.minus(REFRESH_BUFFER));
    }
}
