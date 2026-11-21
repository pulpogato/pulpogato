package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProxyControllerTest {
    @Test
    void defaultTransportSupportsPatchAndDoesNotFollowRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var patchMethod = new AtomicReference<String>();
        var patchBody = new AtomicReference<String>();
        var redirectTargetRequests = new AtomicInteger();
        server.createContext("/patch", exchange -> {
            patchMethod.set(exchange.getRequestMethod());
            patchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add(HttpHeaders.LOCATION, "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirectTargetRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var controller = new ProxyController();
            var baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
            var patchResponse = controller.getLiveResponseBytes(
                    HttpMethod.PATCH, URI.create(baseUri + "/patch"), new HttpEntity<>("patch body"));
            assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(patchMethod.get()).isEqualTo("PATCH");
            assertThat(patchBody.get()).isEqualTo("patch body");

            var redirectResponse = controller.getLiveResponseBytes(
                    HttpMethod.GET, URI.create(baseUri + "/redirect"), new HttpEntity<String>(new HttpHeaders()));
            assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirectResponse.getHeaders().getLocation()).isEqualTo(URI.create("/target"));
            assertThat(redirectTargetRequests.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwardsPatchAndReturnsClientErrorBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var controller = new ProxyController(builder.build());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("test-token");
        var uri = URI.create("https://api.github.com/test?value=1");
        server.expect(requestTo(uri))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(content().json("{\"value\":1}"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid\"}"));

        var response =
                controller.getLiveResponseBytes(HttpMethod.PATCH, uri, new HttpEntity<>("{\"value\":1}", headers));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody())
                .isEqualTo("{\"message\":\"invalid\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void returnsRedirectAndBinaryBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var controller = new ProxyController(builder.build());
        var uri = URI.create("https://api.github.com/test");
        byte[] bytes = {0, (byte) 0xff, 42};
        server.expect(requestTo(uri))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/new")
                        .body(bytes));

        var response = controller.getLiveResponseBytes(HttpMethod.GET, uri, new HttpEntity<String>(new HttpHeaders()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/new"));
        assertThat(response.getBody()).isEqualTo(bytes);
        server.verify();
    }
}
