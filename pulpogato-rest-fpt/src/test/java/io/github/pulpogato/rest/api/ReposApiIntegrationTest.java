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

    @Test
    void testListCommits() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.listCommits("pulpogato", "pulpogato",
                null, null, null, null, null, null, 10L, 1L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isNotEmpty();
        var commits = response.getBody();
        assertThat(commits).hasSize(10);
        var commit = commits.getFirst();
        assertThat(commit.getSha()).isEqualTo("2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
        assertThat(commit.getCommit().getMessage()).isEqualTo("Merge pull request #206 from pulpogato/test-listOrgApps\n\ntest: Add test for listAppInstallations in an org");
        assertThat(commit.getCommitter().getSimpleUser().getLogin()).isEqualTo("web-flow");
        assertThat(commit.getAuthor().getSimpleUser().getLogin()).isEqualTo("rahulsom");
    }
}
