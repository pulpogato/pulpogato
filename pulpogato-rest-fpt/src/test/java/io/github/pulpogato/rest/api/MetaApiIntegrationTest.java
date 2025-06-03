package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.ApiOverview;
import io.github.pulpogato.rest.schemas.Root;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetaApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testRoot() {
        MetaApi api = factory.createClient(MetaApi.class);
        ResponseEntity<Root> response = api.root();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(Root.class);
        
        Root root = response.getBody();
        assertThat(root.getCurrentUserUrl())
                .isNotNull()
                .contains("/user");
        assertThat(root.getEmojisUrl())
                .isNotNull()
                .endsWith("/emojis");
        assertThat(root.getEventsUrl())
                .isNotNull()
                .endsWith("/events");
    }

    @Test
    void testGet() {
        MetaApi api = factory.createClient(MetaApi.class);
        ResponseEntity<ApiOverview> response = api.get();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ApiOverview.class);
        
        ApiOverview meta = response.getBody();
        assertThat(meta.getVerifiablePasswordAuthentication()).isNotNull();
        assertThat(meta.getSshKeyFingerprints()).isNotNull();
        assertThat(meta.getSshKeys()).isNotNull();
        assertThat(meta.getHooks()).isNotNull();
        assertThat(meta.getGit()).isNotNull();
    }


}