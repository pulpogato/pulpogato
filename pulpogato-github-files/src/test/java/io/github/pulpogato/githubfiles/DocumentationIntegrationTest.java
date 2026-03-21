package io.github.pulpogato.githubfiles;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.githubfiles.actions.GithubAction;
import io.github.pulpogato.githubfiles.releases.GithubReleaseConfig;
import io.github.pulpogato.githubfiles.workflows.Event;
import io.github.pulpogato.githubfiles.workflows.GithubWorkflow;
import org.junit.jupiter.api.Test;

class DocumentationIntegrationTest {

    @Test
    void jackson2WorkflowExample() throws Exception {
        // tag::github-files-jackson2[]
        var yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        var workflowYaml = """
                name: CI
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - run: ./gradlew test
                """;

        var workflow = yamlMapper.readValue(workflowYaml, GithubWorkflow.class);
        var workflowName = workflow.getName();
        // end::github-files-jackson2[]

        assertThat(workflowName).isEqualTo("CI");
    }

    @Test
    void jackson3ActionExample() {
        // tag::github-files-jackson3[]
        var yamlMapper = tools.jackson.dataformat.yaml.YAMLMapper.builder().build();

        var actionYaml = """
                name: Setup Java
                description: Setup JDK
                runs:
                  using: node20
                  main: index.js
                """;

        var action = yamlMapper.readValue(actionYaml, GithubAction.class);
        var actionName = action.getName();
        // end::github-files-jackson3[]

        assertThat(actionName).isEqualTo("Setup Java");
    }

    @Test
    void releaseDrafterExample() throws Exception {
        // tag::github-files-release[]
        var yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        var releaseYaml = """
                changelog:
                  categories:
                    - title: Features
                      labels: [feature]
                """;

        var releaseConfig = yamlMapper.readValue(releaseYaml, GithubReleaseConfig.class);
        var firstCategory = releaseConfig.getChangelog().getCategories().get(0).getTitle();
        // end::github-files-release[]

        assertThat(firstCategory).isEqualTo("Features");
    }

    @Test
    void workflowOnUnionExample() throws Exception {
        var yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        var workflowYaml = """
                name: CI
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo hello
                """;

        var workflow = yamlMapper.readValue(workflowYaml, GithubWorkflow.class);

        // tag::github-files-on-union[]
        var on = workflow.getOn();
        if (on.getEvent() != null) {
            assertThat(on.getEvent()).isEqualTo(Event.PUSH);
        } else if (on.getList() != null) {
            assertThat(on.getList()).contains(Event.PUSH);
        } else if (on.getGithubWorkflowOnVariant2() != null) {
            assertThat(on.getGithubWorkflowOnVariant2()).isNotNull();
        }
        // end::github-files-on-union[]
    }

    @Test
    void discussionExample() throws Exception {
        // tag::github-files-discussion[]
        var yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        var discussionYaml = """
                title: New Feature Idea
                body:
                  - type: input
                    attributes:
                      label: Idea name
                      placeholder: My cool idea
                """;

        var discussion =
                yamlMapper.readValue(discussionYaml, io.github.pulpogato.githubfiles.discussion.GithubDiscussion.class);
        var title = discussion.getTitle();
        // end::github-files-discussion[]

        assertThat(title).isEqualTo("New Feature Idea");
    }

    @Test
    void issueConfigExample() throws Exception {
        // tag::github-files-issue-config[]
        var yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        var issueConfigYaml = """
                blank_issues_enabled: false
                contact_links:
                  - name: Community Support
                    url: https://community.example.com
                    about: Get help here
                """;

        var issueConfig = yamlMapper.readValue(
                issueConfigYaml, io.github.pulpogato.githubfiles.issueconfig.GithubIssueConfig.class);
        var enabled = issueConfig.getBlankIssuesEnabled();
        // end::github-files-issue-config[]

        assertThat(enabled).isFalse();
    }
}
