package io.github.pulpogato.rest.api;

import io.github.pulpogato.common.StringOrInteger;
import io.github.pulpogato.rest.schemas.ActionsCacheUsageByRepository;
import io.github.pulpogato.rest.schemas.ActionsCacheList;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ActionsApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetActionsCacheUsage() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheUsage("pulpogato", "pulpogato");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheUsageByRepository.class);

        var cacheUsage = response.getBody();
        assertThat(cacheUsage.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(cacheUsage.getActiveCachesSizeInBytes()).isEqualTo(9664500682L);
        assertThat(cacheUsage.getActiveCachesCount()).isEqualTo(60);
    }

    @Test
    void testGetActionsCacheList() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheList("pulpogato", "pulpogato", null, null, null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheList.class);

        var cacheList = response.getBody();
        assertThat(cacheList.getTotalCount()).isEqualTo(60);
        assertThat(cacheList.getActionsCaches()).isNotNull().hasSize(30);
    }

    @Test
    void testGetActionsCacheListWithPagination() {
        var api = new RestClients(webClient).getActionsApi();
        var response = api.getActionsCacheList("pulpogato", "pulpogato", 10L, 0L, null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheList.class);

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
                StringOrInteger.builder().stringValue("check-issues-statuses.yml").build(),
                ActionsApi.CreateWorkflowDispatchRequestBody.builder().ref("main").build()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

}