package io.github.pulpogato.rest.webhooks;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.pulpogato.rest.schemas.WebhookCheckRun;
import io.github.pulpogato.rest.schemas.WebhookCheckRunCreated;
import io.github.pulpogato.test.TestWebhookResponse;
import io.github.pulpogato.test.WebhookHelper;
import java.util.List;
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
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression test for a {@code check_run} webhook whose {@code status} is {@code "waiting"}.
 *
 * <p>GitHub's {@code check-run} schema (used for REST API responses) lists {@code waiting} and
 * {@code requested} as valid {@code status} values, but the {@code check-run-with-simple-check-suite}
 * schema (used for the {@code check_run} webhook payload) doesn't, even at the latest revision of
 * github/rest-api-description (see https://github.com/github/rest-api-description/issues/7003). The
 * {@code checkRunWithSimpleCheckSuite.status.*.schema.json} additions in pulpogato-common patch the
 * enum locally until upstream fixes it.
 */
@WebMvcTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = CheckRunWebhooksIntegrationTest.CheckRunTestConfig.class)
class CheckRunWebhooksIntegrationTest {
    @Autowired
    MockMvc mvc;

    private static Stream<Arguments> files() {
        return WebhookHelper.getArguments("fpt").filter(args -> ((String) args.get()[0]).startsWith("check-run"));
    }

    @ParameterizedTest
    @MethodSource("files")
    void doTest(String hookname, String filename) throws Exception {
        WebhookHelper.testWebhook(hookname, filename, mvc);
    }

    @Configuration
    @SpringBootConfiguration
    @EnableWebMvc
    static class CheckRunTestConfig implements WebMvcConfigurer {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder()
                    .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                    .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .build();
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new WebhookHeadersArgumentResolver());
        }

        @SuppressWarnings("InnerClassMayBeStatic")
        @RestController
        @RequestMapping("/webhooks")
        @RequiredArgsConstructor
        public class CheckRunWebhooksController implements CheckRunWebhooks<TestWebhookResponse> {
            private final ObjectMapper objectMapper;

            @Override
            public ResponseEntity<TestWebhookResponse> processCheckRun(
                    WebhookHeaders headers, WebhookCheckRun requestBody) {
                var hookname =
                        switch (requestBody) {
                            case WebhookCheckRunCreated ignored -> "check-run-created";
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
    }
}
