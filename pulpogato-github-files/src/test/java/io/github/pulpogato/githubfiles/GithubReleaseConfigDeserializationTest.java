package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfig;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelog;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelogCategorie;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelogExclude;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GithubReleaseConfigDeserializationTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Nested
    class Changelog {
        @Language("yaml")
        static final String YAML = """
                changelog:
                  exclude:
                    labels:
                      - ignore-for-release
                    authors:
                      - octocat
                  categories:
                    - title: Breaking Changes
                      labels:
                        - semver-major
                        - breaking-change
                    - title: New Features
                      labels:
                        - semver-minor
                        - enhancement
                    - title: Bug Fixes
                      labels:
                        - semver-patch
                        - bug
                    - title: Other Changes
                      labels:
                        - "*"
                """;

        @Test
        void deserializesExcludeSection() throws Exception {
            var config = yamlMapper.readValue(YAML, GithubReleaseConfig.class);
            var exclude = config.getChangelog().getExclude();
            assertThat(exclude.getLabels()).containsExactly("ignore-for-release");
            assertThat(exclude.getAuthors()).containsExactly("octocat");
        }

        @Test
        void deserializesCategories() throws Exception {
            var config = yamlMapper.readValue(YAML, GithubReleaseConfig.class);
            var categories = config.getChangelog().getCategories();
            assertThat(categories).hasSize(4);

            assertThat(categories.get(0).getTitle()).isEqualTo("Breaking Changes");
            assertThat(categories.get(0).getLabels()).containsExactly("semver-major", "breaking-change");

            assertThat(categories.get(1).getTitle()).isEqualTo("New Features");
            assertThat(categories.get(1).getLabels()).containsExactly("semver-minor", "enhancement");

            assertThat(categories.get(2).getTitle()).isEqualTo("Bug Fixes");
            assertThat(categories.get(2).getLabels()).containsExactly("semver-patch", "bug");

            assertThat(categories.get(3).getTitle()).isEqualTo("Other Changes");
            assertThat(categories.get(3).getLabels()).containsExactly("*");
        }

        @Test
        void roundTripsViaJson() throws Exception {
            var config = yamlMapper.readValue(YAML, GithubReleaseConfig.class);
            var json = jsonMapper.writeValueAsString(config);
            var roundTripped = jsonMapper.readValue(json, GithubReleaseConfig.class);
            assertThat(roundTripped).isEqualTo(config);
        }
    }

    @Nested
    class BuilderApi {
        @Test
        void canBuildAndRoundTripProgrammatically() throws Exception {
            var config = GithubReleaseConfig.builder()
                    .changelog(GithubReleaseConfigChangelog.builder()
                            .exclude(GithubReleaseConfigChangelogExclude.builder()
                                    .labels(List.of("skip-changelog"))
                                    .build())
                            .categories(List.of(
                                    GithubReleaseConfigChangelogCategorie.builder()
                                            .title("Features")
                                            .labels(List.of("feature"))
                                            .build(),
                                    GithubReleaseConfigChangelogCategorie.builder()
                                            .title("Everything Else")
                                            .labels(List.of("*"))
                                            .build()))
                            .build())
                    .build();

            var json = jsonMapper.writeValueAsString(config);
            var deserialized = jsonMapper.readValue(json, GithubReleaseConfig.class);
            assertThat(deserialized).isEqualTo(config);
            assertThat(deserialized.getChangelog().getCategories()).hasSize(2);
        }
    }
}
