package io.github.pulpogato.test;

import lombok.*;

import java.util.Map;

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
    public static class Response {
        private int statusCode;
        private Map<String, String> headers;
        private String body;
    }
    private Response response;
}
