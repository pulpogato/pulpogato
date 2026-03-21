package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.workflowtemplates.GithubWorkflowTemplateProperties;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubWorkflowTemplatePropertiesDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    private static final String YAML = """
            name: Java CI
            description: Java CI with Gradle
            iconName: java
            categories: [Java]
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
        var props = mp.yamlMapper().readValue(YAML, GithubWorkflowTemplateProperties.class);
        assertThat(props.getName()).isEqualTo("Java CI");
        assertThat(props.getDescription()).isEqualTo("Java CI with Gradle");
        assertThat(props.getIconName()).isEqualTo("java");
        assertThat(props.getCategories())
                .containsExactly(
                        io.github.pulpogato.githubfiles.workflowtemplates.GithubWorkflowTemplatePropertiesCategorie
                                .JAVA);
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var props = mp.yamlMapper().readValue(YAML, GithubWorkflowTemplateProperties.class);
        var json = mp.jsonMapper().writeValueAsString(props);
        var roundTripped = mp.jsonMapper().readValue(json, GithubWorkflowTemplateProperties.class);
        assertThat(roundTripped).isEqualTo(props);
    }
}
