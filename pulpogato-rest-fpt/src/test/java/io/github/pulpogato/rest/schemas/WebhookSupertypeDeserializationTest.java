package io.github.pulpogato.rest.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that the generated sealed webhook supertypes carry enough Jackson metadata
 * (@JsonTypeInfo / @JsonSubTypes) to deserialize a payload straight to the right subtype based on its
 * {@code action} discriminator.
 */
class WebhookSupertypeDeserializationTest {
    // Mirrors the mapper the generated webhook integration tests use.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    @ParameterizedTest
    @MethodSource("checkRunCases")
    void deserializesToTheConcreteSubtypeByAction(String action, Class<? extends WebhookCheckRun> expected) {
        WebhookCheckRun result = objectMapper.readValue("{\"action\":\"" + action + "\"}", WebhookCheckRun.class);

        assertThat(result).isInstanceOf(expected);
    }

    private static Stream<Arguments> checkRunCases() {
        return Stream.of(
                arguments("completed", WebhookCheckRunCompleted.class),
                arguments("created", WebhookCheckRunCreated.class),
                arguments("requested_action", WebhookCheckRunRequestedAction.class),
                arguments("rerequested", WebhookCheckRunRerequested.class));
    }
}
