package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.PullRequestSimple;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PullsApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetActionsCacheUsage() {
        PullsApi api = new RestClients(webClient).getPullsApi();
        var response = api.list(
                "example", "cisys-jenkins-bom", PullsApi.ListState.ALL, "johnburns:NEBULA-3674",
            null, null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);

        List<PullRequestSimple> pulls = response.getBody();

        assertThat(pulls).hasSize(1);

        PullRequestSimple pr = pulls.getFirst();
        assertThat(pr.getNumber()).isEqualTo(170);
    }

}