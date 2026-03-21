package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.issueforms.GithubIssueForms;
import io.github.pulpogato.githubfiles.issueforms.Type;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubIssueFormsDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    private static final String YAML = """
            name: Bug Report
            description: File a bug report
            body:
              - type: input
                id: version
                attributes:
                  label: Version
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
        var issueForms = mp.yamlMapper().readValue(YAML, GithubIssueForms.class);
        assertThat(issueForms.getName()).isEqualTo("Bug Report");
        assertThat(issueForms.getDescription()).isEqualTo("File a bug report");
        assertThat(issueForms.getBody()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesFormItems(Mappers.MapperPair mp) throws Exception {
        var issueForms = mp.yamlMapper().readValue(YAML, GithubIssueForms.class);
        var body = issueForms.getBody();

        var inputItem = body.get(0);
        assertThat(inputItem.getType()).isEqualTo(Type.INPUT);
        assertThat(inputItem.getId()).isEqualTo("version");
        assertThat(inputItem.getAttributes().getLabel()).isEqualTo("Version");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var issueForms = mp.yamlMapper().readValue(YAML, GithubIssueForms.class);
        var json = mp.jsonMapper().writeValueAsString(issueForms);
        var roundTripped = mp.jsonMapper().readValue(json, GithubIssueForms.class);
        assertThat(roundTripped).isEqualTo(issueForms);
    }
}
