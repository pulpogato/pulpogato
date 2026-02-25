package io.github.pulpogato.test;

import java.lang.reflect.Method;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.MockMvcHttpConnector;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(
        classes = {ProxyController.class},
        properties = {
            "logging.level.org.springframework.web.client.RestTemplate=DEBUG",
            "logging.level.org.springframework.boot.test.mock.web=WARN",
            "logging.level.org.springframework.test.web.servlet=WARN",
            "logging.level.org.apache.http.wire=DEBUG",
            "logging.pattern.console=%d{HH:mm:ss.SSS} %-5level %-42logger{36} - %msg%n"
        })
@Slf4j
public class BaseIntegrationTest {
    @SuppressWarnings("unused")
    @Autowired
    private WebApplicationContext webApplicationContext;

    protected WebClient webClient;

    @BeforeEach
    void setUp(TestInfo testInfo) {

        final var packagePart = getTestResourceRootPackage()
                .or(() -> testInfo.getTestClass().map(Class::getPackage))
                .map(Package::getName)
                .map(name1 -> name1.replace(".", "/"))
                .orElseThrow();
        String classNamePart = testInfo.getTestClass()
                .map(Class::getSimpleName)
                .map(name -> name.replace(".", "/"))
                .orElseThrow();
        String methodPart = testInfo.getTestMethod().map(Method::getName).orElseThrow();

        String tapeName = packagePart + "/" + classNamePart + "/" + methodPart;
        var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        var strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .clientConnector(new MockMvcHttpConnector(mockMvc))
                .defaultHeader("TapeName", tapeName)
                .build();

        log.info("");
    }

    protected @NonNull Optional<Package> getTestResourceRootPackage() {
        return Optional.empty();
    }
}
