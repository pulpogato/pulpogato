package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class OrgsApiIntegrationTest extends BaseIntegrationTest {
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
}
