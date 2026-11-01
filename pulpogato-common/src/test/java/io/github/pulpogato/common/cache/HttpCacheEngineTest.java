package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

class HttpCacheEngineTest {

    private static final String CACHE_KEY = "cache-key";
    private static final String URI = "https://api.github.com/repos/foo/bar";
    private static final long CURRENT_TIME = 1_000_000L;

    private final Cache cache = mock(Cache.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(CURRENT_TIME), ZoneId.of("UTC"));

    private HttpCacheEngine engine(boolean alwaysRevalidate) {
        return new HttpCacheEngine(cache, clock, ObservationRegistry.NOOP, 1024, alwaysRevalidate, "test");
    }

    @Nested
    @DisplayName("lookup")
    class LookupTests {

        @Test
        @DisplayName("returns null on a cache miss")
        void returnsNullOnMiss() {
            when(cache.get(CACHE_KEY, CachedResponse.class)).thenReturn(null);

            assertThat(engine(false).lookup(CACHE_KEY, URI, null)).isNull();
        }

        @Test
        @DisplayName("returns the fresh cached entry on a hit")
        void returnsFreshEntry() {
            var cached = new CachedResponse(new byte[0], Map.of(), "\"etag\"", null, 60, CURRENT_TIME);
            when(cache.get(CACHE_KEY, CachedResponse.class)).thenReturn(cached);

            assertThat(engine(false).lookup(CACHE_KEY, URI, null)).isSameAs(cached);
        }

        @Test
        @DisplayName("alwaysRevalidate forces a stale outcome even for a fresh entry")
        void alwaysRevalidateStillReturnsEntry() {
            var cached = new CachedResponse(new byte[0], Map.of(), "\"etag\"", null, 60, CURRENT_TIME);
            when(cache.get(CACHE_KEY, CachedResponse.class)).thenReturn(cached);

            // The entry is still returned to the caller for conditional revalidation; only the
            // internal HIT/STALE observation tag is affected, which is not observable here.
            assertThat(engine(true).lookup(CACHE_KEY, URI, null)).isSameAs(cached);
        }
    }

    @Nested
    @DisplayName("refreshFromNotModified")
    class RefreshFromNotModifiedTests {

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void evictsWhenNotModifiedForbidsStorage(boolean multipleHeaderValues) {
            var stored = Map.of("Cache-Control", List.of("max-age=60"));
            var cached = new CachedResponse(new byte[] {1}, stored, "\"etag\"", null, 60, CURRENT_TIME);
            var values = multipleHeaderValues ? List.of("max-age=120", "no-store") : List.of("no-store");

            var refreshed =
                    engine(false).refreshFromNotModified(CACHE_KEY, URI, cached, Map.of("cache-control", values), null);

            verify(cache).evict(CACHE_KEY);
            assertThat(refreshed.getBody()).containsExactly((byte) 1);
            assertThat(refreshed.isExpired(CURRENT_TIME)).isTrue();
        }

        @Test
        @DisplayName("merges 304 headers over stored headers case-insensitively")
        void mergesHeadersCaseInsensitively() {
            var stored = Map.of(
                    "ETag", List.of("\"old\""),
                    "Content-Type", List.of("application/json"));
            var cached = new CachedResponse(new byte[] {1, 2, 3}, stored, "\"old\"", null, 60, CURRENT_TIME - 10_000);

            var notModifiedHeaders = Map.of(
                    "etag", List.of("\"new\""),
                    "content-length", List.of("0"));

            var refreshed = engine(false).refreshFromNotModified(CACHE_KEY, URI, cached, notModifiedHeaders, null);

            assertThat(refreshed.getEtag()).isEqualTo("\"new\"");
            assertThat(refreshed.getHeaders()).containsEntry("Content-Type", List.of("application/json"));
            // Content-Length must never be taken from a 304, which does not carry a body.
            assertThat(refreshed.getHeaders()).doesNotContainKey("content-length");
            assertThat(refreshed.getHeaders()).doesNotContainKey("Content-Length");
        }

