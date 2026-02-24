package io.github.pulpogato.rest.api.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.pulpogato.rest.api.BaseApiIntegrationTest;
import io.github.pulpogato.rest.schemas.BasicError;
import io.github.pulpogato.rest.schemas.PrivateUser;
import io.github.pulpogato.rest.schemas.SimpleUser;
import io.github.pulpogato.rest.schemas.ValidationErrorSimple;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.ObjectMapper;

class UsersApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void testGetAuthenticatedPublic() {
        // tag::getAuthenticatedPublic[]
        // Create RestClients instance
        RestClients restClients = new RestClients(webClient);
        // Get UsersApi
        UsersApi api = restClients.getUsersApi();
        // Call getAuthenticated method
        var authenticated = api.getAuthenticated().block();
        // end::getAuthenticatedPublic[]
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody()).isNotNull().isInstanceOf(UsersApi.GetAuthenticated200.class);
        // tag::getBody[]
        var body = authenticated.getBody();
        // end::getBody[]
        // TODO: This should be a PublicUser
        // tag::getUser[]
        var privateUser = body.getPrivateUser();
        // end::getUser[]
        assertThat(body.getPrivateUser()).isNotNull();
        assertThat(privateUser.getId()).isEqualTo(193047L);
        // tag::getLogin[]
        var login = privateUser.getLogin();
        // end::getLogin[]
        assertThat(login).isEqualTo("rahulsom");
    }

    @Test
    void testGetAuthenticatedPrivate() {
        var api = new RestClients(webClient).getUsersApi();
        var authenticated = api.getAuthenticated().block();
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody()).isNotNull().isInstanceOf(UsersApi.GetAuthenticated200.class);
        var body = authenticated.getBody();

        assertThat(body.getPrivateUser()).isNotNull();
        var privateUser = body.getPrivateUser();
        assertThat(privateUser.getId()).isEqualTo(193047L);
        assertThat(privateUser.getLogin()).isEqualTo("rahulsom");
        assertThat(privateUser.getPlan()).isNotNull();
        assertThat(privateUser.getPlan().getName()).isEqualTo("free");
    }

    @Test
    @Disabled("PATCH is currently broken")
    void testUpdateAuthenticated() {
        var api = new RestClients(webClient).getUsersApi();
        var update = UsersApi.UpdateAuthenticatedRequestBody.builder()
                .location("San Francisco Bay Area")
                .build();
        var authenticated = api.updateAuthenticated(update).block();
        assertThat(authenticated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authenticated.getBody()).isNotNull().isInstanceOf(PrivateUser.class);
        var body = authenticated.getBody();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getLogin()).isNotNull();
        assertThat(body.getBio()).isEqualTo("This is a test bio");
    }

    @Test
    void testListBlockedEmpty() {
        var api = new RestClients(webClient).getUsersApi();
        var blocked = api.listBlockedByAuthenticatedUser(5L, 0L).block();
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody()).isNotNull().isInstanceOf(List.class);
        var blockedBody = blocked.getBody();
        assertThat(blockedBody).isNotNull().isEmpty();
    }

    @Test
    void testListBlockedValid() {
        var api = new RestClients(webClient).getUsersApi();
        var blocked = api.listBlockedByAuthenticatedUser(5L, 0L).block();
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody()).isNotNull().isInstanceOf(List.class);
        var blockedBody = blocked.getBody();
        assertThat(blockedBody).isNotNull().hasSize(1);
        var blockedUser = blockedBody.getFirst();
        assertThat(blockedUser).isNotNull().isInstanceOf(SimpleUser.class);
        assertThat(blockedUser.getId()).isEqualTo(96304584L);
    }

    @Test
    void testCheckBlocked() {
        var api = new RestClients(webClient).getUsersApi();
        var blocked = api.checkBlocked("some-blocked-user").block();
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody()).isNull();
    }

    @Test
    void testCheckNotBlocked() {
        var api = new RestClients(webClient).getUsersApi();
        var exception = catchThrowableOfType(WebClientResponseException.class, () -> api.checkBlocked("gooduser")
                .block());

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().is4xxClientError()).isTrue();
        assertThat(exception.getResponseBodyAsString()).isNotNull();
    }

    @Test
    void testBlockUserFailed() {
        var api = new RestClients(webClient).getUsersApi();
        var exception = catchThrowableOfType(WebClientResponseException.class, () -> api.block("some-blocked-user")
                .block());

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().value()).isEqualTo(422);
        assertThat(exception.getResponseBodyAsString()).isNotNull();

        var objectMapper = new ObjectMapper();
        var error = objectMapper.readValue(exception.getResponseBodyAsString(), ValidationErrorSimple.class);
        assertThat(error).isNotNull();
        assertThat(error.getMessage()).isEqualTo("Blocked user has already been blocked");
        assertThat(error.getDocumentationUrl()).isEqualTo("https://docs.github.com/rest/users/blocking#block-a-user");
        System.out.println(error.toCode());
        System.out.println(exception.getResponseBodyAsString());
    }

    @Test
    void testBlockUserSuccess() {
        var api = new RestClients(webClient).getUsersApi();
        var blocked = api.block("gooduser").block();

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody()).isNull();
    }

    @Test
    void testUnblockUserSuccess() {
        var api = new RestClients(webClient).getUsersApi();
        var blocked = api.unblock("gooduser").block();

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(blocked.getBody()).isNull();
    }

    @Test
    void testListFollowers() {
        var api = new RestClients(webClient).getUsersApi();
        var followers = api.listFollowersForAuthenticatedUser(5L, 0L).block();
        assertThat(followers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(followers.getBody()).isNotNull().isInstanceOf(List.class);
        var followersBody = followers.getBody();
        assertThat(followersBody).isNotNull().hasSize(5);
        var follower = followersBody.getFirst();
        assertThat(follower).isNotNull().isInstanceOf(SimpleUser.class);
        assertThat(follower.getId()).isEqualTo(29520L);
    }

    @Test
    void testListGpgKeysForAuthenticatedUser() {
        var api = new RestClients(webClient).getUsersApi();
        var gpgKeys = api.listGpgKeysForAuthenticatedUser(5L, 0L).block();
        assertThat(gpgKeys.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gpgKeys.getBody()).isNotNull().isInstanceOf(List.class);
        var gpgKeysBody = gpgKeys.getBody();
        assertThat(gpgKeysBody).isNotNull().hasSize(1);
        var gpgKey = gpgKeysBody.getFirst();
        assertThat(gpgKey).isNotNull();
        assertThat(gpgKey.getId()).isEqualTo(175109L);
        assertThat(gpgKey.getKeyId()).isEqualTo("8B459169D13D7E09");
    }

    @Test
    void testGetGpgKeyForAuthenticatedUser() {
        var api = new RestClients(webClient).getUsersApi();
        var gpgKey = api.getGpgKeyForAuthenticatedUser(175109L).block();
        assertThat(gpgKey.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gpgKey.getBody()).isNotNull();
        var gpgKeyBody = gpgKey.getBody();
        assertThat(gpgKeyBody).isNotNull();
        assertThat(gpgKeyBody.getId()).isEqualTo(175109L);
        assertThat(gpgKeyBody.getKeyId()).isEqualTo("8B459169D13D7E09");
    }

    @Test
    void testListPublicEmailsForAuthenticatedUser() {
        var api = new RestClients(webClient).getUsersApi();
        var response = api.listPublicEmailsForAuthenticatedUser(5L, 0L).block();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);
        var emails = response.getBody();
        assertThat(emails).hasSize(2);

        var first = emails.getFirst();
        assertThat(first.getEmail()).isEqualTo("rsomasunderam@example.com");
        assertThat(first.getPrimary()).isFalse();
        assertThat(first.getVerified()).isTrue();
        assertThat(first.getVisibility()).isNull();

        var second = emails.get(1);
        assertThat(second.getEmail()).isEqualTo("rahul.som@gmail.com");
        assertThat(second.getPrimary()).isTrue();
        assertThat(second.getVerified()).isTrue();
        assertThat(second.getVisibility()).isEqualTo("public");
    }

    @Test
    void testListEmailsForAuthenticatedUser() {
        var api = new RestClients(webClient).getUsersApi();
        var response = api.listEmailsForAuthenticatedUser(5L, 0L).block();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);
        var emails = response.getBody();
        assertThat(emails).hasSize(3);

        var first = emails.getFirst();
        assertThat(first.getEmail()).isEqualTo("rahul.som@gmail.com");
        assertThat(first.getPrimary()).isTrue();
        assertThat(first.getVerified()).isTrue();
        assertThat(first.getVisibility()).isEqualTo("public");

        var second = emails.get(1);
        assertThat(second.getEmail()).isEqualTo("rsom@certifydatasystems.com");
        assertThat(second.getPrimary()).isFalse();
        assertThat(second.getVerified()).isTrue();
        assertThat(second.getVisibility()).isNull();

        var third = emails.get(2);
        assertThat(third.getEmail()).isEqualTo("rsomasunderam@example.com");
        assertThat(third.getPrimary()).isFalse();
        assertThat(third.getVerified()).isTrue();
        assertThat(third.getVisibility()).isNull();
    }

    @Test
    void testListSocialAccountsForAuthenticatedUser() {
        var api = new RestClients(webClient).getUsersApi();

        var response = api.listSocialAccountsForAuthenticatedUser(5L, 0L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull().isInstanceOf(List.class);

        var socialAccount = response.getBody().getFirst();
        assertThat(socialAccount).isNotNull();

        assertThat(socialAccount.getProvider()).isEqualTo("twitter");
        assertThat(socialAccount.getUrl()).isEqualTo("https://twitter.com/rahulsom");
    }

    @Test
    void testGetById() {
        var api = new RestClients(webClient).getUsersApi();

        var response = api.getById(230004L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        // TODO: This should be a PublicUser
        assertThat(body.getPrivateUser()).isNotNull();

        var user = body.getPrivateUser();

        assertThat(user.getLogin()).isEqualTo("sghill");
        assertThat(user.getName()).isEqualTo("Steve Hill");
        assertThat(user.getCompany()).isEqualTo("Example");
        assertThat(user.getLocation()).isEqualTo("SF Bay Area");
        assertThat(user.getEmail()).isEqualTo("sghill.dev@gmail.com");
        assertThat(user.getPublicRepos()).isEqualTo(228);
        assertThat(user.getPublicGists()).isEqualTo(18);
        assertThat(user.getFollowers()).isEqualTo(50);
        assertThat(user.getFollowing()).isEqualTo(40);
    }

    @Test
    void testGetByUsername() {
        var api = new RestClients(webClient).getUsersApi();

        var response = api.getByUsername("sghill").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        // TODO: This should be a PublicUser
        assertThat(body.getPrivateUser()).isNotNull();

        var user = body.getPrivateUser();

        assertThat(user.getLogin()).isEqualTo("sghill");
        assertThat(user.getName()).isEqualTo("Steve Hill");
        assertThat(user.getCompany()).isEqualTo("Example");
        assertThat(user.getLocation()).isEqualTo("SF Bay Area");
        assertThat(user.getEmail()).isEqualTo("sghill.dev@gmail.com");
        assertThat(user.getPublicRepos()).isEqualTo(228);
        assertThat(user.getPublicGists()).isEqualTo(18);
        assertThat(user.getFollowers()).isEqualTo(50);
        assertThat(user.getFollowing()).isEqualTo(40);
    }

    @Test
    void testGetByUsername404() {
        var api = new RestClients(webClient).getUsersApi();

        var exception = catchThrowableOfType(WebClientResponseException.class, () -> api.getByUsername("rahulsom1")
                .block());

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode().value()).isEqualTo(404);
        assertThat(exception.getResponseBodyAsString()).isNotNull();

        var objectMapper = new ObjectMapper();
        var error = objectMapper.readValue(exception.getResponseBodyAsString(), BasicError.class);
        assertThat(error).isNotNull();
        assertThat(error.getMessage()).isEqualTo("Not Found");
        assertThat(error.getStatus()).isEqualTo("404");
        assertThat(error.getDocumentationUrl()).isEqualTo("https://docs.github.com/rest");
    }

    @Test
    void testListFollowersForUser() {
        var api = new RestClients(webClient).getUsersApi();

        var response = api.listFollowersForUser("sghill", 5L, 0L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        assertThat(body).hasSize(5);

        var follower = body.getFirst();

        assertThat(follower).isNotNull();
        assertThat(follower.getId()).isEqualTo(13945L);
        assertThat(follower.getLogin()).isEqualTo("bguthrie");
    }

    @Test
    void testListFollowingForUser() {
        var api = new RestClients(webClient).getUsersApi();

        var response = api.listFollowingForUser("sghill", 5L, 0L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();

        assertThat(body).hasSize(5);

        var following = body.getFirst();

        assertThat(following).isNotNull();
        assertThat(following.getId()).isEqualTo(4732L);
        assertThat(following.getLogin()).isEqualTo("jashkenas");
    }
}
