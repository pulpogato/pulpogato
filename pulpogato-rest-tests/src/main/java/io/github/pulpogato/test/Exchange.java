package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@Setter
@AllArgsConstructor
public class Exchange {
    @Builder
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @Setter
    @AllArgsConstructor
    @JsonPropertyOrder({"method", "uri", "protocol", "headers", "body"})
    public static class Request {
        private String method;
        private String uri;
        private String protocol;
        private Map<String, String> headers;
        private String body;
    }

    private Request request;

    @Builder
    @Getter
    @NoArgsConstructor
    @Setter
    @AllArgsConstructor
    @JsonPropertyOrder({"statusCode", "headers", "body"})
    public static class Response {
        private int statusCode;
        private Map<String, String> headers;
        private String body;
    }

    private Response response;
}
