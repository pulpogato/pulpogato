package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.pulpogato.githubfiles.actions.GithubAction;
import io.github.pulpogato.githubfiles.actions.GithubActionBrandingColor;
import io.github.pulpogato.githubfiles.actions.GithubActionInputsValue;
import io.github.pulpogato.githubfiles.actions.GithubActionRuns;
import io.github.pulpogato.githubfiles.actions.RunsJavascript;
import io.github.pulpogato.githubfiles.actions.RunsJavascriptUsing;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GithubActionDeserializationTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Nested
    class JavascriptAction {
        @Language("yaml")
        static final String YAML = """
                name: Setup Node.js
                author: GitHub
                description: Set up a specific version of Node.js and add it to PATH
                inputs:
                  node-version:
                    description: Version spec of the version to use
                    required: false
                    default: "lts/*"
                  registry-url:
                    description: Optional registry to set up for auth
                runs:
                  using: node20
                  main: dist/setup/index.js
                  post: dist/cache-save/index.js
                  post-if: success()
                branding:
                  color: green
                  icon: package
                """;

        @Test
        void deserializesRootProperties() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            assertThat(action.getName()).isEqualTo("Setup Node.js");
            assertThat(action.getAuthor()).isEqualTo("GitHub");
            assertThat(action.getDescription()).isEqualTo("Set up a specific version of Node.js and add it to PATH");
        }

        @Test
        void deserializesInputsAsTypedMap() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            assertThat(action.getInputs())
                    .hasSize(2)
                    .containsKey("node-version")
                    .containsKey("registry-url");

            var nodeVersion = action.getInputs().get("node-version");
            assertThat(nodeVersion.getDescription()).isEqualTo("Version spec of the version to use");
            assertThat(nodeVersion.getRequired()).isFalse();
            assertThat(nodeVersion.getIsDefault()).isEqualTo("lts/*");

            var registryUrl = action.getInputs().get("registry-url");
            assertThat(registryUrl.getDescription()).isEqualTo("Optional registry to set up for auth");
            assertThat(registryUrl.getRequired()).isNull();
            assertThat(registryUrl.getIsDefault()).isNull();
        }

        @Test
        void deserializesRunsAsJavascriptVariant() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            var runs = action.getRuns();
            assertThat(runs.getRunsJavascript()).isNotNull();
            assertThat(runs.getRunsComposite()).isNull();
            assertThat(runs.getRunsDocker()).isNull();

            var jsRuns = runs.getRunsJavascript();
            assertThat(jsRuns.getUsing()).isEqualTo(RunsJavascriptUsing.NODE20);
            assertThat(jsRuns.getMain()).isEqualTo("dist/setup/index.js");
            assertThat(jsRuns.getPost()).isEqualTo("dist/cache-save/index.js");
            assertThat(jsRuns.getPostIf()).isEqualTo("success()");
            assertThat(jsRuns.getPre()).isNull();
        }

        @Test
        void deserializesBranding() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            assertThat(action.getBranding().getColor()).isEqualTo(GithubActionBrandingColor.GREEN);
        }

        @Test
        void roundTripsViaJson() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            var json = jsonMapper.writeValueAsString(action);
            var roundTripped = jsonMapper.readValue(json, GithubAction.class);
            assertThat(roundTripped).isEqualTo(action);
        }
    }

    @Nested
    class CompositeAction {
        @Language("yaml")
        static final String YAML = """
                name: Greet Someone
                description: Greet someone and record the time
                inputs:
                  who-to-greet:
                    description: Who to greet
                    required: true
                    default: World
                runs:
                  using: composite
                  steps:
                    - run: echo Hello ${{ inputs.who-to-greet }}.
                      shell: bash
                """;

        @Test
        void deserializesRunsAsCompositeVariant() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            var runs = action.getRuns();
            assertThat(runs.getRunsComposite()).isNotNull();
            assertThat(runs.getRunsJavascript()).isNull();
            assertThat(runs.getRunsDocker()).isNull();

            var composite = runs.getRunsComposite();
            assertThat(composite.getUsing()).isEqualTo("composite");
            assertThat(composite.getSteps()).hasSize(1);
        }

        @Test
        void deserializesInputsWithRequiredFlag() throws Exception {
            var action = yamlMapper.readValue(YAML, GithubAction.class);
            assertThat(action.getInputs()).hasSize(1).containsKey("who-to-greet");

            var input = action.getInputs().get("who-to-greet");
            assertThat(input.getRequired()).isTrue();
            assertThat(input.getIsDefault()).isEqualTo("World");
        }
    }

    @Nested
    class BuilderApi {
        @Test
        void canBuildAndRoundTripProgrammatically() throws Exception {
            var action = GithubAction.builder()
                    .name("My Action")
                    .description("Does something")
                    .inputs(Map.of(
                            "token",
                            GithubActionInputsValue.builder()
                                    .description("GitHub token")
                                    .required(true)
                                    .build()))
                    .runs(GithubActionRuns.builder()
                            .runsJavascript(RunsJavascript.builder()
                                    .using(RunsJavascriptUsing.NODE20)
                                    .main("index.js")
                                    .build())
                            .build())
                    .build();

            var json = jsonMapper.writeValueAsString(action);
            var deserialized = jsonMapper.readValue(json, GithubAction.class);
            assertThat(deserialized.getName()).isEqualTo("My Action");
            assertThat(deserialized.getInputs()).containsKey("token");
            assertThat(deserialized.getInputs().get("token").getRequired()).isTrue();
        }
    }

    @Nested
    class EnumValues {
        @Test
        void brandingColorForValue() {
            assertThat(GithubActionBrandingColor.forValue("gray-dark")).isEqualTo(GithubActionBrandingColor.GRAY_DARK);
            assertThat(GithubActionBrandingColor.forValue("green")).isEqualTo(GithubActionBrandingColor.GREEN);
        }

        @Test
        void runsUsingForValue() {
            assertThat(RunsJavascriptUsing.forValue("node20")).isEqualTo(RunsJavascriptUsing.NODE20);
            assertThat(RunsJavascriptUsing.forValue("node16")).isEqualTo(RunsJavascriptUsing.NODE16);
        }
    }
}
