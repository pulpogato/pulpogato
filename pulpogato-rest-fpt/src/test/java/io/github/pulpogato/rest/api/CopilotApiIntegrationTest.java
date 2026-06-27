package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CopilotApiIntegrationTest extends BaseApiIntegrationTest {

    // Replace with an org and user who has a Copilot seat assigned via a team.
    private static final String ORG = "example";
    private static final String USER_WITH_TEAM_SEAT = "rahulsom";

    @Test
    void testGetCopilotSeatDetailsForUserWithDirectAssignment() {
        var api = new RestClients(webClient).getCopilotApi();
        var response = api.getCopilotSeatDetailsForUser(ORG, USER_WITH_TEAM_SEAT);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var seat = response.getBody();
        assertThat(seat).isNotNull();

        var assigningTeam = seat.getAssigningTeam();
        assertThat(assigningTeam).isNotNull();
        assertThat(assigningTeam.isNotSet()).isTrue();
    }
}
