package io.github.pulpogato.test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.PulpogatoModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.test.web.servlet.client.MockMvcHttpConnector;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.lang.reflect.Method;

@SpringBootTest(
        classes = {ProxyController.class},
        properties = {
                "logging.level.org.springframework.web.client.RestTemplate=DEBUG",
                "logging.level.org.springframework.boot.test.mock.web=WARN",
                "logging.level.org.springframework.test.web.servlet=WARN",
                "logging.level.org.apache.http.wire=DEBUG",
                "logging.pattern.console=%d{HH:mm:ss.SSS} %-5level %-42logger{36} - %msg%n"
        }
)
@Slf4j
public class BaseIntegrationTest {
    @SuppressWarnings("unused")
    @Autowired
    private WebApplicationContext webApplicationContext;

    protected HttpServiceProxyFactory factory;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        String classPart = testInfo.getTestClass().map(Class::getName)
                .map(name -> name.replace(".", "/"))
                .orElseThrow();
        String methodPart = testInfo.getTestMethod().map(Method::getName)
                .orElseThrow();

        var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        final var webClient = WebClient.builder()
                .clientConnector(new MockMvcHttpConnector(mockMvc))
                .codecs(it -> {
                    ObjectMapper mapper = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .registerModule(new PulpogatoModule())
                            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
                    it.defaultCodecs()
                            .jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
                })
                .defaultHeader("TapeName", classPart + "/" + methodPart)
                .build();

        factory = HttpServiceProxyFactory.builder()
                .exchangeAdapter(WebClientAdapter.create(webClient))
                .build();

        log.info("");
    }
}
