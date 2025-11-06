package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.LicenseSimple;
import io.github.pulpogato.rest.schemas.License;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LicensesApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetAllCommonlyUsed() {
        LicensesApi api = new RestClients(webClient).getLicensesApi();
        ResponseEntity<List<LicenseSimple>> response = api.getAllCommonlyUsed(null, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<LicenseSimple> licenses = response.getBody();
        assertThat(licenses)
                .isNotEmpty()
                .hasSizeGreaterThan(5);
        
        // Verify common licenses are present
        List<String> licenseKeys = licenses.stream()
                .map(LicenseSimple::getKey)
                .toList();
        
        assertThat(licenseKeys)
                .contains("mit", "apache-2.0", "gpl-3.0");
        
        // Verify structure of first license
        LicenseSimple firstLicense = licenses.getFirst();
        assertThat(firstLicense.getKey()).isEqualTo("agpl-3.0");
        assertThat(firstLicense.getName()).isEqualTo("GNU Affero General Public License v3.0");
        assertThat(firstLicense.getUrl()).isNotNull();
        assertThat(firstLicense.getUrl().toString()).isEqualTo("https://api.github.com/licenses/agpl-3.0");
    }

    @Test
    void testGetMitLicense() {
        LicensesApi api = new RestClients(webClient).getLicensesApi();
        ResponseEntity<License> response = api.get("mit");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(License.class);
        
        License mitLicense = response.getBody();
        assertThat(mitLicense.getKey()).isEqualTo("mit");
        assertThat(mitLicense.getName()).contains("MIT");
        assertThat(mitLicense.getSpdxId()).isEqualTo("MIT");
        assertThat(mitLicense.getBody())
                .contains("MIT License")
                .contains("Permission is hereby granted");
        assertThat(mitLicense.getConditions())
                .hasSize(1)
                .contains("include-copyright");
        assertThat(mitLicense.getPermissions())
                .hasSize(4)
                .contains("commercial-use", "modifications", "distribution", "private-use");
        assertThat(mitLicense.getLimitations())
                .hasSize(2)
                .contains("liability", "warranty");
    }

    @Test
    void testGetApacheLicense() {
        LicensesApi api = new RestClients(webClient).getLicensesApi();
        ResponseEntity<License> response = api.get("apache-2.0");
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(License.class);
        
        License apacheLicense = response.getBody();
        assertThat(apacheLicense.getKey()).isEqualTo("apache-2.0");
        assertThat(apacheLicense.getName()).contains("Apache");
        assertThat(apacheLicense.getSpdxId()).isEqualTo("Apache-2.0");
        assertThat(apacheLicense.getBody())
                .contains("Apache License")
                .contains("Version 2.0");
    }

    @Test
    void testGetFeaturedLicenses() {
        LicensesApi api = new RestClients(webClient).getLicensesApi();
        ResponseEntity<List<LicenseSimple>> response = api.getAllCommonlyUsed(true, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<LicenseSimple> featuredLicenses = response.getBody();
        assertThat(featuredLicenses)
                .isNotEmpty()
                .hasSize(3); // Featured licenses should be a smaller subset
        
        // Verify the first featured license has specific values
        LicenseSimple firstFeatured = featuredLicenses.getFirst();
        assertThat(firstFeatured.getKey()).isEqualTo("apache-2.0");
        assertThat(firstFeatured.getName()).isEqualTo("Apache License 2.0");
        assertThat(firstFeatured.getUrl()).isNotNull();
        assertThat(firstFeatured.getUrl().toString()).isEqualTo("https://api.github.com/licenses/apache-2.0");
    }
}