package io.github.pulpogato.rest.api.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmojisApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGet() {
        var api = new RestClients(webClient).getEmojisApi();
        var response = api.get().block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(Map.class);

        var emojis = response.getBody();
        assertThat(emojis).isNotEmpty().containsKey("+1").containsKey("100").containsKey("heart");

        // Verify that values are URLs
        var heartEmojiUrl = emojis.get("heart");
        assertThat(heartEmojiUrl).isNotNull().startsWith("https://").contains("github");
    }
}
