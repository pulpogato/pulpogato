package io.github.pulpogato.rest.api;

import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsersApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetById() {
        UsersApi api = new RestClients(webClient).getUsersApi();

        var response = api.getById(14513L);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        assertThat(body.getPrivateUser()).isNotNull();
        assertThat(body.getPublicUser()).isNull();

        var user = body.getPrivateUser();

        assertThat(user.getLogin()).isEqualTo("user1");
        assertThat(user.getName()).isEqualTo("User One");
        assertThat(user.getCompany()).isNull();
        assertThat(user.getLocation()).isEqualTo("City, ST");
        assertThat(user.getEmail()).isEqualTo("user1@example.com");
        assertThat(user.getPublicRepos()).isZero();
        assertThat(user.getPublicGists()).isZero();
        assertThat(user.getFollowers()).isZero();
        assertThat(user.getFollowing()).isZero();
        assertThat(user.getSuspendedAt()).isNull();
    }

    @Test
    void testGetSuspendedById() {
        UsersApi api = new RestClients(webClient).getUsersApi();

        var response = api.getById(90L);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        assertThat(body.getPrivateUser()).isNotNull();
        assertThat(body.getPublicUser()).isNull();

        var user = body.getPrivateUser();

        assertThat(user.getLogin()).isEqualTo("user2");
        assertThat(user.getName()).isEqualTo("User Two");
        assertThat(user.getCompany()).isNull();
        assertThat(user.getLocation()).isNull();
        assertThat(user.getEmail()).isEqualTo("user2@example.com");
        assertThat(user.getPublicRepos()).isZero();
        assertThat(user.getPublicGists()).isZero();
        assertThat(user.getFollowers()).isZero();
        assertThat(user.getFollowing()).isZero();
        assertThat(user.getSuspendedAt()).isNotNull();

    }
}
