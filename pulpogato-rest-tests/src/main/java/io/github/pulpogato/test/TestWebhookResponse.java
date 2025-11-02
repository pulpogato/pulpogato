package io.github.pulpogato.test;

import java.util.Map;
import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TestWebhookResponse {
    @Singular
    private Map<String, String> headers;

    private String webhookName;
    private String body;
}
