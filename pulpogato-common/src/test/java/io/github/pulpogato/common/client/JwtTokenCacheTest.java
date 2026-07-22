package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JwtTokenCacheTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T00:00:00Z");

    private final JwtFactory jwtFactory = mock(JwtFactory.class);

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void advanceBy(java.time.Duration duration) {
            instant.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @Test
    void generatesTokenOnFirstCall() {
        when(jwtFactory.create(FIXED_INSTANT.minusSeconds(60), FIXED_INSTANT.plusSeconds(540)))
                .thenReturn("token-1");

        var cache = new JwtTokenCache(jwtFactory, Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");
    }

    @Test
    void cachesTokenBetweenCalls() {
        when(jwtFactory.create(FIXED_INSTANT.minusSeconds(60), FIXED_INSTANT.plusSeconds(540)))
                .thenReturn("token-1");

        var cache = new JwtTokenCache(jwtFactory, Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");
        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");
        verify(jwtFactory).create(FIXED_INSTANT.minusSeconds(60), FIXED_INSTANT.plusSeconds(540));
    }

    @Test
    void refreshesTokenWhenWithinRefreshBufferOfExpiry() {
        var clock = new MutableClock(FIXED_INSTANT);
        when(jwtFactory.create(FIXED_INSTANT.minusSeconds(60), FIXED_INSTANT.plusSeconds(540)))
                .thenReturn("token-1");

        var cache = new JwtTokenCache(jwtFactory, clock);
        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");

        // Advance to within the 30s refresh buffer of the 540s expiry.
        clock.advanceBy(java.time.Duration.ofSeconds(515));
        var refreshedAt = FIXED_INSTANT.plusSeconds(515);
        when(jwtFactory.create(refreshedAt.minusSeconds(60), refreshedAt.plusSeconds(540)))
                .thenReturn("token-2");

        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-2");
    }

    @Test
    void doesNotRefreshBeforeRefreshBuffer() {
        var clock = new MutableClock(FIXED_INSTANT);
        when(jwtFactory.create(FIXED_INSTANT.minusSeconds(60), FIXED_INSTANT.plusSeconds(540)))
                .thenReturn("token-1");

        var cache = new JwtTokenCache(jwtFactory, clock);
        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");

        // Still well outside the 30s refresh buffer of the 540s expiry.
        clock.advanceBy(java.time.Duration.ofSeconds(400));

        assertThat(cache.getOrGenerateJwt()).isEqualTo("token-1");
    }
}