        @Test
        @DisplayName("falls back to the stored ETag/Last-Modified/max-age when the 304 omits them")
        void fallsBackToStoredMetadataWhenOmitted() {
            var stored = Map.of("Content-Type", List.of("application/json"));
            var cached = new CachedResponse(
                    new byte[0], stored, "\"stored-etag\"", "stored-last-modified", 60, CURRENT_TIME);

            var refreshed = engine(false).refreshFromNotModified(CACHE_KEY, URI, cached, Map.of(), null);

            assertThat(refreshed.getEtag()).isEqualTo("\"stored-etag\"");
            assertThat(refreshed.getLastModified()).isEqualTo("stored-last-modified");
            assertThat(refreshed.getMaxAgeSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("restarts the freshness lifetime from now and stores the refreshed entry")
        void restartsFreshnessAndStores() {
            var cached = new CachedResponse(new byte[0], Map.of(), "\"etag\"", null, 60, CURRENT_TIME - 100_000);

            var refreshed = engine(false).refreshFromNotModified(CACHE_KEY, URI, cached, Map.of(), null);

            assertThat(refreshed.getCachedAtMillis()).isEqualTo(CURRENT_TIME);
            verify(cache).put(CACHE_KEY, refreshed);
        }
    }

    @Nested
    @DisplayName("shouldCache")
    class ShouldCacheTests {

        @Test
        @DisplayName("requires at least one caching header")
        void requiresCachingHeader() {
            assertThat(engine(false).shouldCache(null, null, -1, 10)).isFalse();
            assertThat(engine(false).shouldCache("\"etag\"", null, -1, 10)).isTrue();
            assertThat(engine(false).shouldCache(null, "last-modified", -1, 10)).isTrue();
            assertThat(engine(false).shouldCache(null, null, 60, 10)).isTrue();
        }

        @Test
        @DisplayName("rejects content over maxCacheableSize")
        void rejectsOversizedContent() {
            assertThat(engine(false).shouldCache("\"etag\"", null, -1, 2048)).isFalse();
        }

        @Test
        @DisplayName("identifies why a response is not cacheable")
        void identifiesSkipReason() {
            assertThat(engine(false).skipReason(null, null, -1, 10)).isEqualTo(HttpCacheEngine.SKIP_NO_CACHE_HEADERS);
            assertThat(engine(false).skipReason("\"etag\"", null, -1, 2048))
                    .isEqualTo(HttpCacheEngine.SKIP_CONTENT_LENGTH);
            assertThat(engine(false).skipReason("\"etag\"", null, -1, 10)).isNull();
        }
    }

    @Nested
    @DisplayName("exceedsMaxCacheableSize")
    class ExceedsMaxCacheableSizeTests {

        @Test
        void exceedsWhenOverLimit() {
            assertThat(engine(false).exceedsMaxCacheableSize(2048)).isTrue();
            assertThat(engine(false).exceedsMaxCacheableSize(1024)).isFalse();
        }
    }

    @Nested
    @DisplayName("metrics")
    class MetricsTests {

        @Test
        @DisplayName("emits timers with low-cardinality dimensions")
        void emitsMeterBackedObservations() {
            var meterRegistry = new SimpleMeterRegistry();
            var observationRegistry = ObservationRegistry.create();
            observationRegistry
                    .observationConfig()
                    .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
            var namedCache = new ConcurrentMapCache("github-http-cache");
            var engine = new HttpCacheEngine(namedCache, clock, observationRegistry, 1024, false, "WebClient");

            engine.lookup(CACHE_KEY, URI, null);
            engine.recordSkip(CACHE_KEY, URI, HttpCacheEngine.SKIP_NO_CACHE_HEADERS, null);

            var getTimer = meterRegistry
                    .get("pulpogato.cache.get")
                    .tags(
                            "cache.status", "MISS",
                            "cache.name", "github-http-cache",
                            "cache.client", "WebClient",
                            "server.address", "api.github.com")
                    .timer();
            var putTimer = meterRegistry
                    .get("pulpogato.cache.put")
                    .tags(
                            "cache.status", "SKIP",
                            "cache.skip.reason", "NO_CACHE_HEADERS",
                            "cache.name", "github-http-cache",
                            "cache.client", "WebClient",
                            "server.address", "api.github.com")
                    .timer();

            assertThat(getTimer.count()).isEqualTo(1);
            assertThat(putTimer.count()).isEqualTo(1);
            assertThat(getTimer.getId().getTag("uri")).isNull();
            assertThat(getTimer.getId().getTag("cache.key")).isNull();
            meterRegistry.close();
        }
    }

    @Nested
    @DisplayName("parseMaxAge")
    class ParseMaxAgeTests {

        @ParameterizedTest
        @CsvSource({
            "'MAX-AGE = 120', 120",
            "'s-maxage=120', -1",
            "'max-age=99999999999999999999999', -1",
            "'max-age=120junk', -1",
            "'max-age=\"120\"', 120",
            "'max-age=\"120', -1",
            "'max-age=120\"', -1",
            "'extension=\"x,max-age=120,y\"', -1",
            "'extension=\"x,max-age=120,y\", max-age=60', 60"
        })
        void parsesOnlyValidFreshness(String value, long expected) {
            assertThat(HttpCacheEngine.parseMaxAge(value)).isEqualTo(expected);
        }

        @Test
        void parsesMaxAgeDirective() {
            assertThat(HttpCacheEngine.parseMaxAge("max-age=120, must-revalidate"))
                    .isEqualTo(120);
        }

        @Test
        void returnsMinusOneWhenMissing() {
            assertThat(HttpCacheEngine.parseMaxAge("no-cache")).isEqualTo(-1);
            assertThat(HttpCacheEngine.parseMaxAge(null)).isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "max-age=60, extension=\"x,no-store,y\"",
                "extension=\"x,no-cache,y\", max-age=60",
                "extension=\"x,\\\"no-store,y\", max-age=60"
            })
    void ignoresDirectiveNamesInsideQuotedExtensions(String control) {
        assertThat(HttpCacheEngine.hasDirective(control, "no-store")).isFalse();
        assertThat(HttpCacheEngine.hasDirective(control, "no-cache")).isFalse();
        assertThat(HttpCacheEngine.parseMaxAge(control)).isEqualTo(60);
        assertThat(HttpCacheEngine.hasDirective(control + ", no-store", "no-store"))
                .isTrue();
    }
}
