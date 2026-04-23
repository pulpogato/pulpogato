package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.actionstyping.TopLevelOrNull;
import java.util.Map;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubActionsTypingDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    private static final String YAML = """
            inputs:
              log-level:
                type: enum
                allowed-values:
                  - debug
                  - info
              retries:
                type: integer
                named-values:
                  default: 3
              paths:
                type: list
                separator: ","
                list-item:
                  type: string
            outputs:
              artifact-id:
                type: string
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesInputsAndOutputs(Mappers.MapperPair mp) throws Exception {
        var typing = mp.yamlMapper().readValue(YAML, TopLevelOrNull.class);

        assertThat(typing.getInputs()).containsKeys("log-level", "retries", "paths");
        assertThat(typing.getOutputs()).containsKey("artifact-id");

        var logLevel = typing.getInputs().get("log-level");
        assertThat(logLevel)
                .containsEntry("type", "enum")
                .containsEntry("allowed-values", java.util.List.of("debug", "info"));

        var retries = typing.getInputs().get("retries");
        assertThat(retries).containsEntry("type", "integer").containsEntry("named-values", Map.of("default", 3));

        var paths = typing.getInputs().get("paths");
        assertThat(paths)
                .containsEntry("type", "list")
                .containsEntry("separator", ",")
                .containsEntry("list-item", Map.of("type", "string"));

        assertThat(typing.getOutputs().get("artifact-id")).containsEntry("type", "string");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var typing = mp.yamlMapper().readValue(YAML, TopLevelOrNull.class);
        var json = mp.jsonMapper().writeValueAsString(typing);
        var roundTripped = mp.jsonMapper().readValue(json, TopLevelOrNull.class);
        assertThat(roundTripped).isEqualTo(typing);
    }
}
