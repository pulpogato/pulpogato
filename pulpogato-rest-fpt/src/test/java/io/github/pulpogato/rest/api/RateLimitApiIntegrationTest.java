package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.RateLimitOverview;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGet() {
        var api = new RestClients(webClient).getRateLimitApi();
        var response = api.get();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(RateLimitOverview.class);

        var rateLimitOverview = response.getBody();

        // Verify resources structure
        var resources = rateLimitOverview.getResources();
        
        // Verify core rate limit information
        assertThat(resources.getCore().getLimit()).isEqualTo(5000);
        assertThat(resources.getCore().getRemaining()).isEqualTo(4992);
        assertThat(resources.getCore().getReset()).isEqualTo(1749014024);
        assertThat(resources.getCore().getUsed()).isEqualTo(8);
        
        // Verify search rate limit information  
        assertThat(resources.getSearch().getLimit()).isEqualTo(30);
        assertThat(resources.getSearch().getRemaining()).isEqualTo(30);
        assertThat(resources.getSearch().getReset()).isEqualTo(1749011183);
        assertThat(resources.getSearch().getUsed()).isEqualTo(0);
        
        // Verify GraphQL rate limit information
        assertThat(resources.getGraphql().getLimit()).isEqualTo(5000);
        assertThat(resources.getGraphql().getRemaining()).isEqualTo(5000);
        assertThat(resources.getGraphql().getReset()).isEqualTo(1749014723);
        assertThat(resources.getGraphql().getUsed()).isEqualTo(0);
        
        // Verify the legacy rate object
        assertThat(rateLimitOverview.getRate().getLimit()).isEqualTo(5000);
        assertThat(rateLimitOverview.getRate().getRemaining()).isEqualTo(4992);
        assertThat(rateLimitOverview.getRate().getReset()).isEqualTo(1749014024);
    }
}