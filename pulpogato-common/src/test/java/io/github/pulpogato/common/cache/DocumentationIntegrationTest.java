package io.github.pulpogato.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

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
        // Add it to your WebClient
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ...your token here...")
                .build();

        // tag::setup-cache[]
        var cachingFilter = CachingExchangeFilterFunction.builder()
                .cache(cache) // <1>
                .build();

        var cachingClient = webClient.mutate().filter(cachingFilter).build();
        // end::setup-cache[]

        assertThat(cachingClient).isNotNull();
    }

    @Test
    void advancedCache() {
        // Setup cache
        Cache cache = new ConcurrentMapCache("github-http-cache");

        // Setup client
        // Add it to your WebClient
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
}
