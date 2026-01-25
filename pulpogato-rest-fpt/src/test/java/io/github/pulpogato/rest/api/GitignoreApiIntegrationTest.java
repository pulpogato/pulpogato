package io.github.pulpogato.rest.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.schemas.GitignoreTemplate;
import io.github.pulpogato.test.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitignoreApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetAllTemplates() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getAllTemplates();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var templates = response.getBody();
        assertThat(templates).isNotEmpty().contains("Java", "Python", "Node");
    }

    @Test
    void testGetJavaTemplate() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getTemplate("Java");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(GitignoreTemplate.class);

        var template = response.getBody();
        assertThat(template.getName()).isEqualTo("Java");
        assertThat(template.getSource()).isNotNull().contains("*.class").contains("*.jar");
    }

    @Test
    void testGetPythonTemplate() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getTemplate("Python");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(GitignoreTemplate.class);

        var template = response.getBody();
        assertThat(template.getName()).isEqualTo("Python");
        assertThat(template.getSource()).isNotNull().contains("__pycache__").contains("*.py[cod]");
    }
}
