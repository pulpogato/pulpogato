package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.pulpogato.githubfiles.workflows.Event;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflow;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflowJobsValue;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflowOn;
import io.github.pulpogato.githubfiles.workflows.NormalJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubWorkflowDeserializationTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Nested
    class NormalJobWorkflow {
        @Language("yaml")
        static final String YAML = """
                name: CI
                on:
                  push:
                    branches: [main]
                  pull_request:
                    branches: [main]
                env:
                  CI: "true"
                  NODE_ENV: test
                jobs:
                  build:
                    name: Build and Test
                    runs-on: ubuntu-latest
                    timeout-minutes: 30
                    env:
                      GRADLE_OPTS: "-Xmx2g"
                    steps:
                      - uses: actions/checkout@v4
                      - name: Run tests
                        run: ./gradlew test
                """;

        @Test
        void deserializesRootProperties() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getName()).isEqualTo("CI");
        }

        @Test
        void deserializesOnTriggerAsEventMap() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getGithubWorkflowOnVariant2()).isNotNull();

            var variant = on.getGithubWorkflowOnVariant2();
            assertThat(variant.getPush()).isNotNull();
            assertThat(variant.getPush().getBranches()).containsExactly("main");
            assertThat(variant.getPullRequest()).isNotNull();
            assertThat(variant.getPullRequest().getBranches()).containsExactly("main");
        }

        @Test
        void deserializesJobsAsTypedMap() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getJobs()).hasSize(1).containsKey("build");

            var buildJob = wf.getJobs().get("build");
            assertThat(buildJob.getNormalJob()).isNotNull();
            assertThat(buildJob.getReusableWorkflowCallJob()).isNull();
        }

        @Test
        void deserializesNormalJobProperties() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var job = wf.getJobs().get("build").getNormalJob();

            assertThat(job.getName()).isEqualTo("Build and Test");
            assertThat(job.getRunsOn()).isEqualTo("ubuntu-latest");
            assertThat(job.getTimeoutMinutes()).isEqualTo(30);
            assertThat(job.getSteps()).hasSize(2);
        }

        @Test
        void deserializesWorkflowLevelEnv() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var env = wf.getEnv();
            assertThat(env.getMap()).containsEntry("CI", "true").containsEntry("NODE_ENV", "test");
        }

        @Test
        void roundTripsViaJson() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var json = jsonMapper.writeValueAsString(wf);
            var roundTripped = jsonMapper.readValue(json, GithubWorkflow.class);
            assertThat(roundTripped).isEqualTo(wf);
        }
    }

    @Nested
    class ReusableWorkflow {
        @Language("yaml")
        static final String YAML = """
                name: Deploy
                on: workflow_dispatch
                jobs:
                  staging:
                    uses: octo-org/octo-repo/.github/workflows/deploy.yml@main
                    with:
                      environment: staging
                    secrets: inherit
                """;

        @Test
        void deserializesOnTriggerAsSingleEvent() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getEvent()).isEqualTo(Event.WORKFLOW_DISPATCH);
        }

        @Test
        void deserializesReusableWorkflowCallJob() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getJobs()).hasSize(1).containsKey("staging");

            var staging = wf.getJobs().get("staging");
            assertThat(staging.getReusableWorkflowCallJob()).isNotNull();
            assertThat(staging.getNormalJob()).isNull();

            var callJob = staging.getReusableWorkflowCallJob();
            assertThat(callJob.getUses()).isEqualTo("octo-org/octo-repo/.github/workflows/deploy.yml@main");
        }

        @Test
        void deserializesReusableWorkflowWith() throws Exception {
            var wf = yamlMapper.readValue(YAML, GithubWorkflow.class);
            var callJob = wf.getJobs().get("staging").getReusableWorkflowCallJob();
            assertThat(callJob.getWith().getMap()).containsEntry("environment", "staging");
        }
    }

    @Nested
    class BuilderApi {
        @Test
        void canBuildWorkflowProgrammatically() throws Exception {
            var wf = GithubWorkflow.builder()
                    .name("Test")
                    .on(GithubWorkflowOn.builder().event(Event.PUSH).build())
                    .jobs(Map.of(
                            "test",
                            GithubWorkflowJobsValue.builder()
                                    .normalJob(NormalJob.builder()
                                            .runsOn("ubuntu-latest")
                                            .steps(List.of(Map.of("run", "echo hello")))
                                            .build())
                                    .build()))
                    .build();

            var json = jsonMapper.writeValueAsString(wf);
            var deserialized = jsonMapper.readValue(json, GithubWorkflow.class);
            assertThat(deserialized.getName()).isEqualTo("Test");
            assertThat(deserialized.getJobs()).containsKey("test");
            assertThat(deserialized.getJobs().get("test").getNormalJob().getRunsOn())
                    .isEqualTo("ubuntu-latest");
        }
    }

    @Nested
    class OnTriggerVariants {
        @Test
        void deserializesEventList() throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            @SuppressWarnings("unchecked")
            var rawList = (List<Object>) (List<?>) on.getList();
            assertThat(rawList).containsExactly("push", "pull_request");
            assertThat(on.getEvent()).isNull();
            assertThat(on.getGithubWorkflowOnVariant2()).isNull();
        }

        @Test
        void deserializesEventMap() throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on:
                      push:
                        branches: [main]
                      pull_request:
                        branches: [main]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getGithubWorkflowOnVariant2()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPush()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPush().getBranches()).containsExactly("main");
            assertThat(on.getGithubWorkflowOnVariant2().getPullRequest()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPullRequest().getBranches())
                    .containsExactly("main");
            assertThat(on.getEvent()).isNull();
            assertThat(on.getList()).isNull();
        }

        @Test
        void deserializesEventMapAsJson() throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: {push: {branches: ["main"]},pull_request: {branches: ["main"]}}
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getGithubWorkflowOnVariant2()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPush()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPush().getBranches()).containsExactly("main");
            assertThat(on.getGithubWorkflowOnVariant2().getPullRequest()).isNotNull();
            assertThat(on.getGithubWorkflowOnVariant2().getPullRequest().getBranches())
                    .containsExactly("main");
            assertThat(on.getEvent()).isNull();
            assertThat(on.getList()).isNull();
        }

        @Test
        void deserializesSingleEvent() throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getEvent()).isEqualTo(Event.PUSH);
            assertThat(on.getList()).isNull();
            assertThat(on.getGithubWorkflowOnVariant2()).isNull();
        }

        @Test
        void allVariantsRoundTripViaJson() throws Exception {
            var singleEvent = yamlMapper.readValue(/* language=yaml */ """
                    name: A
                    on: push
                    jobs:
                      a:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo a
                    """, GithubWorkflow.class);

            var eventList = yamlMapper.readValue(/* language=yaml */ """
                    name: B
                    on: [push, pull_request]
                    jobs:
                      b:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo b
                    """, GithubWorkflow.class);

            var eventMap = yamlMapper.readValue(/* language=yaml */ """
                    name: C
                    on:
                      push:
                        branches: [main]
                    jobs:
                      c:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo c
                    """, GithubWorkflow.class);

            var eventMapAsJson = yamlMapper.readValue(/* language=yaml */ """
                    name: C
                    on: {push:{branches: ["main"]}}
                    jobs:
                      c:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo c
                    """, GithubWorkflow.class);

            for (var wf : List.of(singleEvent, eventList, eventMap, eventMapAsJson)) {
                var json = jsonMapper.writeValueAsString(wf);
                var roundTripped = jsonMapper.readValue(json, GithubWorkflow.class);
                assertThat(roundTripped).isEqualTo(wf);
            }
        }
    }

    @Nested
    class EventEnum {
        @Test
        void forValueResolvesKnownEvents() {
            assertThat(Event.forValue("push")).isEqualTo(Event.PUSH);
            assertThat(Event.forValue("pull_request")).isEqualTo(Event.PULL_REQUEST);
            assertThat(Event.forValue("workflow_dispatch")).isEqualTo(Event.WORKFLOW_DISPATCH);
            assertThat(Event.forValue("workflow_call")).isEqualTo(Event.WORKFLOW_CALL);
        }
    }

    @Nested
    class WorkflowFiles {
        private static final Path WORKFLOWS_DIR = Path.of("src/test/resources/workflows");

        static Stream<Path> workflowFiles() throws IOException {
            return Files.list(WORKFLOWS_DIR)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .sorted();
        }

        @ParameterizedTest
        @MethodSource("workflowFiles")
        void deserializesFromFile(Path file) throws Exception {
            var yaml = Files.readString(file);
            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);

            assertThat(wf.getName()).isNotNull();
            assertThat(wf.getOn()).isNotNull();
            assertThat(wf.getJobs()).isNotEmpty();
        }

        @ParameterizedTest
        @MethodSource("workflowFiles")
        void serializesToJsonAndBack(Path file) throws Exception {
            var yaml = Files.readString(file);
            var wf = yamlMapper.readValue(yaml, GithubWorkflow.class);

            var json = jsonMapper.writeValueAsString(wf);
            var roundTripped = jsonMapper.readValue(json, GithubWorkflow.class);

            assertThat(roundTripped.getName()).isEqualTo(wf.getName());
            assertThat(roundTripped.getOn()).isEqualTo(wf.getOn());
            assertThat(roundTripped.getJobs()).hasSameSizeAs(wf.getJobs());
        }
    }
}
