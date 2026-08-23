package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class ReposApiIntegrationTest extends BaseIntegrationTest {

    /**
     * Guards the {@code repos.update.allowAutoMerge} schema addition, which exists because the GHES
     * descriptions omit {@code allow_auto_merge} from the repos/update request body even though the
     * server accepts it: <a href="https://github.com/github/rest-api-description/issues/6902">#6902</a>
     *
     * <p>The tape matches on the serialized request body, so if the addition is dropped before the
     * upstream schema carries the property, {@code allowAutoMerge} silently disappears from the
     * request, the recorded exchange stops matching, and this test fails.
     */
    @Test
    void testUpdateAllowAutoMerge() {
        ReposApi api = new RestClients(webClient).getReposApi();

        var response = api.update(
                "octocat",
                "Hello-World",
                ReposApi.UpdateRequestBody.builder().allowAutoMerge(true).build());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var repository = response.getBody();

        assertThat(repository.getFullName()).isEqualTo("octocat/Hello-World");
        assertThat(repository.getAllowAutoMerge()).isTrue();
    }
}
