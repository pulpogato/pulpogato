package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.common.annotations.Generated;
import io.github.pulpogato.githubfiles.workflows.Env;
import io.github.pulpogato.githubfiles.workflows.Event;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflow;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflowJobsValue;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflowOn;
import io.github.pulpogato.githubfiles.workflows.NormalJob;
import io.github.pulpogato.githubfiles.workflows.NormalJobRunsOn;
import io.github.pulpogato.githubfiles.workflows.Step;
import io.github.pulpogato.githubfiles.workflows.StepTimeoutMinutes;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GithubWorkflowDeserializationTest {

    static Stream<Mappers.MapperPair> mappers() {
        return Mappers.mappers();
    }

    @Test
    void generatedAnnotationsIncludeSchemaRefs() throws Exception {
        var stepAnnotation = Step.class.getAnnotation(Generated.class);
        assertThat(stepAnnotation).isNotNull();
        assertThat(stepAnnotation.schemaRef()).isEqualTo("#/definitions/step");

        var timeoutField = Step.class.getDeclaredField("timeoutMinutes");
        var timeoutFieldAnnotation = timeoutField.getAnnotation(Generated.class);
        assertThat(timeoutFieldAnnotation).isNotNull();
        assertThat(timeoutFieldAnnotation.schemaRef()).isEqualTo("#/definitions/step/properties/timeout-minutes");

        var timeoutTypeAnnotation = StepTimeoutMinutes.class.getAnnotation(Generated.class);
        assertThat(timeoutTypeAnnotation).isNotNull();
        assertThat(timeoutTypeAnnotation.schemaRef()).isEqualTo("#/definitions/step/properties/timeout-minutes");

        var envMapField = Env.class.getDeclaredField("map");
        var envMapFieldAnnotation = envMapField.getAnnotation(Generated.class);
        assertThat(envMapFieldAnnotation).isNotNull();
        assertThat(envMapFieldAnnotation.schemaRef()).isEqualTo("#/definitions/env/oneOf/0");

        var envStringField = Env.class.getDeclaredField("string");
        var envStringFieldAnnotation = envStringField.getAnnotation(Generated.class);
        assertThat(envStringFieldAnnotation).isNotNull();
        assertThat(envStringFieldAnnotation.schemaRef()).isEqualTo("#/definitions/env/oneOf/1");
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
            assertThat(job.getRunsOn().getString()).isEqualTo("ubuntu-latest");
            assertThat(job.getTimeoutMinutes().getBigDecimal()).isEqualByComparingTo(BigDecimal.valueOf(30));
            assertThat(job.getTimeoutMinutes().getString()).isNull();
            assertThat(job.getSteps()).hasSize(2);
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesNormalJobWithUnknownJobLevelKeys(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on:
                      pull_request:
                        branches: [main]
                    jobs:
                      test:
                        runs-on: self-hosted
                        customField: ignored
                        steps:
                          - uses: actions/checkout@v4
                          - uses: actions/setup-java@v4
                            with:
                              java-version: "21"
                          - run: ./gradlew test
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var job = wf.getJobs().get("test");
            assertThat(job.getNormalJob()).isNotNull();
            assertThat(job.getReusableWorkflowCallJob()).isNull();
            assertThat(job.getNormalJob().getRunsOn().getString()).isEqualTo("self-hosted");
            assertThat(job.getNormalJob().getSteps())
                    .extracting(Step::getUses)
                    .contains("actions/checkout@v4", "actions/setup-java@v4");
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesRunsOnList(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: [self-hosted, linux]
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var runsOn = wf.getJobs().get("build").getNormalJob().getRunsOn();
            assertThat(runsOn.getList()).containsExactly("self-hosted", "linux");
            assertThat(runsOn.getString()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesRunsOnRunnerGroupWithSingleLabel(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on:
                          group: ubuntu-runners
                          labels: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var runsOn = wf.getJobs().get("build").getNormalJob().getRunsOn();
            assertThat(runsOn.getNormalJobRunsOnVariant2().getGroup()).isEqualTo("ubuntu-runners");
            assertThat(runsOn.getNormalJobRunsOnVariant2().getLabels().getString())
                    .isEqualTo("ubuntu-latest");
            assertThat(runsOn.getNormalJobRunsOnVariant2().getLabels().getList())
                    .isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesRunsOnRunnerGroupWithLabelList(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on:
                          group: ubuntu-runners
                          labels: [self-hosted, linux]
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var runsOn = wf.getJobs().get("build").getNormalJob().getRunsOn();
            assertThat(runsOn.getNormalJobRunsOnVariant2().getGroup()).isEqualTo("ubuntu-runners");
            assertThat(runsOn.getNormalJobRunsOnVariant2().getLabels().getList())
                    .containsExactly("self-hosted", "linux");
            assertThat(runsOn.getNormalJobRunsOnVariant2().getLabels().getString())
                    .isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesStepTimeoutMinutesAsNumber(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                            timeout-minutes: 5
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var stepTimeout =
                    wf.getJobs().get("build").getNormalJob().getSteps().get(0).getTimeoutMinutes();
            assertThat(stepTimeout.getBigDecimal()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(stepTimeout.getString()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesStepTimeoutMinutesAsExpressionString(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                            timeout-minutes: "${{ fromJSON(vars.STEP_TIMEOUT) }}"
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var stepTimeout =
                    wf.getJobs().get("build").getNormalJob().getSteps().get(0).getTimeoutMinutes();
            assertThat(stepTimeout.getString()).isEqualTo("${{ fromJSON(vars.STEP_TIMEOUT) }}");
            assertThat(stepTimeout.getBigDecimal()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesContainerPortsAsTypedUnion(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        container:
                          image: node:20
                          ports: [5432, "8080:80"]
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var ports = wf.getJobs()
                    .get("build")
                    .getNormalJob()
                    .getContainer()
                    .getContainer()
                    .getPorts();
            assertThat(ports).hasSize(2);
            assertThat(ports.get(0).getBigDecimal()).isEqualByComparingTo(BigDecimal.valueOf(5432));
            assertThat(ports.get(0).getString()).isNull();
            assertThat(ports.get(1).getString()).isEqualTo("8080:80");
            assertThat(ports.get(1).getBigDecimal()).isNull();
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
        void deserializesStrategyFailFastAsBoolean(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        strategy:
                          fail-fast: true
                          matrix:
                            java: [21]
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var failFast =
                    wf.getJobs().get("build").getNormalJob().getStrategy().getFailFast();
            assertThat(failFast.getBoolean_()).isTrue();
            assertThat(failFast.getString()).isNull();
        }

        @ParameterizedTest
        @MethodSource("io.github.pulpogato.githubfiles.GithubWorkflowDeserializationTest#mappers")
        void deserializesStrategyFailFastAsExpressionString(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on: push
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        strategy:
                          fail-fast: "${{ startsWith(github.ref, 'refs/tags/') }}"
                          matrix:
                            java: [21]
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var failFast =
                    wf.getJobs().get("build").getNormalJob().getStrategy().getFailFast();
            assertThat(failFast.getString()).isEqualTo("${{ startsWith(github.ref, 'refs/tags/') }}");
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
        void deserializesReusableWorkflowCallJobWithUnknownJobLevelKeys(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: Deploy
                    on: workflow_dispatch
                    jobs:
                      staging:
                        uses: octo-org/octo-repo/.github/workflows/deploy.yml@main
                        customField: ignored
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var staging = wf.getJobs().get("staging");
            assertThat(staging.getReusableWorkflowCallJob()).isNotNull();
            assertThat(staging.getNormalJob()).isNull();
            assertThat(staging.getReusableWorkflowCallJob().getUses())
                    .isEqualTo("octo-org/octo-repo/.github/workflows/deploy.yml@main");
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
                                            .runsOn(NormalJobRunsOn.builder()
                                                    .string("ubuntu-latest")
                                                    .build())
                                            .steps(List.of(Step.builder()
                                                    .run("echo hello")
                                                    .build()))
                                            .build())
                                    .build()))
                    .build();

            var json = mp.jsonMapper().writeValueAsString(wf);
            var deserialized = mp.jsonMapper().readValue(json, GithubWorkflow.class);
            assertThat(deserialized.getName()).isEqualTo("Test");
            assertThat(deserialized.getJobs()).containsKey("test");
            assertThat(deserialized
                            .getJobs()
                            .get("test")
                            .getNormalJob()
                            .getRunsOn()
                            .getString())
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
        void deserializesEventMapWithBareKeys(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on:
                      push:
                        branches: [main]
                      pull_request:
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
            assertThat(on.getGithubWorkflowOnVariant2().getPullRequest()).isNotNull();
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
        void deserializesTypedIssueCommentEventConfig(Mappers.MapperPair mp) throws Exception {
            @Language("yaml")
            var yaml = """
                    name: CI
                    on:
                      issue_comment:
                        types: [created, edited]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - run: echo hello
                    """;

            var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);
            var on = wf.getOn().getGithubWorkflowOnVariant2();
            assertThat(on.getIssueComment()).isNotNull();
            assertThat(on.getIssueComment().getTypes()).isNotNull();
            assertThat(on.getIssueComment().getTypes().getList()).containsExactly("created", "edited");
            assertThat(on.getIssueComment().getTypes().getString()).isNull();
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
        static Stream<String> workflowResources() {
            try {
                var resourceNames = new ArrayList<String>();
                var urls = WorkflowFiles.class.getClassLoader().getResources("workflows");
                while (urls.hasMoreElements()) {
                    var url = urls.nextElement();
                    if ("file".equals(url.getProtocol())) {
                        try (var paths = Files.list(Path.of(url.toURI()))) {
                            paths.filter(path -> path.toString().endsWith(".yml")
                                            || path.toString().endsWith(".yaml"))
                                    .map(path -> "workflows/" + path.getFileName())
                                    .forEach(resourceNames::add);
                        }
                        continue;
                    }
                    if ("jar".equals(url.getProtocol())) {
                        var connection = (JarURLConnection) url.openConnection();
                        var prefix = connection.getEntryName() + "/";
                        try (var jar = connection.getJarFile()) {
                            jar.stream()
                                    .filter(entry -> !entry.isDirectory())
                                    .map(ZipEntry::getName)
                                    .filter(name -> name.startsWith(prefix))
                                    .filter(name -> name.endsWith(".yml") || name.endsWith(".yaml"))
                                    .forEach(resourceNames::add);
                        }
                    }
                }
                return resourceNames.stream().distinct().sorted();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to list workflow resources", e);
            }
        }

        private static String readWorkflowResource(String resourceName) throws IOException {
            try (var inputStream = WorkflowFiles.class.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IOException("Missing workflow resource: " + resourceName);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @ParameterizedTest
        @MethodSource("workflowResources")
        void deserializesFromResource(String resourceName) throws Exception {
            var yaml = readWorkflowResource(resourceName);
            for (var mp : Mappers.mappers().toList()) {
                var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);

                assertThat(wf.getName()).as("%s with %s", resourceName, mp).isNotNull();
                assertThat(wf.getOn()).as("%s with %s", resourceName, mp).isNotNull();
                assertThat(wf.getJobs()).as("%s with %s", resourceName, mp).isNotEmpty();
            }
        }

        @ParameterizedTest
        @MethodSource("workflowResources")
        void serializesToJsonAndBack(String resourceName) throws Exception {
            var yaml = readWorkflowResource(resourceName);
            for (var mp : Mappers.mappers().toList()) {
                var wf = mp.yamlMapper().readValue(yaml, GithubWorkflow.class);

                var json = mp.jsonMapper().writeValueAsString(wf);
                var roundTripped = mp.jsonMapper().readValue(json, GithubWorkflow.class);

                assertThat(roundTripped.getName())
                        .as("%s with %s", resourceName, mp)
                        .isEqualTo(wf.getName());
                assertThat(roundTripped.getOn())
                        .as("%s with %s", resourceName, mp)
                        .isEqualTo(wf.getOn());
                assertThat(roundTripped.getJobs())
                        .as("%s with %s", resourceName, mp)
                        .hasSameSizeAs(wf.getJobs());
            }
        }
    }
}
