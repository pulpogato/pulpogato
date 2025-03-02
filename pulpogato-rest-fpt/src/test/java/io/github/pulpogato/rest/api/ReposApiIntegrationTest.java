package io.github.pulpogato.rest.api;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReposApiIntegrationTest extends BaseIntegrationTest {
    @Test
    void testListTags() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.listTags("pulpogato", "pulpogato", 100L, 1L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isNotEmpty();
        var tags = response.getBody();
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).getName()).isEqualTo("v0.2.0");
        assertThat(tags.get(1).getName()).isEqualTo("v0.1.0");
    }
}
