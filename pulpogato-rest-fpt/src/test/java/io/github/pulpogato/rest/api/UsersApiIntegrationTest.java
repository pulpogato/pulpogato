package io.github.pulpogato.rest.api;

import io.github.pulpogato.test.ProxyApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ProxyApplication.class},
        properties = {
                "spring.application.name=pulpogato-demo",
                "logging.level.org.springframework.web.client.RestTemplate=DEBUG",
                "logging.level.org.apache.http.wire=DEBUG",
        }
)
class UsersApiIntegrationTest {

    @LocalServerPort
    int randomServerPort;

    HttpServiceProxyFactory factory;

    @BeforeEach
    void setUp() {
        HttpClient httpClient = HttpClient.create();

        final var webClient = WebClient.builder()
                .baseUrl("http://localhost:" + randomServerPort)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        factory = HttpServiceProxyFactory.builder()
                .exchangeAdapter(WebClientAdapter.create(webClient))
                .build();
    }

    @Test
    void testGetAuthenticated() {
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

}
