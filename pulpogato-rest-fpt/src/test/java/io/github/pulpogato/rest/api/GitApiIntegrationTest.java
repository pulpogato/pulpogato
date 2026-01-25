package io.github.pulpogato.rest.api;

import static io.github.pulpogato.rest.api.GitApi.CreateTreeRequestBody.Tree;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.test.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GitApiIntegrationTest extends BaseIntegrationTest {
    /**
     * Tests creating a tree that moves a file by:
     * 1. Adding a new entry with the existing blob SHA at a new path
     * 2. Deleting the old entry by setting sha to null
     *
     * This test reproduces the issue described in https://github.com/pulpogato/pulpogato/issues/742
     * where @JsonInclude(NON_NULL) prevents null sha values from being serialized,
     * causing the GitHub API to reject the request.
     *
     * Using live data from https://github.com/pulpogato/create-demo repository.
     */
    @Test
    void testCreateTreeWithFileMove() {
        var api = new RestClients(webClient).getGitApi();

        // Repository: pulpogato/create-demo
        // Current state: README.md exists at root
        var owner = "pulpogato";
        var repo = "create-demo";
        var baseTreeSha = "c16df50e4252a304c1b00418b3f86e94ceec62a6";
        var readmeBlobSha = "e0190bf9ca1fa03808d7c0cc4a04e3203d78f94e";

        var response = api.createTree(
                owner,
                repo,
                GitApi.CreateTreeRequestBody.builder()
                        .baseTree(baseTreeSha)
                        .tree(List.of(
                                // Add README.md at new location with existing blob SHA
                                Tree.builder()
                                        .mode(Tree.Mode._100644)
                                        .type(Tree.Type.BLOB)
                                        .path("docs/README.md")
                                        .sha(readmeBlobSha)
                                        .build(),
                                // Delete README.md from old location by setting sha to null
                                Tree.builder()
                                        .mode(Tree.Mode._100644)
                                        .type(Tree.Type.BLOB)
                                        .path("README.md")
                                        .sha(null) // null sha means delete it
                                        .build()))
                        .build());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSha()).isNotNull();
    }
}
