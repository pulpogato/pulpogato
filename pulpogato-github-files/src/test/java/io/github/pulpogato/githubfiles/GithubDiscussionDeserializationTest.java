package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.discussion.GithubDiscussion;
import io.github.pulpogato.githubfiles.discussion.GithubDiscussionBodyType;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubDiscussionDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Language("yaml")
    static final String YAML = """
            title: "New Discussion"
            labels:
              - announcement
              - important
            body:
              - type: markdown
                attributes:
                  label: "Read first"
                  description: "Please read the following rules before posting."
              - type: input
                attributes:
                  label: "Topic"
                  description: "What is this discussion about?"
                  placeholder: "Enter topic"
                validations:
                  required: true
            """;

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
        var discussion = mp.yamlMapper().readValue(YAML, GithubDiscussion.class);
        assertThat(discussion.getTitle()).isEqualTo("New Discussion");
        assertThat(discussion.getLabels().getList()).containsExactly("announcement", "important");
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void deserializesBodyElements(Mappers.MapperPair mp) throws Exception {
        var discussion = mp.yamlMapper().readValue(YAML, GithubDiscussion.class);
        assertThat(discussion.getBody()).hasSize(2);

        var element0 = discussion.getBody().get(0);
        assertThat(element0.getType()).isEqualTo(GithubDiscussionBodyType.MARKDOWN);
        assertThat(element0.getAttributes().getLabel()).isEqualTo("Read first");

        var element1 = discussion.getBody().get(1);
        assertThat(element1.getType()).isEqualTo(GithubDiscussionBodyType.INPUT);
        assertThat(element1.getAttributes().getLabel()).isEqualTo("Topic");
        assertThat(element1.getValidations().getRequired()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("mappers")
    void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
        var discussion = mp.yamlMapper().readValue(YAML, GithubDiscussion.class);
        var json = mp.jsonMapper().writeValueAsString(discussion);
        var roundTripped = mp.jsonMapper().readValue(json, GithubDiscussion.class);
        assertThat(roundTripped).isEqualTo(discussion);
    }
}
