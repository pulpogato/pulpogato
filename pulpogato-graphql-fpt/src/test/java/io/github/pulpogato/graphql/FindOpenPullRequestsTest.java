package io.github.pulpogato.graphql;

import com.netflix.graphql.dgs.client.WebClientGraphQLClient;
import io.github.pulpogato.common.util.LinkedHashMapBuilder;
import io.github.pulpogato.graphql.types.MergeableState;
import io.github.pulpogato.graphql.types.PullRequestMergeMethod;
import io.github.pulpogato.graphql.types.Repository;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FindOpenPullRequestsTest extends BaseIntegrationTest {
    @Language("graphql")
    public static final String OPEN_PRS = """
        query findOpenPullRequests($owner: String!, $repo: String!, $branch: String!) {
          repository(owner: $owner, name: $repo, followRenames: false) {
            pullRequests(first: 100, states: [OPEN], baseRefName: $branch, orderBy: {field: UPDATED_AT, direction: DESC}) {
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

    @Test
    void testFindOpenPullRequestsQuery() {
        var graphqlWebClient = webClient.mutate().baseUrl("/graphql").build();
        WebClientGraphQLClient graphQLClient = new WebClientGraphQLClient(graphqlWebClient);

        var variables = LinkedHashMapBuilder.of(
                Map.entry("owner", "pulpogato"),
                Map.entry("repo", "pulpogato"),
                Map.entry("branch", "main")
        );
        var response = graphQLClient.reactiveExecuteQuery(OPEN_PRS, variables).block();

        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isEmpty();

        var repository = response.extractValueAsObject("repository", Repository.class);
        assertThat(repository).isNotNull();

        var pullRequests = repository.getPullRequests();
        assertThat(pullRequests).isNotNull();
        assertThat(pullRequests.getTotalCount()).isEqualTo(5);

        var nodes = pullRequests.getNodes();
        assertThat(nodes).isNotNull()
                .hasSize(5);

        // PR #769 - renovate/spring-core
        var pr769 = nodes.get(0);
        assertThat(pr769.getId()).isEqualTo("PR_kwDOMTOTFM64abAF");
        assertThat(pr769.getNumber()).isEqualTo(769);
        assertThat(pr769.getHeadRefName()).isEqualTo("renovate/spring-core");
        assertThat(pr769.getHeadRefOid()).isEqualTo("fbc53e352ab54e24e1e3de5484c713fa3cf319a0");
        assertThat(pr769.getBaseRefOid()).isEqualTo("b585f610fde01343985ad97455a33def33af63be");
        assertThat(pr769.getMergeable()).isEqualTo(MergeableState.MERGEABLE);
        assertThat(pr769.getAuthor().getLogin()).isEqualTo("renovate");
        assertThat(pr769.getUrl().toString()).isEqualTo("https://github.com/pulpogato/pulpogato/pull/769");
        assertThat(pr769.getAutoMergeRequest()).isNotNull();
        assertThat(pr769.getAutoMergeRequest().getEnabledBy().getLogin()).isEqualTo("renovate");
        assertThat(pr769.getAutoMergeRequest().getMergeMethod()).isEqualTo(PullRequestMergeMethod.MERGE);

        // PR #768 - remove-jackson-split-reference
        var pr768 = nodes.get(1);
        assertThat(pr768.getId()).isEqualTo("PR_kwDOMTOTFM64OYaG");
        assertThat(pr768.getNumber()).isEqualTo(768);
        assertThat(pr768.getHeadRefName()).isEqualTo("remove-jackson-split-reference");
        assertThat(pr768.getHeadRefOid()).isEqualTo("ca664557f0cc6cdaf96a2b98d200ec05df883329");
        assertThat(pr768.getBaseRefOid()).isEqualTo("b585f610fde01343985ad97455a33def33af63be");
        assertThat(pr768.getMergeable()).isEqualTo(MergeableState.MERGEABLE);
        assertThat(pr768.getAuthor().getLogin()).isEqualTo("rahulsom");
        assertThat(pr768.getUrl().toString()).isEqualTo("https://github.com/pulpogato/pulpogato/pull/768");
        assertThat(pr768.getAutoMergeRequest()).isNotNull();
        assertThat(pr768.getAutoMergeRequest().getEnabledBy().getLogin()).isEqualTo("rahulsom");
        assertThat(pr768.getAutoMergeRequest().getMergeMethod()).isEqualTo(PullRequestMergeMethod.MERGE);

        // PR #767 - renovate/com.gradle.develocity-4.x
        var pr767 = nodes.get(2);
        assertThat(pr767.getId()).isEqualTo("PR_kwDOMTOTFM64N5J8");
        assertThat(pr767.getNumber()).isEqualTo(767);
        assertThat(pr767.getHeadRefName()).isEqualTo("renovate/com.gradle.develocity-4.x");
        assertThat(pr767.getHeadRefOid()).isEqualTo("a978c20c700e39103864d0eedbdd195ee02134bf");
        assertThat(pr767.getBaseRefOid()).isEqualTo("b585f610fde01343985ad97455a33def33af63be");
        assertThat(pr767.getMergeable()).isEqualTo(MergeableState.MERGEABLE);
        assertThat(pr767.getAuthor().getLogin()).isEqualTo("renovate");
        assertThat(pr767.getUrl().toString()).isEqualTo("https://github.com/pulpogato/pulpogato/pull/767");
        assertThat(pr767.getAutoMergeRequest()).isNotNull();
        assertThat(pr767.getAutoMergeRequest().getEnabledBy().getLogin()).isEqualTo("renovate");
        assertThat(pr767.getAutoMergeRequest().getMergeMethod()).isEqualTo(PullRequestMergeMethod.MERGE);

        // PR #766 - renovate/dgs
        var pr766 = nodes.get(3);
        assertThat(pr766.getId()).isEqualTo("PR_kwDOMTOTFM64N5HO");
        assertThat(pr766.getNumber()).isEqualTo(766);
        assertThat(pr766.getHeadRefName()).isEqualTo("renovate/dgs");
        assertThat(pr766.getHeadRefOid()).isEqualTo("75640d81374341d80e40d4a8716a8c224457985b");
        assertThat(pr766.getBaseRefOid()).isEqualTo("b585f610fde01343985ad97455a33def33af63be");
        assertThat(pr766.getMergeable()).isEqualTo(MergeableState.MERGEABLE);
        assertThat(pr766.getAuthor().getLogin()).isEqualTo("renovate");
        assertThat(pr766.getUrl().toString()).isEqualTo("https://github.com/pulpogato/pulpogato/pull/766");
        assertThat(pr766.getAutoMergeRequest()).isNotNull();
        assertThat(pr766.getAutoMergeRequest().getEnabledBy().getLogin()).isEqualTo("renovate");
        assertThat(pr766.getAutoMergeRequest().getMergeMethod()).isEqualTo(PullRequestMergeMethod.MERGE);

        // PR #730 - test-utils-test (no auto-merge, UNKNOWN mergeable state in first response)
        var pr730 = nodes.get(4);
        assertThat(pr730.getId()).isEqualTo("PR_kwDOMTOTFM62Jj6D");
        assertThat(pr730.getNumber()).isEqualTo(730);
        assertThat(pr730.getHeadRefName()).isEqualTo("test-utils-test");
        assertThat(pr730.getHeadRefOid()).isEqualTo("0d53e71c027ed068c616cf9e966def858dfa1c41");
        assertThat(pr730.getBaseRefOid()).isEqualTo("caf877eec171597a21b980f51910b7b45a4593cc");
        assertThat(pr730.getMergeable()).isIn(MergeableState.UNKNOWN, MergeableState.CONFLICTING);
        assertThat(pr730.getAuthor().getLogin()).isEqualTo("rahulsom");
        assertThat(pr730.getUrl().toString()).isEqualTo("https://github.com/pulpogato/pulpogato/pull/730");
        assertThat(pr730.getAutoMergeRequest()).isNull();
    }
}
