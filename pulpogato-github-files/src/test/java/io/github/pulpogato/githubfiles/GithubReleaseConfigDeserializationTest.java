package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.releases.GithubReleaseConfig;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelog;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelogCategorie;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfigChangelogExclude;
import java.util.List;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubReleaseConfigDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubReleaseConfigDeserializationTest#mappers")
        void deserializesExcludeSection(Mappers.MapperPair mp) throws Exception {
            var config = mp.yamlMapper().readValue(YAML, GithubReleaseConfig.class);
            var exclude = config.getChangelog().getExclude();
            assertThat(exclude.getLabels()).containsExactly("ignore-for-release");
            assertThat(exclude.getAuthors()).containsExactly("octocat");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubReleaseConfigDeserializationTest#mappers")
        void deserializesCategories(Mappers.MapperPair mp) throws Exception {
            var config = mp.yamlMapper().readValue(YAML, GithubReleaseConfig.class);
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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubReleaseConfigDeserializationTest#mappers")
        void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
            var config = mp.yamlMapper().readValue(YAML, GithubReleaseConfig.class);
            var json = mp.jsonMapper().writeValueAsString(config);
            var roundTripped = mp.jsonMapper().readValue(json, GithubReleaseConfig.class);
            assertThat(roundTripped).isEqualTo(config);
        }
    }

    @Nested
    class BuilderApi {
        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubReleaseConfigDeserializationTest#mappers")
        void canBuildAndRoundTripProgrammatically(Mappers.MapperPair mp) throws Exception {
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

            var json = mp.jsonMapper().writeValueAsString(config);
            var deserialized = mp.jsonMapper().readValue(json, GithubReleaseConfig.class);
            assertThat(deserialized).isEqualTo(config);
            assertThat(deserialized.getChangelog().getCategories()).hasSize(2);
        }
    }
}
