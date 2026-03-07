package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TapeTest {

    @Test
    void serializedTapeOmitsNullBodyIsBinary() throws Exception {
        var response = Exchange.Response.builder()
                .statusCode(200)
                .headers(java.util.Map.of("Content-Type", "application/json"))
                .bodyIsBinary(null)
                .body("{\"ok\": true}")
                .build();
        var exchange = Exchange.builder()
                .request(Exchange.Request.builder()
                        .method("GET")
                        .uri("/test")
                        .protocol("HTTP/1.1")
                        .headers(java.util.Map.of())
                        .body(null)
                        .build())
                .response(response)
                .build();

        String yaml = Tape.serializeExchanges(List.of(exchange));

        assertThat(yaml).doesNotContain("bodyIsBinary");
    }

    @Test
    void serializedTapeIncludesTrueBodyIsBinary() throws Exception {
        var response = Exchange.Response.builder()
                .statusCode(200)
                .headers(java.util.Map.of("Content-Type", "application/vnd.github.raw+json"))
                .bodyIsBinary(true)
                .body("3d3f5827")
                .build();
        var exchange = Exchange.builder()
                .request(Exchange.Request.builder()
                        .method("GET")
                        .uri("/test")
                        .protocol("HTTP/1.1")
                        .headers(java.util.Map.of())
                        .body(null)
                        .build())
                .response(response)
                .build();

        String yaml = Tape.serializeExchanges(List.of(exchange));

        assertThat(yaml).contains("bodyIsBinary: true");
    }
}
