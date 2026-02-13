package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

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

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesRootProperties(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getName()).isEqualTo("CI");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesOnTriggerAsEventMap(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getGithubWorkflowOnVariant2()).isNotNull();

            var variant = on.getGithubWorkflowOnVariant2();
            assertThat(variant.getPush()).isNotNull();
            assertThat(variant.getPush().getBranches()).containsExactly("main");
            assertThat(variant.getPullRequest()).isNotNull();
            assertThat(variant.getPullRequest().getBranches()).containsExactly("main");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesJobsAsTypedMap(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getJobs()).hasSize(1).containsKey("build");

            var buildJob = wf.getJobs().get("build");
            assertThat(buildJob.getNormalJob()).isNotNull();
            assertThat(buildJob.getReusableWorkflowCallJob()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesNormalJobProperties(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var job = wf.getJobs().get("build").getNormalJob();

            assertThat(job.getName()).isEqualTo("Build and Test");
            assertThat(job.getRunsOn()).isEqualTo("ubuntu-latest");
            assertThat(job.getTimeoutMinutes()).isEqualTo(30);
            assertThat(job.getSteps()).hasSize(2);
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesWorkflowLevelEnv(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var env = wf.getEnv();
            assertThat(env.getMap()).containsEntry("CI", "true").containsEntry("NODE_ENV", "test");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void roundTripsViaJson(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var json = mp.jsonMapper().writeValueAsString(wf);
            var roundTripped = mp.jsonMapper().readValue(json, GithubWorkflow.class);
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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesOnTriggerAsSingleEvent(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getEvent()).isEqualTo(Event.WORKFLOW_DISPATCH);
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesReusableWorkflowCallJob(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            assertThat(wf.getJobs()).hasSize(1).containsKey("staging");

            var staging = wf.getJobs().get("staging");
            assertThat(staging.getReusableWorkflowCallJob()).isNotNull();
            assertThat(staging.getNormalJob()).isNull();

            var callJob = staging.getReusableWorkflowCallJob();
            assertThat(callJob.getUses()).isEqualTo("octo-org/octo-repo/.github/workflows/deploy.yml@main");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesReusableWorkflowWith(Mappers.MapperPair mp) throws Exception {
            var wf = mp.yamlMapper().readValue(YAML, GithubWorkflow.class);
            var callJob = wf.getJobs().get("staging").getReusableWorkflowCallJob();
            assertThat(callJob.getWith().getMap()).containsEntry("environment", "staging");
        }
    }

    @Nested
    class BuilderApi {
        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void canBuildWorkflowProgrammatically(Mappers.MapperPair mp) throws Exception {
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

            var json = mp.jsonMapper().writeValueAsString(wf);
            var deserialized = mp.jsonMapper().readValue(json, GithubWorkflow.class);
            assertThat(deserialized.getName()).isEqualTo("Test");
            assertThat(deserialized.getJobs()).containsKey("test");
            assertThat(deserialized.getJobs().get("test").getNormalJob().getRunsOn())
                    .isEqualTo("ubuntu-latest");
        }
    }

    @Nested
    class OnTriggerVariants {
        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesEventList(Mappers.MapperPair mp) throws Exception {
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

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getList()).containsExactly(Event.PUSH, Event.PULL_REQUEST);
            assertThat(on.getEvent()).isNull();
            assertThat(on.getGithubWorkflowOnVariant2()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesEventMap(Mappers.MapperPair mp) throws Exception {
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

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesEventMapAsJson(Mappers.MapperPair mp) throws Exception {
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

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
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

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesSingleEvent(Mappers.MapperPair mp) throws Exception {
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

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn();
            assertThat(on.getEvent()).isEqualTo(Event.PUSH);
            assertThat(on.getList()).isNull();
            assertThat(on.getGithubWorkflowOnVariant2()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void allVariantsRoundTripViaJson(Mappers.MapperPair mp) throws Exception {
            var singleEvent = mp.yamlMapper().readValue(/* language=yaml */ """
                    name: A
                    on: push
                    jobs:
                      a:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo a
                    """, GithubWorkflow.class);

            var eventList = mp.yamlMapper().readValue(/* language=yaml */ """
                    name: B
                    on: [push, pull_request]
                    jobs:
                      b:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo b
                    """, GithubWorkflow.class);

            var eventMap = mp.yamlMapper().readValue(/* language=yaml */ """
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

            var eventMapAsJson = mp.yamlMapper().readValue(/* language=yaml */ """
                    name: C
                    on: {push:{branches: ["main"]}}
                    jobs:
                      c:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo c
                    """, GithubWorkflow.class);

            for (var wf : List.of(singleEvent, eventList, eventMap, eventMapAsJson)) {
                var json = mp.jsonMapper().writeValueAsString(wf);
                var roundTripped = mp.jsonMapper().readValue(json, GithubWorkflow.class);
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
            for (var mp : Mappers.mappers().toList()) {
                var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);

                assertThat(wf.getName())
                        .as("%s with %s", file.getFileName(), mp)
                        .isNotNull();
                assertThat(wf.getOn()).as("%s with %s", file.getFileName(), mp).isNotNull();
                assertThat(wf.getJobs())
                        .as("%s with %s", file.getFileName(), mp)
                        .isNotEmpty();
            }
        }

        @ParameterizedTest
        @MethodSource("workflowFiles")
        void serializesToJsonAndBack(Path file) throws Exception {
            var yaml = Files.readString(file);
            for (var mp : Mappers.mappers().toList()) {
                var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);

                var json = mp.jsonMapper().writeValueAsString(wf);
                var roundTripped = mp.jsonMapper().readValue(json, GithubWorkflow.class);

                assertThat(roundTripped.getName())
                        .as("%s with %s", file.getFileName(), mp)
                        .isEqualTo(wf.getName());
                assertThat(roundTripped.getOn())
                        .as("%s with %s", file.getFileName(), mp)
                        .isEqualTo(wf.getOn());
                assertThat(roundTripped.getJobs())
                        .as("%s with %s", file.getFileName(), mp)
                        .hasSameSizeAs(wf.getJobs());
            }
        }
    }
}
