package io.github.pulpogato.graphql;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.client.WebClientGraphQLClient;
import io.github.pulpogato.common.util.LinkedHashMapBuilder;
import io.github.pulpogato.graphql.types.AutoMergeRequest;
import io.github.pulpogato.graphql.types.MergeableState;
import io.github.pulpogato.graphql.types.PullRequest;
import io.github.pulpogato.graphql.types.PullRequestMergeMethod;
import io.github.pulpogato.graphql.types.Repository;
import io.github.pulpogato.graphql.types.User;
import io.github.pulpogato.test.BaseIntegrationTest;
import java.net.URI;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

class FindOpenPullRequestsTest extends BaseIntegrationTest {
    // tag::query[]
    @Language("graphql")
    public static final String OPEN_PRS = """
        query findOpenPullRequests($owner: String!, $repo: String!, $branch: String!) {
          repository(owner: $owner, name: $repo, followRenames: false) {
            pullRequests(
                first: 100, states: [OPEN], baseRefName: $branch,
                orderBy: {field: UPDATED_AT, direction: DESC}
            ) {
              totalCount
              nodes {
                id
                number
                headRefName
                headRefOid
                baseRefOid
                mergeable
                author {
                  __typename
                  login
                }
                url
                autoMergeRequest {
                  enabledBy {
                    __typename
                    login
                  }
                  mergeMethod
                }
              }
            }
          }
        }
        """;
    // end::query[]

    @Test
    void testFindOpenPullRequestsQuery() {
        var graphqlWebClient = webClient.mutate().baseUrl("/graphql").build();
        // tag::execute[]
        WebClientGraphQLClient graphQLClient = new WebClientGraphQLClient(graphqlWebClient);

        var variables = LinkedHashMapBuilder.of(
                entry("owner", "pulpogato"), entry("repo", "pulpogato"), entry("branch", "main"));
        var response = graphQLClient.reactiveExecuteQuery(OPEN_PRS, variables).block();
        // end::execute[]

        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isEmpty();

        // tag::extract[]
        var repository = response.extractValueAsObject("repository", Repository.class);
        // end::extract[]
        assertThat(repository).isNotNull();

        var pullRequests = repository.getPullRequests();
        assertThat(pullRequests).isNotNull();
        assertThat(pullRequests.getTotalCount()).isEqualTo(5);

        var nodes = pullRequests.getNodes();
        assertThat(nodes).isNotNull().hasSize(5);

        // PR #769 - renovate/spring-core
        var pr769 = nodes.get(0);
        assertThat(pr769)
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(PullRequest.newBuilder()
                        .id("PR_kwDOMTOTFM64abAF")
                        .number(769)
                        .headRefName("renovate/spring-core")
                        .headRefOid("fbc53e352ab54e24e1e3de5484c713fa3cf319a0")
                        .baseRefOid("b585f610fde01343985ad97455a33def33af63be")
                        .mergeable(MergeableState.MERGEABLE)
                        .author(User.newBuilder().login("renovate").build())
                        .url(URI.create("https://github.com/pulpogato/pulpogato/pull/769"))
                        .autoMergeRequest(AutoMergeRequest.newBuilder()
                                .enabledBy(User.newBuilder().login("renovate").build())
                                .mergeMethod(PullRequestMergeMethod.MERGE)
                                .build())
                        .build());

        // PR #768 - remove-jackson-split-reference
        var pr768 = nodes.get(1);
        assertThat(pr768)
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(PullRequest.newBuilder()
                        .id("PR_kwDOMTOTFM64OYaG")
                        .number(768)
                        .headRefName("remove-jackson-split-reference")
                        .headRefOid("ca664557f0cc6cdaf96a2b98d200ec05df883329")
                        .baseRefOid("b585f610fde01343985ad97455a33def33af63be")
                        .mergeable(MergeableState.MERGEABLE)
                        .author(User.newBuilder().login("rahulsom").build())
                        .url(URI.create("https://github.com/pulpogato/pulpogato/pull/768"))
                        .autoMergeRequest(AutoMergeRequest.newBuilder()
                                .enabledBy(User.newBuilder().login("rahulsom").build())
                                .mergeMethod(PullRequestMergeMethod.MERGE)
                                .build())
                        .build());

        // PR #767 - renovate/com.gradle.develocity-4.x
        var pr767 = nodes.get(2);
        assertThat(pr767)
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(PullRequest.newBuilder()
                        .id("PR_kwDOMTOTFM64N5J8")
                        .number(767)
                        .headRefName("renovate/com.gradle.develocity-4.x")
                        .headRefOid("a978c20c700e39103864d0eedbdd195ee02134bf")
                        .baseRefOid("b585f610fde01343985ad97455a33def33af63be")
                        .mergeable(MergeableState.MERGEABLE)
                        .author(User.newBuilder().login("renovate").build())
                        .url(URI.create("https://github.com/pulpogato/pulpogato/pull/767"))
                        .autoMergeRequest(AutoMergeRequest.newBuilder()
                                .enabledBy(User.newBuilder().login("renovate").build())
                                .mergeMethod(PullRequestMergeMethod.MERGE)
                                .build())
                        .build());

        // PR #766 - renovate/dgs
        var pr766 = nodes.get(3);
        assertThat(pr766)
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(PullRequest.newBuilder()
                        .id("PR_kwDOMTOTFM64N5HO")
                        .number(766)
                        .headRefName("renovate/dgs")
                        .headRefOid("75640d81374341d80e40d4a8716a8c224457985b")
                        .baseRefOid("b585f610fde01343985ad97455a33def33af63be")
                        .mergeable(MergeableState.MERGEABLE)
                        .author(User.newBuilder().login("renovate").build())
                        .url(URI.create("https://github.com/pulpogato/pulpogato/pull/766"))
                        .autoMergeRequest(AutoMergeRequest.newBuilder()
                                .enabledBy(User.newBuilder().login("renovate").build())
                                .mergeMethod(PullRequestMergeMethod.MERGE)
                                .build())
                        .build());

        // PR #730 - test-utils-test (no auto-merge, UNKNOWN mergeable state in first response)
        var pr730 = nodes.get(4);
        assertThat(pr730)
                .isNotNull()
                .usingRecursiveComparison()
                .ignoringFields("mergeable")
                .isEqualTo(PullRequest.newBuilder()
                        .id("PR_kwDOMTOTFM62Jj6D")
                        .number(730)
                        .headRefName("test-utils-test")
                        .headRefOid("0d53e71c027ed068c616cf9e966def858dfa1c41")
                        .baseRefOid("caf877eec171597a21b980f51910b7b45a4593cc")
                        .mergeable(MergeableState.UNKNOWN)
                        .author(User.newBuilder().login("rahulsom").build())
                        .url(URI.create("https://github.com/pulpogato/pulpogato/pull/730"))
                        .autoMergeRequest(null)
                        .build());
    }
}
