package io.github.pulpogato.rest.api.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.common.StringOrInteger;
import io.github.pulpogato.common.cache.CachingExchangeFilterFunction;
import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import io.github.pulpogato.rest.schemas.ActionsCacheList;
import io.github.pulpogato.rest.schemas.ActionsCacheUsageByRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpStatus;

class ActionsApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGetActionsCacheUsage() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheUsage("pulpogato", "pulpogato").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(ActionsCacheUsageByRepository.class);

        var cacheUsage = response.getBody();
        assertThat(cacheUsage.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(cacheUsage.getActiveCachesSizeInBytes()).isEqualTo(9664500682L);
        assertThat(cacheUsage.getActiveCachesCount()).isEqualTo(60);
    }

    @Test
    void testGetActionsCacheList() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheList("pulpogato", "pulpogato", null, null, null, null, null, null)
                .block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(ActionsCacheList.class);

        var cacheList = response.getBody();
        assertThat(cacheList.getTotalCount()).isEqualTo(60);
        assertThat(cacheList.getActionsCaches()).isNotNull().hasSize(30);
    }

    @Test
    void testGetActionsCacheListWithPagination() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheList("pulpogato", "pulpogato", 10L, 0L, null, null, null, null)
                .block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(ActionsCacheList.class);

        var cacheList = response.getBody();
        assertThat(cacheList.getTotalCount()).isEqualTo(60);
        assertThat(cacheList.getActionsCaches()).isNotNull();

        // Verify pagination works
        assertThat(cacheList.getActionsCaches()).hasSize(10);
    }

    @Test
    void testCreateWorkflowDispatch() {
        RestClients restClients = new RestClients(webClient);
        // Next statement was for testing if converters can be added in user-land.
        // restClients.getConversionService().addConverter(new StringOrInteger.StringConverter());
        var api = restClients.getActionsApi();

        var response = api.createWorkflowDispatch(
                        "pulpogato",
                        "pulpogato",
                        StringOrInteger.builder()
                                .stringValue("check-issues-statuses.yml")
                                .build(),
                        ActionsApi.CreateWorkflowDispatchRequestBody.builder()
                                .ref("main")
                                .build())
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var body = response.getBody();
        assertThat(body).isNull();
    }

    @Test
    void testCreateWorkflowDispatchWithResponse() {
        RestClients restClients = new RestClients(webClient);
        // Next statement was for testing if converters can be added in user-land.
        // restClients.getConversionService().addConverter(new StringOrInteger.StringConverter());
        var api = restClients.getActionsApi();

        var response = api.createWorkflowDispatch(
                        "pulpogato",
                        "pulpogato",
                        StringOrInteger.builder()
                                .stringValue("check-issues-statuses.yml")
                                .build(),
                        ActionsApi.CreateWorkflowDispatchRequestBody.builder()
                                .ref("main")
                                .returnRunDetails(true)
                                .build())
                .block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getWorkflowRunId()).isEqualTo(22420419990L);
        assertThat(body.getRunUrl())
                .isEqualTo(URI.create("https://api.github.com/repos/pulpogato/pulpogato/actions/runs/22420419990"));
        assertThat(body.getHtmlUrl())
                .isEqualTo(URI.create("https://github.com/pulpogato/pulpogato/actions/runs/22420419990"));
    }

    @Test
    void testListWorkflowRuns() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.listWorkflowRuns(
                        "pulpogato",
                        "pulpogato",
                        StringOrInteger.builder()
                                .stringValue("check-issues-statuses.yml")
                                .build(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)
                .block();
        assertThat(response.getBody())
                .as("Workflow runs response should not be null")
                .isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testListWorkflowRunsWithCache() {
        var cachingClient = webClient
                .mutate()
                .filter(CachingExchangeFilterFunction.builder()
                        .cache(new ConcurrentMapCache("test-cache"))
                        .build())
                .build();
        var api = new RestClients(cachingClient).getActionsApi();
        var response = api.listWorkflowRuns(
                        "pulpogato",
                        "pulpogato",
                        StringOrInteger.builder()
                                .stringValue("check-issues-statuses.yml")
                                .build(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)
                .block();
        assertThat(response.getBody())
                .as("Workflow runs response should not be null")
                .isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
