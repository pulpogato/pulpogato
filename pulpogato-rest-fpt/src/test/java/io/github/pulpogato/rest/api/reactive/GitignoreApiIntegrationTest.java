package io.github.pulpogato.rest.api.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import io.github.pulpogato.rest.schemas.GitignoreTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitignoreApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGetAllTemplates() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getAllTemplates().block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var templates = response.getBody();
        assertThat(templates).isNotEmpty().contains("Java", "Python", "Node");
    }

    @Test
    void testGetJavaTemplate() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getTemplate("Java").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(GitignoreTemplate.class);

        var template = response.getBody();
        assertThat(template.getName()).isEqualTo("Java");
        assertThat(template.getSource()).isNotNull().contains("*.class").contains("*.jar");
    }

    @Test
    void testGetPythonTemplate() {
        var api = new RestClients(webClient).getGitignoreApi();
        var response = api.getTemplate("Python").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(GitignoreTemplate.class);

        var template = response.getBody();
        assertThat(template.getName()).isEqualTo("Python");
        assertThat(template.getSource()).isNotNull().contains("__pycache__").contains("*.py[cod]");
    }
}
