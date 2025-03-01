package io.github.pulpogato.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
                "logging.level.org.apache.http.wire=DEBUG",
        }
)
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
                .defaultHeader("TapeName", classPart + "/" + methodPart)
                .build();

        factory = HttpServiceProxyFactory.builder()
                .exchangeAdapter(WebClientAdapter.create(webClient))
                .build();
    }
}
