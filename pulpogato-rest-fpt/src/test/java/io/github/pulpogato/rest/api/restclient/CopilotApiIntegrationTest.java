package io.github.pulpogato.rest.api.restclient;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import org.junit.jupiter.api.Test;

class CopilotApiIntegrationTest extends BaseApiIntegrationTest {

    // Replace with an org and user whose Copilot seat is assigned directly, not via a team.
    private static final String ORG = "example";
    private static final String USER_WITH_DIRECT_SEAT = "rahulsom";

    @Test
    void testGetCopilotSeatDetailsForUserWithDirectAssignment() {
        var api = new RestClients(restClient).getCopilotApi();
        var response = api.getCopilotSeatDetailsForUser(ORG, USER_WITH_DIRECT_SEAT);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var seat = response.getBody();
        assertThat(seat).isNotNull();

        var assigningTeam = seat.getAssigningTeam();
        assertThat(assigningTeam).isNotNull();
        assertThat(assigningTeam.isNotSet()).isTrue();
    }
}
