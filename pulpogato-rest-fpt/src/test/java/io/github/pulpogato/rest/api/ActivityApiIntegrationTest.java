package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.Event;
import io.github.pulpogato.rest.schemas.Feed;
import io.github.pulpogato.rest.schemas.MinimalRepository;
import io.github.pulpogato.rest.schemas.Repository;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testListPublicEvents() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        ResponseEntity<List<Event>> response = api.listPublicEvents(10L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<Event> events = response.getBody();
        assertThat(events)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(10); // We requested max 10 per page
        
        if (!events.isEmpty()) {
            Event firstEvent = events.getFirst();
            assertThat(firstEvent.getId()).isNotNull();
            assertThat(firstEvent.getType()).isNotNull();
            assertThat(firstEvent.getActor()).isNotNull();
            assertThat(firstEvent.getRepo()).isNotNull();
            assertThat(firstEvent.getCreatedAt()).isNotNull();
        }
    }

    @Test
    void testGetFeeds() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        ResponseEntity<Feed> response = api.getFeeds();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(Feed.class);
        
        Feed feed = response.getBody();
        assertThat(feed.getTimelineUrl()).isNotNull();
        assertThat(feed.getUserUrl()).isNotNull();
        assertThat(feed.getCurrentUserPublicUrl()).isNotNull();
        // getCurrentUserUrl is not present in the response, only getCurrentUserPublicUrl
        // assertThat(feed.getCurrentUserUrl()).isNotNull();
        // getCurrentUserActorUrl and getCurrentUserOrganizationUrl are not present in this response
        // assertThat(feed.getCurrentUserActorUrl()).isNotNull();
        // assertThat(feed.getCurrentUserOrganizationUrl()).isNotNull();
        assertThat(feed.getSecurityAdvisoriesUrl()).isNotNull();
        
        // Verify URLs contain expected patterns
        assertThat(feed.getTimelineUrl()).contains("timeline");
        assertThat(feed.getUserUrl()).contains("{user}");
        assertThat(feed.getSecurityAdvisoriesUrl()).contains("security-advisories");
    }

    @Test
    void testListPublicOrgEvents() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        // Using GitHub's own organization as an example
        ResponseEntity<List<Event>> response = api.listPublicOrgEvents("github", 5L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<Event> events = response.getBody();
        assertThat(events)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(5); // We requested max 5 per page
        
        if (!events.isEmpty()) {
            Event firstEvent = events.getFirst();
            assertThat(firstEvent.getId()).isNotNull();
            assertThat(firstEvent.getType()).isNotNull();
            assertThat(firstEvent.getActor()).isNotNull();
            assertThat(firstEvent.getRepo()).isNotNull();
            assertThat(firstEvent.getCreatedAt()).isNotNull();
            
            // Verify this is related to the github org
            if (firstEvent.getOrg() != null) {
                assertThat(firstEvent.getOrg().getLogin()).isEqualTo("github");
            }
        }
    }

    @Test
    void testListRepoEvents() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        // Using a popular repository as an example
        ResponseEntity<List<Event>> response = api.listRepoEvents("octocat", "Hello-World", 5L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<Event> events = response.getBody();
        assertThat(events)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(5); // We requested max 5 per page
        
        if (!events.isEmpty()) {
            Event firstEvent = events.getFirst();
            assertThat(firstEvent.getId()).isNotNull();
            assertThat(firstEvent.getType()).isNotNull();
            assertThat(firstEvent.getActor()).isNotNull();
            assertThat(firstEvent.getRepo()).isNotNull();
            assertThat(firstEvent.getCreatedAt()).isNotNull();
            
            // Verify this is related to the specified repository
            assertThat(firstEvent.getRepo().getName()).isEqualTo("octocat/Hello-World");
        }
    }

    @Test
    void testListReposStarredByAuthenticatedUser() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        ResponseEntity<List<Repository>> response = api.listReposStarredByAuthenticatedUser(
                null, null, 10L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<Repository> repositories = response.getBody();
        assertThat(repositories)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(10); // We requested max 10 per page
        
        // If user has starred repositories, verify structure
        if (!repositories.isEmpty()) {
            Repository firstRepo = repositories.getFirst();
            assertThat(firstRepo.getId()).isNotNull();
            assertThat(firstRepo.getName()).isNotNull();
            assertThat(firstRepo.getFullName()).isNotNull();
            assertThat(firstRepo.getOwner()).isNotNull();
            assertThat(firstRepo.getHtmlUrl()).isNotNull();
        }
    }

    @Test
    void testListWatchedReposForAuthenticatedUser() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        ResponseEntity<List<MinimalRepository>> response = api.listWatchedReposForAuthenticatedUser(10L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<MinimalRepository> repositories = response.getBody();
        assertThat(repositories)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(10); // We requested max 10 per page
        
        // If user is watching repositories, verify structure
        if (!repositories.isEmpty()) {
            MinimalRepository firstRepo = repositories.getFirst();
            assertThat(firstRepo.getId()).isNotNull();
            assertThat(firstRepo.getName()).isNotNull();
            assertThat(firstRepo.getFullName()).isNotNull();
            assertThat(firstRepo.getOwner()).isNotNull();
        }
    }

    @Test
    void testListPublicEventsForUser() {
        ActivityApi api = factory.createClient(ActivityApi.class);
        // Using octocat as a well-known GitHub user
        ResponseEntity<List<Event>> response = api.listPublicEventsForUser("rahulsom", 5L, 1L);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<Event> events = response.getBody();
        assertThat(events)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(5); // We requested max 5 per page
        
        if (!events.isEmpty()) {
            Event firstEvent = events.getFirst();
            assertThat(firstEvent.getId()).isNotNull();
            assertThat(firstEvent.getType()).isNotNull();
            assertThat(firstEvent.getActor()).isNotNull();
            assertThat(firstEvent.getRepo()).isNotNull();
            assertThat(firstEvent.getCreatedAt()).isNotNull();
            
            // Verify this is related to the specified user
            assertThat(firstEvent.getActor().getLogin()).isEqualTo("rahulsom");
        }
    }

}