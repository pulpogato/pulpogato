package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @JsonPropertyOrder({"statusCode", "headers", "bodyIsBinary", "body"})
    public static class Response {
        private int statusCode;
        private Map<String, String> headers;
        /**
         * When true, {@link #body} is a hexdump of the raw response bytes. When replaying, the hex is
         * decoded to bytes and exposed as a string (ISO-8859-1) so the original bytes are preserved.
         */
        private Boolean bodyIsBinary;

        private String body;
    }

    private Response response;
}
