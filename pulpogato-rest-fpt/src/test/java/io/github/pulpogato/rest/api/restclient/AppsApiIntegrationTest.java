package io.github.pulpogato.rest.api.restclient;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import io.github.pulpogato.rest.schemas.IntegrationOwner;
import io.github.pulpogato.rest.schemas.SimpleUser;
import org.junit.jupiter.api.Test;

class AppsApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGetBySlugResolvesOwnerToSimpleUser() {
        var api = new RestClients(restClient).getAppsApi();
        // GET /apps/{app_slug} is a public endpoint; renovate is a well-known GitHub App
        // owned by mend (a user account), so the owner field always resolves to SimpleUser.
        var response = api.getBySlug("renovate");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var integration = response.getBody();
        assertThat(integration).isNotNull();
        assertThat(integration.getSlug()).isEqualTo("renovate");

        IntegrationOwner owner = integration.getOwner();
        assertThat(owner).isNotNull().isInstanceOf(SimpleUser.class);
        var simpleUser = (SimpleUser) owner;
        assertThat(simpleUser.getLogin()).isEqualTo("mend");
        assertThat(simpleUser.getId()).isEqualTo(105765982L);
    }
}
