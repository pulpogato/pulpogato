package io.github.pulpogato.rest.schemas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class CheckSuiteAppOwnerDeserializationTest {
    @ParameterizedTest
    @ValueSource(strings = {"enterprise", "Organization", "Bot", "User"})
    void shouldPreserveAppOwnerTypeWithJackson2(String ownerType) throws Exception {
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var webhook = mapper.readValue(payload(ownerType), WebhookCheckSuiteCompleted.class);

        assertThat(webhook.getCheckSuite()
                        .getApp()
                        .getOwner()
                        .getValue()
                        .getType()
                        .getValue())
                .isEqualTo(ownerType);
        assertThat(mapper.readTree(mapper.writeValueAsString(webhook))
                        .at("/check_suite/app/owner/type")
                        .asText())
                .isEqualTo(ownerType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"enterprise", "Organization", "Bot", "User"})
    void shouldPreserveAppOwnerTypeWithJackson3(String ownerType) {
        var mapper = JsonMapper.builder().build();
        var webhook = mapper.readValue(payload(ownerType), WebhookCheckSuiteCompleted.class);

        assertThat(webhook.getCheckSuite()
                        .getApp()
                        .getOwner()
                        .getValue()
                        .getType()
                        .getValue())
                .isEqualTo(ownerType);
        assertThat(mapper.readTree(mapper.writeValueAsString(webhook))
                        .at("/check_suite/app/owner/type")
                        .asString())
                .isEqualTo(ownerType);
    }

    private static String payload(String ownerType) {
        return """
                {"action":"completed","check_suite":{"app":{"owner":{"type":"%s"}}}}
                """.formatted(ownerType);
    }
}
