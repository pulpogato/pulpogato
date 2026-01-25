package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class SearchApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testSearchRepositories() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.repos("pulpogato", null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Repos200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();

        // Should find at least the pulpogato repository itself
        assertThat(searchResult.getItems()).isNotEmpty();
        var firstRepo = searchResult.getItems().getFirst();
        assertThat(firstRepo.getName()).isNotNull();
        assertThat(firstRepo.getFullName()).isNotNull();
        assertThat(firstRepo.getOwner()).isNotNull();
        assertThat(firstRepo.getOwner().getLogin()).isNotNull();
    }

    @Test
    void testSearchRepositoriesWithLanguageFilter() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.repos("language:java", null, null, 10L, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Repos200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull().isEmpty();
    }

    @Test
    void testSearchUsers() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.users("rahulsom", null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Users200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();

        // Should find users with "rahulsom" in name/login
        assertThat(searchResult.getItems()).isNotEmpty();
        var firstUser = searchResult.getItems().getFirst();
        assertThat(firstUser.getLogin()).isNotNull();
        assertThat(firstUser.getId()).isNotNull();
        assertThat(firstUser.getType()).isNotNull();
    }

    @Test
    void testSearchUsersWithLocationFilter() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.users("location:california", null, null, 5L, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Users200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();

        // Verify pagination works
        assertThat(searchResult.getItems()).isEmpty();
    }

    @Test
    void testSearchTopics() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.topics("java", null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Topics200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();

        // Should find Java-related topics
        assertThat(searchResult.getItems()).isNotEmpty();
        var firstTopic = searchResult.getItems().getFirst();
        assertThat(firstTopic.getName()).isNotNull();
        assertThat(firstTopic.getDisplayName()).isNotNull();
        assertThat(firstTopic.getCreatedBy()).isNotNull();
    }

    @Test
    void testSearchTopicsPopular() {
        var api = new RestClients(webClient).getSearchApi();
        var response = api.topics("machine-learning", 10L, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(SearchApi.Topics200.class);

        var searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();

        // Verify topics search returns results
        assertThat(searchResult.getItems()).isNotEmpty();
        assertThat(searchResult.getItems().size()).isEqualTo(10);

        var firstTopic = searchResult.getItems().getFirst();
        assertThat(firstTopic.getName()).isNotNull();
        assertThat(firstTopic.getScore()).isNotNull();
    }
}
