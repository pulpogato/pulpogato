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

    @Test
    void testRequestReviewers() {
        var api = new RestClients(webClient).getPullsApi();
        var response = api.requestReviewers(
                "corp", "cisys-jenkins-bom", 168L,
                PullsApi.RequestReviewersRequestBody.builder()
                        .reviewers(List.of("sghill", "egoh"))
                        .teamReviewers(List.of("team-ascii"))
                        .build());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();

        var pr = response.getBody();
        assertThat(pr.getNumber()).isEqualTo(168);
    }

}