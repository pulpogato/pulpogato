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
        LicensesApi api = factory.createClient(LicensesApi.class);
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
        assertThat(firstLicense.getKey()).isNotNull();
        assertThat(firstLicense.getName()).isNotNull();
        assertThat(firstLicense.getUrl()).isNotNull();
    }

    @Test
    void testGetMitLicense() {
        LicensesApi api = factory.createClient(LicensesApi.class);
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
                .isNotNull()
                .contains("MIT License")
                .contains("Permission is hereby granted");
        assertThat(mitLicense.getConditions())
                .isNotNull()
                .isNotEmpty();
        assertThat(mitLicense.getPermissions())
                .isNotNull()
                .isNotEmpty();
        assertThat(mitLicense.getLimitations())
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void testGetApacheLicense() {
        LicensesApi api = factory.createClient(LicensesApi.class);
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
                .isNotNull()
                .contains("Apache License")
                .contains("Version 2.0");
    }

    @Test
    void testGetFeaturedLicenses() {
        LicensesApi api = factory.createClient(LicensesApi.class);
        ResponseEntity<List<LicenseSimple>> response = api.getAllCommonlyUsed(true, null, null);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        
        List<LicenseSimple> featuredLicenses = response.getBody();
        assertThat(featuredLicenses)
                .isNotEmpty()
                .hasSizeLessThanOrEqualTo(10); // Featured licenses should be a smaller subset
        
        // All featured licenses should have required fields
        featuredLicenses.forEach(license -> {
            assertThat(license.getKey()).isNotNull();
            assertThat(license.getName()).isNotNull();
            assertThat(license.getUrl()).isNotNull();
        });
    }
}