package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.test.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullsApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetActionsCacheUsage() {
        var api = new RestClients(webClient).getPullsApi();
        var response = api.list(
                "example",
                "cisys-jenkins-bom",
                PullsApi.ListState.ALL,
                "johnburns:NEBULA-3674",
                null,
                null,
                null,
                null,
                null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var pulls = response.getBody();

        assertThat(pulls).hasSize(1);

        var pr = pulls.getFirst();
        assertThat(pr.getNumber()).isEqualTo(170);
    }

    @Test
    void testRequestReviewers() {
        var api = new RestClients(webClient).getPullsApi();
        var response = api.requestReviewers(
                "corp",
                "cisys-jenkins-bom",
                168L,
                PullsApi.RequestReviewersRequestBody.builder()
                        .reviewers(List.of("sghill", "egoh"))
                        .teamReviewers(List.of("team-ascii"))
                        .build());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        var pr = response.getBody();
        assertThat(pr.getNumber()).isEqualTo(168);
    }

    @Test
    void testGetDiff() {
        var api = new RestClients(webClient).getPullsApi();
        var response = api.getDiff("pulpogato", "pulpogato", 988L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isNotEmpty();

        var diff = response.getBody();
        assertThat(diff).contains("diff --git ").contains("@@");
    }

    @Test
    void testGetPatch() {
        var api = new RestClients(webClient).getPullsApi();
        var response = api.getPatch("pulpogato", "pulpogato", 988L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isNotEmpty();

        var patch = response.getBody();
        assertThat(patch).contains("diff --git ").contains("index ");
    }
}
