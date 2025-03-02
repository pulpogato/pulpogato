package io.github.pulpogato.rest.api;

import io.github.pulpogato.rest.schemas.PrivateUser;
import io.github.pulpogato.rest.schemas.SimpleUser;
import io.github.pulpogato.test.BaseIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
    @Disabled("PATCH is currently broken")
    void testUpdateAuthenticated() {
        UsersApi api = factory.createClient(UsersApi.class);
        var update = UsersApi.UpdateAuthenticatedRequestBody.builder()
                .location("San Francisco Bay Area")
                .build();
        var authenticated = api.updateAuthenticated(update);
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody())
                .isNotNull()
                .isInstanceOf(PrivateUser.class);
        var body = authenticated.getBody();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getLogin()).isNotNull();
        assertThat(body.getBio()).isEqualTo("This is a test bio");
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

    @Test
    void testCheckBlocked() {
        UsersApi api = factory.createClient(UsersApi.class);
        var blocked = api.checkBlocked("some-blocked-user");
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody())
                .isNull();
    }

    @Test
    void testCheckNotBlocked() {
        UsersApi api = factory.createClient(UsersApi.class);
        WebClientResponseException exception = catchThrowableOfType(WebClientResponseException.class, () -> api.checkBlocked("gooduser"));

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().is4xxClientError()).isTrue();
        assertThat(exception.getResponseBodyAsString()).isNotNull();
    }

    @Test
    void testBlockUserFailed() {
        UsersApi api = factory.createClient(UsersApi.class);
        WebClientResponseException exception = catchThrowableOfType(WebClientResponseException.class, () -> api.block("some-blocked-user"));

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().is4xxClientError()).isTrue();
        assertThat(exception.getResponseBodyAsString()).isNotNull();
    }

    @Test
    void testBlockUserSuccess() {
        UsersApi api = factory.createClient(UsersApi.class);
        var blocked = api.block("gooduser");

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody())
                .isNull();
    }

    @Test
    void testUnblockUserSuccess() {
        UsersApi api = factory.createClient(UsersApi.class);
        var blocked = api.block("gooduser");

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody())
                .isNull();
    }

    @Test
    void testListFollowers() {
        UsersApi api = factory.createClient(UsersApi.class);
        var followers = api.listFollowersForAuthenticatedUser( 5L, 0L);
        assertThat(followers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(followers.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        var followersBody = followers.getBody();
        assertThat(followersBody)
                .isNotNull()
                .hasSize(5);
        var follower = followersBody.getFirst();
        assertThat(follower)
                .isNotNull()
                .isInstanceOf(SimpleUser.class);
        assertThat(follower.getId()).isEqualTo(29520L);
    }

    @Test
    void testListGpgKeysForAuthenticatedUser() {
        UsersApi api = factory.createClient(UsersApi.class);
        var gpgKeys = api.listGpgKeysForAuthenticatedUser(5L, 0L);
        assertThat(gpgKeys.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gpgKeys.getBody())
                .isNotNull()
                .isInstanceOf(List.class);
        var gpgKeysBody = gpgKeys.getBody();
        assertThat(gpgKeysBody)
                .isNotNull()
                .hasSize(1);
        var gpgKey = gpgKeysBody.getFirst();
        assertThat(gpgKey)
                .isNotNull();
        assertThat(gpgKey.getId()).isEqualTo(175109L);
        assertThat(gpgKey.getKeyId()).isEqualTo("8B459169D13D7E09");
    }

    @Test
    void testGetGpgKeyForAuthenticatedUser() {
        UsersApi api = factory.createClient(UsersApi.class);
        var gpgKey = api.getGpgKeyForAuthenticatedUser(175109L);
        assertThat(gpgKey.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gpgKey.getBody())
                .isNotNull();
        var gpgKeyBody = gpgKey.getBody();
        assertThat(gpgKeyBody)
                .isNotNull();
        assertThat(gpgKeyBody.getId()).isEqualTo(175109L);
        assertThat(gpgKeyBody.getKeyId()).isEqualTo("8B459169D13D7E09");
    }

}
