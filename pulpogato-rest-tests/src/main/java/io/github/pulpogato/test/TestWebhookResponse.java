package io.github.pulpogato.test;

import lombok.*;

import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TestWebhookResponse {
    @Singular private Map<String, String> headers;
    private String webhookName;
    private String body;
}
