package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.GitignoreTemplate;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitignoreApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetAllTemplates() {
        GitignoreApi api = factory.createClient(GitignoreApi.class);
        ResponseEntity<List<String>> response = api.getAllTemplates();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<String> templates = response.getBody();
        assertThat(templates)
                .isNotEmpty()
                .contains("Java", "Python", "Node");
    }

    @Test
    void testGetJavaTemplate() {
        GitignoreApi api = factory.createClient(GitignoreApi.class);
        ResponseEntity<GitignoreTemplate> response = api.getTemplate("Java");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(GitignoreTemplate.class);
        
        GitignoreTemplate template = response.getBody();
        assertThat(template.getName()).isEqualTo("Java");
        assertThat(template.getSource())
                .isNotNull()
                .contains("*.class")
                .contains("*.jar");
    }

    @Test
    void testGetPythonTemplate() {
        GitignoreApi api = factory.createClient(GitignoreApi.class);
        ResponseEntity<GitignoreTemplate> response = api.getTemplate("Python");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(GitignoreTemplate.class);
        
        GitignoreTemplate template = response.getBody();
        assertThat(template.getName()).isEqualTo("Python");
        assertThat(template.getSource())
                .isNotNull()
                .contains("__pycache__")
                .contains("*.py[cod]");
    }
}