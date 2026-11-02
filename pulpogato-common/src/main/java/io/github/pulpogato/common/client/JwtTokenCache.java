package io.github.pulpogato.common.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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

    private final AtomicReference<@Nullable CachedToken> cachedToken = new AtomicReference<>();

    JwtTokenCache(JwtFactory jwtFactory, Clock clock) {
        this.jwtFactory = jwtFactory;
        this.clock = clock;
    }

    String getOrGenerateJwt() {
        var current = cachedToken.get();
        if (current != null && current.isValidAt(clock.instant())) {
            return current.value();
        }

        lock.lock();
        try {
            var generateTime = clock.instant();
            current = cachedToken.get();
            if (current != null && current.isValidAt(generateTime)) {
                return current.value();
            }

            var expiresAt = generateTime.plus(TOKEN_VALIDITY);
            var validToken = Objects.requireNonNull(jwtFactory.create(generateTime.minus(CLOCK_DRIFT), expiresAt));
            // Readers must never pair the previous token with a replacement token's expiry.
            cachedToken.set(new CachedToken(validToken, expiresAt));
            var maskedToken = validToken.substring(Math.max(0, validToken.length() - 8));
            log.debug("Generated new JWT token that expires at {}: '...{}'", expiresAt, maskedToken);
            return validToken;
        } finally {
            lock.unlock();
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValidAt(Instant checkTime) {
            return checkTime.isBefore(expiresAt.minus(REFRESH_BUFFER));
        }
    }
}
