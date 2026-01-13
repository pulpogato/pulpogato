package io.github.pulpogato.rest.webhooks;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.pulpogato.rest.schemas.WebhookPing;
import io.github.pulpogato.test.TestWebhookResponse;
import io.github.pulpogato.test.WebhookHelper;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

@WebMvcTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = PingWebhooksIntegrationTest.PingTestConfig.class)
class PingWebhooksIntegrationTest {
    @Autowired
    MockMvc mvc;

    private static Stream<Arguments> files() {
        return WebhookHelper.getArguments("fpt").filter(args -> args.get()[0].equals("ping"));
    }

    @ParameterizedTest
    @MethodSource("files")
    void doTest(String hookname, String filename) throws Exception {
        WebhookHelper.testWebhook(hookname, filename, mvc);
    }

    @Configuration
    @SpringBootConfiguration
    @EnableWebMvc
    static class PingTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder()
                    .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                    .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .build();
        }

        static
        // tag::ping-webhook-controller[]
        @Getter
        @RestController
        @RequestMapping("/webhooks")
        public class PingWebhooksController implements PingWebhooks<TestWebhookResponse> {
            @Autowired
            private ObjectMapper objectMapper;

            @Override
            public ResponseEntity<TestWebhookResponse> processPing(
                    String userAgent,
                    String xGithubHookId,
                    String xGithubEvent,
                    String xGithubHookInstallationTargetId,
                    String xGithubHookInstallationTargetType,
                    String xGitHubDelivery,
                    String xHubSignature256,
                    WebhookPing requestBody) {
                return ResponseEntity.ok(TestWebhookResponse.builder()
                        .webhookName("ping")
                        .body(objectMapper.writeValueAsString(requestBody))
                        .build());
            }

        }
        // end::ping-webhook-controller[]
    }
}
