package io.github.pulpogato.rest.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.ObjectMapper;
import io.github.pulpogato.common.SingularOrPlural;
import io.github.pulpogato.rest.schemas.ContentFile;
import io.github.pulpogato.rest.schemas.CustomPropertyValue;
import io.github.pulpogato.rest.schemas.FullRepository;
import io.github.pulpogato.test.BaseIntegrationTest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReposApiIntegrationTest extends BaseIntegrationTest {
    @Test
    void testListTags() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.listTags("pulpogato", "pulpogato", 100L, 1L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isNotEmpty();
        var tags = response.getBody();
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).getName()).isEqualTo("v0.2.0");
        assertThat(tags.get(1).getName()).isEqualTo("v0.1.0");
    }

    @Test
    void testListCommits() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.listCommits("pulpogato", "pulpogato",
                null, null, null, null, null, null, 10L, 1L);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isNotEmpty();
        var commits = response.getBody();
        assertThat(commits).hasSize(10);
        var commit = commits.getFirst();
        assertThat(commit.getSha()).isEqualTo("2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
        assertThat(commit.getCommit().getMessage()).isEqualTo("Merge pull request #206 from pulpogato/test-listOrgApps\n\ntest: Add test for listAppInstallations in an org");
        assertThat(commit.getCommitter().getSimpleUser().getLogin()).isEqualTo("web-flow");
        assertThat(commit.getAuthor().getSimpleUser().getLogin()).isEqualTo("rahulsom");
    }

    @Test
    void testGetCommit() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.getCommit("pulpogato", "pulpogato", 1L, 1L, "2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var commit = response.getBody();
        assertThat(commit.getSha()).isEqualTo("2667e9ae0adcdbf378fe6273658b57f4e5d24a39");
    }

    @Test
    void testGetContentObject() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.getContentObject("pulpogato", "pulpogato", "README.adoc", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("README.adoc");
        assertThat(body.getType()).isEqualTo("file");
        assertThat(body.getPath()).isEqualTo("README.adoc");

        var content = body.getContent().replace("\n", "");
        assertThat(content).isNotNull();
        var decoded = new String(Base64.getDecoder().decode(content));
        assertThat(decoded).startsWith("= Pulpogato");
    }

    @Test
    void testGetContent() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.getContent("pulpogato", "pulpogato", "README.adoc", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getContentFile().getName()).isEqualTo("README.adoc");
        assertThat(body.getContentFile().getType()).isEqualTo(ContentFile.Type.FILE);
        assertThat(body.getContentFile().getPath()).isEqualTo("README.adoc");

        var content = body.getContentFile().getContent().replace("\n", "");
        assertThat(content).isNotNull();
        var decoded = new String(Base64.getDecoder().decode(content));
        assertThat(decoded).startsWith("= Pulpogato");
    }

    @Test
    void testGet() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.get("pulpogato", "pulpogato");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("pulpogato");
        assertThat(body.getFullName()).isEqualTo("pulpogato/pulpogato");
        assertThat(body.getOwner().getLogin()).isEqualTo("pulpogato");
    }

    @Test
    void testGetBranch() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.getBranch("pulpogato", "pulpogato", "main");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("main");
        assertThat(body.getIsProtected()).isTrue();
    }

    @Test
    void testGetBranchProtection() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.getBranchProtection("pulpogato", "pulpogato", "main");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getRequiredStatusChecks().getContexts()).containsExactly("jenkins/pulpogato");
    }

    @Test
    void testCreateRepositoryInOrg() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.createInOrg("pulpogato", ReposApi.CreateInOrgRequestBody.builder()
                .name("create-demo")
                .description("create demo")
                .homepage("https://github.com/pulpogato/create-demo")
                .build());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("create-demo");
        assertThat(body.getFullName()).isEqualTo("pulpogato/create-demo");
        assertThat(body.getOwner().getLogin()).isEqualTo("pulpogato");
    }

    @Test
    void testCreateRepositoryInOrgWithCustomProperties() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.createInOrg("example", ReposApi.CreateInOrgRequestBody.builder()
                .name("rsomasunderam-custom-props-demo")
                .description("create demo")
                .customProperties(Map.of("custom_boolean_prop", "false"))
                .build());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull();
        var body = response.getBody();
        assertThat(body.getName()).isEqualTo("rsomasunderam-custom-props-demo");
        assertThat(body.getFullName()).isEqualTo("example/rsomasunderam-custom-props-demo");
        assertThat(body.getOwner().getLogin()).isEqualTo("example");
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    static class TestCustomProperties {
        private Boolean customBooleanProp;

        public Map<String, Object> toMap() {
            return Map.ofEntries(
                    Map.entry("custom_boolean_prop", this.customBooleanProp)
            );
        }

        public static TestCustomProperties fromMap(Map<String, Object> map) {
            return TestCustomProperties.builder()
                    .customBooleanProp((Boolean) map.get("custom_boolean_prop"))
                    .build();
        }
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @Setter
    static class ExtendedCreateInOrgRequestBody extends ReposApi.CreateInOrgRequestBody {
        @JsonIgnore
        private TestCustomProperties typedCustomProperties;

        public ExtendedCreateInOrgRequestBody normalize() {
            return this.toBuilder()
                    .customProperties(typedCustomProperties.toMap())
                    .build();
        }
    }

    @Getter
    @Setter
    static class ExtendedFullRepository extends FullRepository {
        @JsonIgnore
        public TestCustomProperties getTypedCustomProperties() {
            return TestCustomProperties.fromMap(this.getCustomProperties());
        }
    }

    @Test
    void testCreateRepositoryInOrgWithExtendedCustomProperties() {
        var api = new RestClients(webClient).getReposApi();
        var response = api.createInOrg("example", ExtendedCreateInOrgRequestBody.builder()
                .name("rsomasunderam-custom-props-demo")
                .description("create demo")
                .typedCustomProperties(TestCustomProperties.builder()
                        .customBooleanProp(false)
                        .build())
                .build().normalize());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(FullRepository.class);

        var objectMapper = new ObjectMapper();
        var body = objectMapper.readValue(objectMapper.writeValueAsString(response.getBody()),
                ExtendedFullRepository.class);

        assertThat(body.getName()).isEqualTo("rsomasunderam-custom-props-demo");
        assertThat(body.getFullName()).isEqualTo("example/rsomasunderam-custom-props-demo");
        assertThat(body.getOwner().getLogin()).isEqualTo("example");
        assertThat(body.getTypedCustomProperties().getCustomBooleanProp()).isFalse();
    }

    @Test
    void testCreateOrUpdateCustomPropertiesValues() {
        var api = new RestClients(webClient).getReposApi();

        var customPropertyValue = CustomPropertyValue.builder()
                .propertyName("audit_pci")
                .value(SingularOrPlural.singular("out_of_scope"))
                .build();

        var requestBody = ReposApi.CustomPropertiesForReposCreateOrUpdateRepositoryValuesRequestBody.builder()
                .properties(List.of(customPropertyValue))
                .build();

        var response = api.customPropertiesForReposCreateOrUpdateRepositoryValues("corp", "rsomasunderam-test", requestBody);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void testCreateOrUpdateCustomPropertiesValuesWithMultipleProperties() {
        var api = new RestClients(webClient).getReposApi();

        var environmentProperty = CustomPropertyValue.builder()
                .propertyName("some_string")
                .value(SingularOrPlural.singular("string value"))
                .build();

        var teamProperty = CustomPropertyValue.builder()
                .propertyName("audit_sox")
                .value(SingularOrPlural.singular("in_scope"))
                .build();

        var requestBody = ReposApi.CustomPropertiesForReposCreateOrUpdateRepositoryValuesRequestBody.builder()
                .properties(List.of(environmentProperty, teamProperty))
                .build();

        var response = api.customPropertiesForReposCreateOrUpdateRepositoryValues("corp", "rsomasunderam-test", requestBody);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void testCreateOrUpdateCustomPropertiesValuesWithArrayValue() {
        var api = new RestClients(webClient).getReposApi();

        var tagsProperty = CustomPropertyValue.builder()
                .propertyName("plural_value")
                .value(SingularOrPlural.plural(List.of("apple", "banana")))
                .build();

        var requestBody = ReposApi.CustomPropertiesForReposCreateOrUpdateRepositoryValuesRequestBody.builder()
                .properties(List.of(tagsProperty))
                .build();

        var response = api.customPropertiesForReposCreateOrUpdateRepositoryValues("corp", "rsomasunderam-test", requestBody);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void testCreateOrUpdateCustomPropertiesValuesEmptyProperties() {
        var api = new RestClients(webClient).getReposApi();
        var requestBody = ReposApi.CustomPropertiesForReposCreateOrUpdateRepositoryValuesRequestBody.builder()
                .properties(List.of())
                .build();

        var response = api.customPropertiesForReposCreateOrUpdateRepositoryValues("corp", "rsomasunderam-test", requestBody);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
