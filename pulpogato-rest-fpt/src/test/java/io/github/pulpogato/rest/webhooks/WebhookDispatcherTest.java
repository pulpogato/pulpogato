package io.github.pulpogato.rest.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pulpogato.common.WebhookHandler;
import io.github.pulpogato.rest.schemas.WebhookPullRequestEdited;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class WebhookDispatcherTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    @Test
    void dispatchesMultiActionEventToRegisteredHandler() throws Exception {
        PullRequestWebhooks<String> handler = (headers, body) -> {
            assertThat(headers.getGithubEvent()).isEqualTo("pull_request");
            assertThat(body).isInstanceOf(WebhookPullRequestEdited.class);
            return ResponseEntity.ok(body.getClass().getSimpleName());
        };
        var dispatcher = new WebhookDispatcher(mapper, List.of(handler));

        var response =
                dispatcher.dispatch("pull_request", fixture("pull_request/edited.http"), headers("pull_request"));

        assertThat(response.getBody()).isEqualTo("WebhookPullRequestEdited");
    }

    @Test
    void dispatchesSingleEventToRegisteredHandler() throws Exception {
        PingWebhooks<String> handler = (headers, body) -> {
            assertThat(headers.getGithubEvent()).isEqualTo("ping");
            return ResponseEntity.ok(body.getZen());
        };
        var dispatcher = new WebhookDispatcher(mapper, List.of(handler));

        var response = dispatcher.dispatch("ping", fixture("ping/default.http"), headers("ping"));

        assertThat(response.getBody()).isEqualTo("Practicality beats purity.");
    }

    @Test
    void rejectsUnknownEvent() {
        var dispatcher = new WebhookDispatcher(mapper, List.of());
        var webhookHeaders = headers("not_a_github_event");

        assertThatThrownBy(() -> dispatcher.dispatch("not_a_github_event", "{}", webhookHeaders))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown webhook event: not_a_github_event");
    }

    @Test
    void rejectsKnownEventWithoutHandler() {
        var dispatcher = new WebhookDispatcher(mapper, List.of());
        var webhookHeaders = headers("ping");

        assertThatThrownBy(() -> dispatcher.dispatch("ping", "{}", webhookHeaders))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No handler registered for webhook event: ping");
    }

    @Test
    void rejectsAmbiguousHandlerBeans() {
        PingWebhooks<String> first = (headers, body) -> ResponseEntity.ok("first");
        PingWebhooks<String> second = (headers, body) -> ResponseEntity.ok("second");
        var handlers = List.<WebhookHandler>of(first, second);

        assertThatThrownBy(() -> new WebhookDispatcher(mapper, handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Multiple PingWebhooks handlers for webhook event: ping");
    }

    @Test
    void springInjectsHandlerBeansAsList() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(DispatcherConfiguration.class)) {
            var dispatcher = context.getBean(WebhookDispatcher.class);

            var response = dispatcher.dispatch("ping", fixture("ping/default.http"), headers("ping"));

            assertThat(response.getBody()).isEqualTo("Practicality beats purity.");
        }
    }

    private static WebhookHeaders headers(String eventName) {
        return WebhookHeaders.builder().githubEvent(eventName).build();
    }

    private static String fixture(String path) throws IOException {
        try (var stream = WebhookDispatcherTest.class.getResourceAsStream("/webhooks/" + path)) {
            assertThat(stream).isNotNull();
            var recording = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            var bodyStart = recording.indexOf("\n\n");
            assertThat(bodyStart).isGreaterThanOrEqualTo(0);
            return recording.substring(bodyStart + 2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DispatcherConfiguration {
        @Bean
        JsonMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        PingWebhooks<String> pingWebhooks() {
            return (headers, body) -> ResponseEntity.ok(body.getZen());
        }

        @Bean
        WebhookDispatcher webhookDispatcher(JsonMapper objectMapper, List<WebhookHandler> handlers) {
            return new WebhookDispatcher(objectMapper, handlers);
        }
    }
}
