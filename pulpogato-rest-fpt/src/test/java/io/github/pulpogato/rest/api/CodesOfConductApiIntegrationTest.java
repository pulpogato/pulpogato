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
        CodesOfConductApi api = new RestClients(webClient).getCodesOfConductApi();
        ResponseEntity<List<CodeOfConduct>> response = api.getAllCodesOfConduct();
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<CodeOfConduct> codesOfConduct = response.getBody();
        assertThat(codesOfConduct)
                .isNotEmpty()
                .hasSize(2);
        
        // Verify structure of codes of conduct
        CodeOfConduct firstCode = codesOfConduct.getFirst();
        assertThat(firstCode.getKey()).isEqualTo("django");
        assertThat(firstCode.getName()).isEqualTo("Django");
        assertThat(firstCode.getUrl().toString()).isEqualTo("https://api.github.com/codes_of_conduct/django");
        
        // Check for common codes of conduct
        List<String> codeKeys = codesOfConduct.stream()
                .map(CodeOfConduct::getKey)
                .toList();
        
        // Common codes that GitHub provides (using actual key format)
        assertThat(codeKeys)
                .contains("django", "contributor_covenant");
    }

    @Test
    void testGetContributorCovenantCodeOfConduct() {
        CodesOfConductApi api = new RestClients(webClient).getCodesOfConductApi();
        ResponseEntity<CodeOfConduct> response = api.getConductCode("contributor_covenant");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(CodeOfConduct.class);
        
        CodeOfConduct codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("contributor_covenant");
        assertThat(codeOfConduct.getName()).contains("Contributor Covenant");
        assertThat(codeOfConduct.getUrl().toString()).isEqualTo("https://api.github.com/codes_of_conduct/contributor_covenant");
        assertThat(codeOfConduct.getBody())
                .isNotNull()
                .contains("Our Pledge")
                .contains("community");
    }

    @Test
    void testGetCitizenCodeOfConduct() {
        CodesOfConductApi api = new RestClients(webClient).getCodesOfConductApi();
        ResponseEntity<CodeOfConduct> response = api.getConductCode("citizen_code_of_conduct");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(CodeOfConduct.class);
        
        CodeOfConduct codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("citizen_code_of_conduct");
        assertThat(codeOfConduct.getName()).contains("Citizen Code");
        assertThat(codeOfConduct.getUrl().toString()).isEqualTo("https://api.github.com/codes_of_conduct/citizen_code_of_conduct");
        assertThat(codeOfConduct.getBody())
                .isNotNull()
                .isNotEmpty();
    }
}