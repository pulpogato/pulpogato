package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.common.cache.CachingExchangeFilterFunction;
import io.github.pulpogato.common.client.MetricsExchangeFunction;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

class DocumentationIntegrationTest {
    @Test
    void setupCache() {
        // Setup cache
        Cache cache = new ConcurrentMapCache("github-http-cache");

        // Setup client
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ...your token here...")
                .build();

        // tag::setup-cache[]
        var cachingFilter = CachingExchangeFilterFunction.builder()
                .cache(cache) // <1>
                .build();

        var cachingClient = webClient
                .mutate()
                .filter(cachingFilter) // <2>
                .build();
        // end::setup-cache[]

        assertThat(cachingClient).isNotNull();
    }

    @Test
    void advancedCache() {
        // Setup cache
        Cache cache = new ConcurrentMapCache("github-http-cache");

        // Setup client
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ...your token here...")
                .build();

        // tag::advanced-cache[]
        var cachingFilter = CachingExchangeFilterFunction.builder()
                .cache(cache)
                .cacheKeyMapper(request -> request.url().toASCIIString()) // <1>
                .maxCacheableSize(4 * 1024 * 1024) // <2>
                .alwaysRevalidate(true) // <3>
                .build();

        var cachingClient = webClient.mutate().filter(cachingFilter).build();
        // end::advanced-cache[]

        assertThat(cachingClient).isNotNull();
    }

    @Test
    void setupMetrics() {
        var meterRegistry = new SimpleMeterRegistry();

        // Setup client
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ...your token here...")
                .build();

        // tag::setup-metrics[]
        var metricsFilter = MetricsExchangeFunction.builder()
                .registry(meterRegistry) // <1>
                .build();

        var metricsClient = webClient.mutate().filter(metricsFilter).build();
        // end::setup-metrics[]
        assertThat(metricsClient).isNotNull();
    }

    @Test
    void advancedMetrics() {
        var meterRegistry = new SimpleMeterRegistry();

        // Setup client
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ...your token here...")
                .build();

        // tag::advanced-metrics[]
        var metricsFilter = MetricsExchangeFunction.builder()
                .registry(meterRegistry)
                .prefix("myapp.github.rateLimit") // <1>
                .defaultTags(List.of(Tag.of("env", "production"))) // <2>
                .build();

        var metricsClient = webClient.mutate().filter(metricsFilter).build();
        // end::advanced-metrics[]
        assertThat(metricsClient).isNotNull();
    }
}
