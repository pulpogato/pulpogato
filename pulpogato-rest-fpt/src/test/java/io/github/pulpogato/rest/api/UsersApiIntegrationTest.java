package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.SimpleUser;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsersApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void testGetAuthenticatedPublic() {
        UsersApi api = factory.createClient(UsersApi.class);
        ResponseEntity<UsersApi.GetAuthenticated200> authenticated = api.getAuthenticated();
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody())
                .isNotNull()
                .isInstanceOf(UsersApi.GetAuthenticated200.class);
        var body = authenticated.getBody();
        // TODO: This should be a PublicUser
        assertThat(body.getPrivateUser()).isNotNull();
        var privateUser = body.getPrivateUser();
        assertThat(privateUser.getId()).isNotNull();
        assertThat(privateUser.getLogin()).isNotNull();
    }

    @Test
    void testGetAuthenticatedPrivate() {
        UsersApi api = factory.createClient(UsersApi.class);
        ResponseEntity<UsersApi.GetAuthenticated200> authenticated = api.getAuthenticated();
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody())
                .isNotNull()
                .isInstanceOf(UsersApi.GetAuthenticated200.class);
        var body = authenticated.getBody();

        assertThat(body.getPrivateUser()).isNotNull();
        var privateUser = body.getPrivateUser();
        assertThat(privateUser.getId()).isNotNull();
        assertThat(privateUser.getLogin()).isNotNull();
        assertThat(privateUser.getPlan()).isNotNull();
        assertThat(privateUser.getPlan().getName()).isNotNull();
    }

    @Test
    void testListBlockedEmpty() {
        UsersApi api = factory.createClient(UsersApi.class);
        var blocked = api.listBlockedByAuthenticatedUser(5L, 0L);
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        var blockedBody = blocked.getBody();
        assertThat(blockedBody)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testListBlockedValid() {
        UsersApi api = factory.createClient(UsersApi.class);
        var blocked = api.listBlockedByAuthenticatedUser(5L, 0L);
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        var blockedBody = blocked.getBody();
        assertThat(blockedBody)
                .isNotNull()
                .hasSize(1);
        var blockedUser = blockedBody.getFirst();
        assertThat(blockedUser)
                .isNotNull()
                .isInstanceOf(SimpleUser.class);
        assertThat(blockedUser.getId()).isEqualTo(96304584L);
    }

}
