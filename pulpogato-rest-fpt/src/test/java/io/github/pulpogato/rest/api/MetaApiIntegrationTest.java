package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.schemas.ApiOverview;
import io.github.pulpogato.rest.schemas.Root;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class MetaApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testRoot() {
        var api = new RestClients(webClient).getMetaApi();
        var response = api.root();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(Root.class);

        var root = response.getBody();
        assertThat(root.getCurrentUserUrl()).isEqualTo("https://api.github.com/user");
        assertThat(root.getEmojisUrl()).isEqualTo("https://api.github.com/emojis");
        assertThat(root.getEventsUrl()).isEqualTo("https://api.github.com/events");
    }

    @Test
    void testGet() {
        var api = new RestClients(webClient).getMetaApi();
        var response = api.get();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(ApiOverview.class);

        var meta = response.getBody();
        assertThat(meta.getVerifiablePasswordAuthentication()).isFalse();
        assertThat(meta.getSshKeyFingerprints()).isNotNull();
        assertThat(meta.getSshKeys()).hasSize(3);
        assertThat(meta.getHooks()).hasSize(6);
        assertThat(meta.getGit()).hasSize(36);
    }
}
