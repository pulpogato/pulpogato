package io.github.pulpogato.rest.api;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmojisApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGet() {
        EmojisApi api = new RestClients(webClient).getEmojisApi();
        ResponseEntity<Map<String, String>> response = api.get();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(Map.class);
        
        Map<String, String> emojis = response.getBody();
        assertThat(emojis)
                .isNotEmpty()
                .containsKey("+1")
                .containsKey("100")
                .containsKey("heart");
        
        // Verify that values are URLs
        String heartEmojiUrl = emojis.get("heart");
        assertThat(heartEmojiUrl)
                .isNotNull()
                .startsWith("https://")
                .contains("github");
    }
}