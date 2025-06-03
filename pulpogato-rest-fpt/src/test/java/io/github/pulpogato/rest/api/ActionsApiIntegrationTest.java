package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.ActionsCacheUsageByRepository;
import io.github.pulpogato.rest.schemas.ActionsCacheList;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActionsApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetActionsCacheUsage() {
        ActionsApi api = factory.createClient(ActionsApi.class);
        ResponseEntity<ActionsCacheUsageByRepository> response = api.getActionsCacheUsage("pulpogato", "pulpogato");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheUsageByRepository.class);
        
        ActionsCacheUsageByRepository cacheUsage = response.getBody();
        assertThat(cacheUsage.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(cacheUsage.getActiveCachesSizeInBytes()).isNotNull();
        assertThat(cacheUsage.getActiveCachesCount()).isNotNull();
    }

    @Test
    void testGetActionsCacheList() {
        ActionsApi api = factory.createClient(ActionsApi.class);
        ResponseEntity<ActionsCacheList> response = api.getActionsCacheList("pulpogato", "pulpogato", null, null, null, null, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheList.class);
        
        ActionsCacheList cacheList = response.getBody();
        assertThat(cacheList.getTotalCount()).isNotNull();
        assertThat(cacheList.getActionsCaches()).isNotNull();
    }

    @Test
    void testGetActionsCacheListWithPagination() {
        ActionsApi api = factory.createClient(ActionsApi.class);
        ResponseEntity<ActionsCacheList> response = api.getActionsCacheList("pulpogato", "pulpogato", 10L, 0L, null, null, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(ActionsCacheList.class);
        
        ActionsCacheList cacheList = response.getBody();
        assertThat(cacheList.getTotalCount()).isNotNull();
        assertThat(cacheList.getActionsCaches()).isNotNull();
        
        // Verify pagination works
        if (cacheList.getActionsCaches().size() > 0) {
            assertThat(cacheList.getActionsCaches().size()).isLessThanOrEqualTo(10);
        }
    }

}