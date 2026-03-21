package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.issueconfig.GithubIssueConfig;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubIssueConfigDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    static final String YAML = """
            blank_issues_enabled: false
            contact_links:
              - name: Community Support
                url: https://community.example.com
                about: Please ask questions in our community forum.
              - name: Security Vulnerabilities
                url: https://github.com/example/repo/security/advisories/new
                about: Please report security vulnerabilities here.
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
        var config = mp.yamlMapper().readValue(YAML, GithubIssueConfig.class);
        assertThat(config.getBlankIssuesEnabled()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesContactLinks(Mappers.MapperPair mp) throws Exception {
        var config = mp.yamlMapper().readValue(YAML, GithubIssueConfig.class);
        assertThat(config.getContactLinks()).hasSize(2);

        var link0 = config.getContactLinks().get(0);
        assertThat(link0.getName()).isEqualTo("Community Support");
        assertThat(link0.getUrl()).isEqualTo("https://community.example.com");
        assertThat(link0.getAbout()).isEqualTo("Please ask questions in our community forum.");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var config = mp.yamlMapper().readValue(YAML, GithubIssueConfig.class);
        var json = mp.jsonMapper().writeValueAsString(config);
        var roundTripped = mp.jsonMapper().readValue(json, GithubIssueConfig.class);
        assertThat(roundTripped).isEqualTo(config);
    }
}
