package io.github.pulpogato.rest.api.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import io.github.pulpogato.rest.schemas.CodeOfConduct;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodesOfConductApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGetAllCodesOfConduct() {
        var api = new RestClients(webClient).getCodesOfConductApi();
        var response = api.getAllCodesOfConduct().block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var codesOfConduct = response.getBody();
        assertThat(codesOfConduct).isNotEmpty().hasSize(2);

        // Verify structure of codes of conduct
        var firstCode = codesOfConduct.getFirst();
        assertThat(firstCode.getKey()).isEqualTo("django");
        assertThat(firstCode.getName()).isEqualTo("Django");
        assertThat(firstCode.getUrl().toString()).isEqualTo("https://api.github.com/codes_of_conduct/django");

        // Check for common codes of conduct
        var codeKeys = codesOfConduct.stream().map(CodeOfConduct::getKey).toList();

        // Common codes that GitHub provides (using actual key format)
        assertThat(codeKeys).contains("django", "contributor_covenant");
    }

    @Test
    void testGetContributorCovenantCodeOfConduct() {
        var api = new RestClients(webClient).getCodesOfConductApi();
        var response = api.getConductCode("contributor_covenant").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(CodeOfConduct.class);

        var codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("contributor_covenant");
        assertThat(codeOfConduct.getName()).contains("Contributor Covenant");
        assertThat(codeOfConduct.getUrl().toString())
                .isEqualTo("https://api.github.com/codes_of_conduct/contributor_covenant");
        assertThat(codeOfConduct.getBody()).isNotNull().contains("Our Pledge").contains("community");
    }

    @Test
    void testGetCitizenCodeOfConduct() {
        var api = new RestClients(webClient).getCodesOfConductApi();
        var response = api.getConductCode("citizen_code_of_conduct").block();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(CodeOfConduct.class);

        var codeOfConduct = response.getBody();
        assertThat(codeOfConduct.getKey()).isEqualTo("citizen_code_of_conduct");
        assertThat(codeOfConduct.getName()).contains("Citizen Code");
        assertThat(codeOfConduct.getUrl().toString())
                .isEqualTo("https://api.github.com/codes_of_conduct/citizen_code_of_conduct");
        assertThat(codeOfConduct.getBody()).isNotNull().isNotEmpty();
    }
}
