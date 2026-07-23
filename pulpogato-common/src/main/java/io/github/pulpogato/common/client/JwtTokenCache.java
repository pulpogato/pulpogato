package io.github.pulpogato.common.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Framework-agnostic JWT caching shared by {@link JwtFilter} and
 * {@link JwtClientHttpRequestInterceptor}: generates a JSON Web Token via a {@link JwtFactory} and
 * reuses it until it's within {@link #REFRESH_BUFFER} of expiry, at which point a new token is
 * generated. Tokens are valid for approximately 9 minutes.
 */
@Slf4j
class JwtTokenCache {

    private static final Duration CLOCK_DRIFT = Duration.ofSeconds(60);
    private static final Duration REFRESH_BUFFER = Duration.ofSeconds(30);
    private static final Duration TOKEN_VALIDITY = Duration.ofSeconds(540);

    private final JwtFactory jwtFactory;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();

    @Nullable
    private volatile String cachedToken;

    @Nullable
    private volatile Instant tokenExpiresAt;

    JwtTokenCache(JwtFactory jwtFactory, Clock clock) {
        this.jwtFactory = jwtFactory;
        this.clock = clock;
    }

    String getOrGenerateJwt() {
        var checkTime = clock.instant();
        if (isCachedTokenValid(checkTime)) {
            return Objects.requireNonNull(cachedToken);
        }

        lock.lock();
        try {
            // Double-check after acquiring lock
            var generateTime = clock.instant();
            if (isCachedTokenValid(generateTime)) {
                return Objects.requireNonNull(cachedToken);
            }

            tokenExpiresAt = generateTime.plus(TOKEN_VALIDITY);
            cachedToken = jwtFactory.create(generateTime.minus(CLOCK_DRIFT), Objects.requireNonNull(tokenExpiresAt));
            String validToken = Objects.requireNonNull(cachedToken);
            String maskedToken = validToken.substring(Math.max(0, validToken.length() - 8));
            log.debug("Generated new JWT token that expires at {}: '...{}'", tokenExpiresAt, maskedToken);
            return validToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isCachedTokenValid(Instant checkTime) {
        if (cachedToken == null || tokenExpiresAt == null) {
            return false;
        }
        var expiry = Objects.requireNonNull(tokenExpiresAt);
        return checkTime.isBefore(expiry.minus(REFRESH_BUFFER));
    }
}
