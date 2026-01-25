package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.schemas.Feed;
import io.github.pulpogato.test.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testListPublicEvents() {
        var api = new RestClients(webClient).getActivityApi();
        var response = api.listPublicEvents(10L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var events = response.getBody();
        assertThat(events).isNotNull().hasSize(10); // We requested max 10 per page

        var firstEvent = events.getFirst();
        assertThat(firstEvent.getId()).isEqualTo("50486820386");
        assertThat(firstEvent.getType()).isEqualTo("PushEvent");
        assertThat(firstEvent.getActor().getLogin()).isEqualTo("james2037");
        assertThat(firstEvent.getRepo().getName()).isEqualTo("james2037/mcp-php-server");
        assertThat(firstEvent.getCreatedAt()).isEqualTo("2025-06-04T04:24:53Z");
    }

    @Test
    void testGetFeeds() {
        var api = new RestClients(webClient).getActivityApi();
        var response = api.getFeeds();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(Feed.class);

        var feed = response.getBody();
        assertThat(feed.getTimelineUrl()).isEqualTo("https://github.com/timeline");
        assertThat(feed.getUserUrl()).isEqualTo("https://github.com/{user}");
        assertThat(feed.getCurrentUserPublicUrl()).isEqualTo("https://github.com/rahulsom");
        // getCurrentUserUrl is not present in the response, only getCurrentUserPublicUrl
        // assertThat(feed.getCurrentUserUrl()).isNotNull();
        // getCurrentUserActorUrl and getCurrentUserOrganizationUrl are not present in this response
        // assertThat(feed.getCurrentUserActorUrl()).isNotNull();
        // assertThat(feed.getCurrentUserOrganizationUrl()).isNotNull();
        assertThat(feed.getSecurityAdvisoriesUrl()).isEqualTo("https://github.com/security-advisories");

        // Verify URLs contain expected patterns
        assertThat(feed.getTimelineUrl()).contains("timeline");
        assertThat(feed.getUserUrl()).contains("{user}");
        assertThat(feed.getSecurityAdvisoriesUrl()).contains("security-advisories");
    }

    @Test
    void testListPublicOrgEvents() {
        var api = new RestClients(webClient).getActivityApi();
        // Using GitHub's own organization as an example
        var response = api.listPublicOrgEvents("github", 5L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var events = response.getBody();
        assertThat(events).isNotNull().hasSize(5); // We requested max 5 per page

        var firstEvent = events.getFirst();
        assertThat(firstEvent.getId()).isEqualTo("50473417741");
        assertThat(firstEvent.getType()).isEqualTo("IssueCommentEvent");
        assertThat(firstEvent.getActor().getLogin()).isEqualTo("Sharra-writes");
        assertThat(firstEvent.getRepo().getName()).isEqualTo("github/docs");
        assertThat(firstEvent.getCreatedAt()).isNotNull();

        // Verify this is related to the GitHub org
        assertThat(firstEvent.getOrg()).isNotNull();
        assertThat(firstEvent.getOrg().getLogin()).isEqualTo("github");
    }

    @Test
    void testListRepoEvents() {
        var api = new RestClients(webClient).getActivityApi();
        // Using a popular repository as an example
        var response = api.listRepoEvents("octocat", "Hello-World", 5L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var events = response.getBody();
        assertThat(events).isNotNull().hasSize(5); // We requested max 5 per page

        var firstEvent = events.getFirst();
        assertThat(firstEvent.getId()).isEqualTo("50472301049");
        assertThat(firstEvent.getType()).isEqualTo("ForkEvent");
        assertThat(firstEvent.getActor().getLogin()).isEqualTo("antoniovial");
        assertThat(firstEvent.getRepo().getName()).isEqualTo("octocat/Hello-World");
        assertThat(firstEvent.getCreatedAt()).isNotNull();
    }

    @Test
    void testListReposStarredByAuthenticatedUser() {
        var api = new RestClients(webClient).getActivityApi();
        var response = api.listReposStarredByAuthenticatedUser(null, null, 10L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var repositories = response.getBody();
        assertThat(repositories).isNotNull().hasSize(1); // We have 1 starred repository

        // Verify starred repositories structure
        var firstRepo = repositories.getFirst();
        assertThat(firstRepo.getId()).isEqualTo(825463572L);
        assertThat(firstRepo.getName()).isEqualTo("pulpogato");
        assertThat(firstRepo.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(firstRepo.getOwner().getLogin()).isEqualTo("pulpogato");
        assertThat(firstRepo.getHtmlUrl()).hasToString("https://github.com/pulpogato/pulpogato");
    }

    @Test
    void testListWatchedReposForAuthenticatedUser() {
        var api = new RestClients(webClient).getActivityApi();
        var response = api.listWatchedReposForAuthenticatedUser(10L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var repositories = response.getBody();
        assertThat(repositories).isNotNull().hasSize(10); // We requested max 10 per page

        // Verify watched repositories structure
        var firstRepo = repositories.getFirst();
        assertThat(firstRepo.getId()).isEqualTo(1006911L);
        assertThat(firstRepo.getName()).isEqualTo("bash-utils");
        assertThat(firstRepo.getFullName()).isEqualTo("rahulsom/bash-utils");
        assertThat(firstRepo.getOwner().getLogin()).isEqualTo("rahulsom");
    }

    @Test
    void testListPublicEventsForUser() {
        var api = new RestClients(webClient).getActivityApi();
        // Using octocat as a well-known GitHub user
        var response = api.listPublicEventsForUser("rahulsom", 5L, 1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var events = response.getBody();
        assertThat(events).isNotNull().hasSize(5); // We requested max 5 per page

        var firstEvent = events.getFirst();
        assertThat(firstEvent.getId()).isEqualTo("50486444578");
        assertThat(firstEvent.getType()).isEqualTo("PushEvent");
        assertThat(firstEvent.getActor().getLogin()).isEqualTo("rahulsom");
        assertThat(firstEvent.getRepo().getName()).isEqualTo("pulpogato/pulpogato");
        assertThat(firstEvent.getCreatedAt()).isNotNull();
    }
}
