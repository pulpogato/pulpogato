package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.ContentFile;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Base64;

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

    @Test
    void testGetCommit() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.getCommit("pulpogato", "pulpogato", 1L, 1L, "2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var commit = response.getBody();
        assertThat(commit.getSha()).isEqualTo("2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
    }

    @Test
    void testGetContentObject() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.getContentObject("pulpogato", "pulpogato", "README.adoc", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("README.adoc");
        assertThat(body.getType()).isEqualTo("file");
        assertThat(body.getPath()).isEqualTo("README.adoc");

        var content = body.getContent().replace("\n", "");
        assertThat(content).isNotNull();
        var decoded = new String(Base64.getDecoder().decode(content));
        assertThat(decoded).startsWith("= Pulpogato");
    }

    @Test
    void testGetContent() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.getContent("pulpogato", "pulpogato", "README.adoc", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getContentFile().getName()).isEqualTo("README.adoc");
        assertThat(body.getContentFile().getType()).isEqualTo(ContentFile.Type.FILE);
        assertThat(body.getContentFile().getPath()).isEqualTo("README.adoc");

        var content = body.getContentFile().getContent().replace("\n", "");
        assertThat(content).isNotNull();
        var decoded = new String(Base64.getDecoder().decode(content));
        assertThat(decoded).startsWith("= Pulpogato");
    }

    @Test
    void testGet() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.get("pulpogato", "pulpogato");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("pulpogato");
        assertThat(body.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(body.getOwner().getLogin()).isEqualTo("pulpogato");
    }

    @Test
    void testGetBranch() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.getBranch("pulpogato", "pulpogato", "main");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("main");
        assertThat(body.getIsProtected()).isTrue();
    }

    @Test
    void testGetBranchProtection() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.getBranchProtection("pulpogato", "pulpogato", "main");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getRequiredStatusChecks().getContexts()).containsExactly("jenkins/pulpogato");
    }

    @Test
    void testCreateRepositoryInOrg() {
        ReposApi api = factory.createClient(ReposApi.class);
        var response = api.createInOrg("pulpogato", ReposApi.CreateInOrgRequestBody.builder()
                .name("create-demo")
                .description("create demo")
                .homepage("https://github.com/pulpogato/create-demo")
                .build());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("create-demo");
        assertThat(body.getFullName()).isEqualTo("pulpogato/create-demo");
        assertThat(body.getOwner().getLogin()).isEqualTo("pulpogato");
    }
}
