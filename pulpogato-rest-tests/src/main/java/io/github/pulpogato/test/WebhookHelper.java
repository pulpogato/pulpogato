package io.github.pulpogato.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.provider.Arguments;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WebhookHelper {
    private static Set<String> getWebhookNames() {
        return new Reflections("webhooks", Scanners.Resources).getResources(Pattern.compile(".+\\.http"));
    }

    public static Stream<Arguments> getArguments(String version) {
        return WebhookHelper.getWebhookNames()
                .stream()
                .filter(it -> {
                    final InputStream resourceAsStream = WebhookHelper.class.getResourceAsStream("/" + it);
                    assertThat(resourceAsStream).isNotNull();
                    final var reader = new BufferedReader(new InputStreamReader(resourceAsStream));
                    final var lines = reader.lines().toList();
                    final var include = lines.stream()
                            .filter(l -> l.toLowerCase().startsWith("X-Pulpogato-Include:".toLowerCase()))
                            .findFirst().map(l -> l.split(":",2)[1].trim()).orElse("*");
                    final var exclude = lines.stream()
                            .filter(l -> l.toLowerCase().startsWith("X-Pulpogato-Exclude:".toLowerCase()))
                            .findFirst().map(l -> l.split(":",2)[1].trim()).orElse("");

                    return testVersion(version, include, exclude);
                })
                .map(it -> Arguments.arguments(it.split("/")[1], it));
    }

    private static boolean testVersion(String version, String include, String exclude) {
        if (include.equals("*")) {
            return !exclude.contains(version);
        }
        if (Arrays.stream(include.split(",")).map(String::trim).collect(Collectors.toSet()).contains(version)) {
            return !exclude.contains(version);
        }
        return false;
    }

    public static void testWebhook(String hookname, String filename, MockMvc mvc) throws Exception {
        final MvcResult mvcResult = mvc.perform(WebhookHelper.buildRequest(filename))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookName").value(hookname))
                .andReturn();

        final TestWebhookResponse response = new ObjectMapper().readValue(
                mvcResult.getResponse().getContentAsString(), TestWebhookResponse.class);

        final var softly = new SoftAssertions();
        TestUtils.diffJson(
                mvcResult.getRequest().getContentAsString(),
                response.getBody(),
                softly
        );
        softly.assertAll();
    }

    @SneakyThrows
    private static MockHttpServletRequestBuilder buildRequest(String filename) {
        final InputStream resourceAsStream = WebhookHelper.class.getResourceAsStream("/" + filename);
        assertThat(resourceAsStream).isNotNull();
        final var reader = new BufferedReader(new InputStreamReader(resourceAsStream));

        final String firstLine = reader.readLine();
        assertThat(firstLine)
                .startsWith("POST /webhooks")
                .endsWith(" HTTP/1.1");
        final MockHttpServletRequestBuilder requestBuilder = post("/webhooks");
        while(true) {
            final String line = reader.readLine();
            if (line.isEmpty()) {
                break;
            }
            final String[] split = line.split(":", 2);
            requestBuilder.header(split[0], split[1].trim());
        }
        final var body = new StringBuilder();
        while(true) {
            final String line = reader.readLine();
            if (line == null) {
                break;
            }
            body.append(line).append("\n");
        }
        requestBuilder.content(body.toString());
        reader.close();

        return requestBuilder;
    }

}
