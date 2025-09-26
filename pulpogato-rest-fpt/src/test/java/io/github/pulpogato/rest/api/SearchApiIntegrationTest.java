package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.RepoSearchResultItem;
import io.github.pulpogato.rest.schemas.TopicSearchResultItem;
import io.github.pulpogato.rest.schemas.UserSearchResultItem;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testSearchRepositories() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Repos200> response = api.repos("pulpogato", null, null, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Repos200.class);
        
        SearchApi.Repos200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();
        
        // Should find at least the pulpogato repository itself
        assertThat(searchResult.getItems()).isNotEmpty();
        RepoSearchResultItem firstRepo = searchResult.getItems().getFirst();
        assertThat(firstRepo.getName()).isNotNull();
        assertThat(firstRepo.getFullName()).isNotNull();
        assertThat(firstRepo.getOwner()).isNotNull();
        assertThat(firstRepo.getOwner().getLogin()).isNotNull();
    }

    @Test
    void testSearchRepositoriesWithLanguageFilter() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Repos200> response = api.repos("language:java", null, null, 10L, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Repos200.class);
        
        SearchApi.Repos200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull().isEmpty();
    }

    @Test
    void testSearchUsers() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Users200> response = api.users("rahulsom", null, null, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Users200.class);
        
        SearchApi.Users200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();
        
        // Should find users with "rahulsom" in name/login
        assertThat(searchResult.getItems()).isNotEmpty();
        UserSearchResultItem firstUser = searchResult.getItems().getFirst();
        assertThat(firstUser.getLogin()).isNotNull();
        assertThat(firstUser.getId()).isNotNull();
        assertThat(firstUser.getType()).isNotNull();
    }

    @Test
    void testSearchUsersWithLocationFilter() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Users200> response = api.users("location:california", null, null, 5L, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Users200.class);
        
        SearchApi.Users200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();
        
        // Verify pagination works
        assertThat(searchResult.getItems()).isEmpty();
    }

    @Test
    void testSearchTopics() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Topics200> response = api.topics("java", null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Topics200.class);
        
        SearchApi.Topics200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();
        
        // Should find Java-related topics
        assertThat(searchResult.getItems()).isNotEmpty();
        TopicSearchResultItem firstTopic = searchResult.getItems().getFirst();
        assertThat(firstTopic.getName()).isNotNull();
        assertThat(firstTopic.getDisplayName()).isNotNull();
        assertThat(firstTopic.getCreatedBy()).isNotNull();
    }

    @Test
    void testSearchTopicsPopular() {
        SearchApi api = factory.createClient(SearchApi.class);
        ResponseEntity<SearchApi.Topics200> response = api.topics("machine-learning", 10L, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(SearchApi.Topics200.class);
        
        SearchApi.Topics200 searchResult = response.getBody();
        assertThat(searchResult.getTotalCount()).isNotNull();
        assertThat(searchResult.getItems()).isNotNull();
        
        // Verify topics search returns results
        assertThat(searchResult.getItems()).isNotEmpty();
        assertThat(searchResult.getItems().size()).isEqualTo(10);
        
        TopicSearchResultItem firstTopic = searchResult.getItems().getFirst();
        assertThat(firstTopic.getName()).isNotNull();
        assertThat(firstTopic.getScore()).isNotNull();
    }
}