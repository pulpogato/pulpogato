package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.schemas.AppPermissions;
import org.junit.jupiter.api.Test;

class OrgsApiIntegrationTest extends BaseApiIntegrationTest {
    @Test
    void testListInstallations() {
        var api = new RestClients(webClient).getOrgsApi();
        var response = api.listAppInstallations("corp", 100L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        var installations = response.getBody();
        assertThat(installations.getInstallations()).hasSize(1);
        var installation = installations.getInstallations().getFirst();
        assertThat(installation.getAccount()).isNotNull();
        assertThat(installation.getAccount().getValue().getSimpleUser().getLogin())
                .isEqualTo("pulpogato");
        assertThat(installation.getAppSlug()).isEqualTo("renovate");
    }

    // https://github.com/github/rest-api-description/issues/6272
    @Test
    void testListInstallationsWithOrganizationCopilotSeatManagementRead() {
        var api = new RestClients(webClient).getOrgsApi();
        var response = api.listAppInstallations("corp", 100L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        var installation = response.getBody().getInstallations().getFirst();
        assertThat(installation.getPermissions().getOrganizationCopilotSeatManagement())
                .isEqualTo(AppPermissions.OrganizationCopilotSeatManagement.READ);
    }
}
