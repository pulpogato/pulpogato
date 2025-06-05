package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.CodeOfConduct;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodesOfConductApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetAllCodesOfConduct() {
        CodesOfConductApi api = factory.createClient(CodesOfConductApi.class);
        ResponseEntity<List<CodeOfConduct>> response = api.getAllCodesOfConduct();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<CodeOfConduct> codesOfConduct = response.getBody();
        assertThat(codesOfConduct)
                .isNotEmpty()
                .hasSizeGreaterThan(0);
        
        // Verify structure of codes of conduct
        CodeOfConduct firstCode = codesOfConduct.getFirst();
        assertThat(firstCode.getKey()).isNotNull();
        assertThat(firstCode.getName()).isNotNull();
        assertThat(firstCode.getUrl()).isNotNull();
        
        // Check for common codes of conduct
        List<String> codeKeys = codesOfConduct.stream()
                .map(CodeOfConduct::getKey)
                .toList();
        
        // Common codes that GitHub provides (using actual key format)
        assertThat(codeKeys)
                .containsAnyOf("contributor_covenant", "citizen_code_of_conduct");
    }

    @Test
    void testGetContributorCovenantCodeOfConduct() {
        CodesOfConductApi api = factory.createClient(CodesOfConductApi.class);
        ResponseEntity<CodeOfConduct> response = api.getConductCode("contributor_covenant");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(CodeOfConduct.class);
        
        CodeOfConduct codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("contributor_covenant");
        assertThat(codeOfConduct.getName()).contains("Contributor Covenant");
        assertThat(codeOfConduct.getUrl()).isNotNull();
        assertThat(codeOfConduct.getBody())
                .isNotNull()
                .contains("Our Pledge")
                .contains("community");
    }

    @Test
    void testGetCitizenCodeOfConduct() {
        CodesOfConductApi api = factory.createClient(CodesOfConductApi.class);
        ResponseEntity<CodeOfConduct> response = api.getConductCode("citizen_code_of_conduct");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(CodeOfConduct.class);
        
        CodeOfConduct codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("citizen_code_of_conduct");
        assertThat(codeOfConduct.getName()).contains("Citizen Code");
        assertThat(codeOfConduct.getUrl()).isNotNull();
        assertThat(codeOfConduct.getBody())
                .isNotNull()
                .isNotEmpty();
    }
}