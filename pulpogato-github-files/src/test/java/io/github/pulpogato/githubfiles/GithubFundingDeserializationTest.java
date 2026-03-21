package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.funding.GithubFunding;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubFundingDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    private static final String YAML = """
            github: octocat
            patreon: octocat
            tidelift: npm/pulpogato
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
        var funding = mp.yamlMapper().readValue(YAML, GithubFunding.class);
        assertThat(funding.getGithub().getString()).isEqualTo("octocat");
        assertThat(funding.getPatreon()).isEqualTo("octocat");
        assertThat(funding.getTidelift()).isEqualTo("npm/pulpogato");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesGithubSponsorsAsList(Mappers.MapperPair mp) throws Exception {
        @Language("yaml")
        var yaml = """
                github: [octocat, hubot]
                """;
        var funding = mp.yamlMapper().readValue(yaml, GithubFunding.class);
        assertThat(funding.getGithub().getList()).containsExactly("octocat", "hubot");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var funding = mp.yamlMapper().readValue(YAML, GithubFunding.class);
        var json = mp.jsonMapper().writeValueAsString(funding);
        var roundTripped = mp.jsonMapper().readValue(json, GithubFunding.class);
        assertThat(roundTripped).isEqualTo(funding);
    }
}
