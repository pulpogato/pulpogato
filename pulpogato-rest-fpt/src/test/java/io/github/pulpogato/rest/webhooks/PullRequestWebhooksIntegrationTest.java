package io.github.pulpogato.rest.webhooks;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.pulpogato.rest.schemas.WebhookPullRequest;
import io.github.pulpogato.rest.schemas.WebhookPullRequestEdited;
import io.github.pulpogato.rest.schemas.WebhookPullRequestReviewRequested;
import io.github.pulpogato.test.TestWebhookResponse;
import io.github.pulpogato.test.WebhookHelper;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration test for multi-event webhook handling via the sealed {@link WebhookPullRequest}
 * supertype. Verifies that the framework correctly deserializes each payload to the right concrete
 * subtype, that pattern matching in a {@code processPullRequest} implementation dispatches
 * correctly, and that the body round-trips without loss.
 *
 * <p>Contrast with {@link PingWebhooksIntegrationTest}, which exercises a single-event webhook
 * where there is only one concrete type and no sealed-supertype dispatch.
 */
@WebMvcTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = PullRequestWebhooksIntegrationTest.PullRequestTestConfig.class)
class PullRequestWebhooksIntegrationTest {
    @Autowired
    MockMvc mvc;

    private static Stream<Arguments> files() {
        return WebhookHelper.getArguments("fpt").filter(args -> ((String) args.get()[0]).startsWith("pull-request"));
    }

    @ParameterizedTest
    @MethodSource("files")
    void doTest(String hookname, String filename) throws Exception {
        WebhookHelper.testWebhook(hookname, filename, mvc);
    }

    @Configuration
    @SpringBootConfiguration
    @EnableWebMvc
    static class PullRequestTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder()
                    .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                    .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .build();
        }

        @SuppressWarnings("InnerClassMayBeStatic")
        // tag::pull-request-webhook-controller[]
        @RestController
        @RequestMapping("/webhooks")
        @RequiredArgsConstructor
        public class PullRequestWebhooksController implements PullRequestWebhooks<TestWebhookResponse> {
            private final ObjectMapper objectMapper;

            @Override
            public ResponseEntity<TestWebhookResponse> processPullRequest(
                    String userAgent,
                    String xGithubHookId,
                    String xGithubEvent,
                    String xGithubHookInstallationTargetId,
                    String xGithubHookInstallationTargetType,
                    String xGitHubDelivery,
                    String xHubSignature256,
                    WebhookPullRequest requestBody) {
                var hookname =
                        switch (requestBody) {
                            case WebhookPullRequestEdited ignored -> "pull-request-edited";
                            case WebhookPullRequestReviewRequested ignored -> "pull-request-review-requested";
                            default ->
                                throw new UnsupportedOperationException("No test fixture for action: "
                                        + requestBody.getClass().getSimpleName());
                        };
                return ResponseEntity.ok(TestWebhookResponse.builder()
                        .webhookName(hookname)
                        .body(objectMapper.writeValueAsString(requestBody))
                        .build());
            }
        }
        // end::pull-request-webhook-controller[]
    }
}
