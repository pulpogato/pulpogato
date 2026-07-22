package io.github.pulpogato.common.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

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
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    JwtTokenCache(JwtFactory jwtFactory, Clock clock) {
        this.jwtFactory = jwtFactory;
        this.clock = clock;
    }

    String getOrGenerateJwt() {
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
            log.debug("Generated new JWT token that expires at {}: '...{}'", tokenExpiresAt, maskedToken);
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
